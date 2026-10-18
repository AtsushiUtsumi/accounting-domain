package com.example.accounting.model.inventory;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.money.RoundingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 移動平均法。仕入れるたびに在庫全体の平均単価を再計算し、次の払出にその単価を使う。 */
public final class MovingAverageInventoryValuationMethod implements InventoryValuationMethod {

    private final RoundingPolicy roundingPolicy;

    private MovingAverageInventoryValuationMethod(RoundingPolicy roundingPolicy) {
        this.roundingPolicy = roundingPolicy;
    }

    public static MovingAverageInventoryValuationMethod of() {
        return new MovingAverageInventoryValuationMethod(RoundingPolicy.roundDown());
    }

    public static MovingAverageInventoryValuationMethod of(RoundingPolicy roundingPolicy) {
        return new MovingAverageInventoryValuationMethod(roundingPolicy);
    }

    @Override
    public List<InventoryLedgerEntry> valuate(List<InventoryMovement> movements) {
        int balanceQuantity = 0;
        Money balanceAmount = Money.ZERO;
        List<InventoryLedgerEntry> entries = new ArrayList<>();

        for (InventoryMovement movement : movements) {
            switch (movement) {
                case InventoryMovement.Receipt receipt -> {
                    InventoryLot lot = new InventoryLot(receipt.quantity(), receipt.unitCost());
                    balanceQuantity += receipt.quantity();
                    balanceAmount = balanceAmount.plus(lot.amount());
                    Money averageUnitCost = averageUnitCost(balanceAmount, balanceQuantity);
                    entries.add(new InventoryLedgerEntry(
                            receipt.date(), receipt.description(),
                            List.of(lot), List.of(),
                            List.of(new InventoryLot(balanceQuantity, averageUnitCost))));
                }
                case InventoryMovement.Issue issue -> {
                    if (issue.quantity() > balanceQuantity) {
                        throw new AccountingException("在庫不足: 払出数量が残高を超えている");
                    }
                    Money unitCost = averageUnitCost(balanceAmount, balanceQuantity);
                    InventoryLot issued = new InventoryLot(issue.quantity(), unitCost);
                    balanceQuantity -= issue.quantity();
                    balanceAmount = balanceAmount.minus(issued.amount());
                    entries.add(new InventoryLedgerEntry(
                            issue.date(), issue.description(),
                            List.of(), List.of(issued),
                            balanceQuantity == 0
                                    ? List.of()
                                    : List.of(new InventoryLot(balanceQuantity, unitCost))));
                }
            }
        }
        return entries;
    }

    private Money averageUnitCost(Money totalAmount, int totalQuantity) {
        BigDecimal raw = BigDecimal.valueOf(totalAmount.amountInYen())
                .divide(BigDecimal.valueOf(totalQuantity), 10, RoundingMode.HALF_UP);
        return roundingPolicy.apply(raw);
    }
}
