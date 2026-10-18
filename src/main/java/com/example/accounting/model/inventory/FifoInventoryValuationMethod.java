package com.example.accounting.model.inventory;

import com.example.accounting.exception.AccountingException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 先入先出法。古い仕入(ロット)から順に払い出す。在庫を取得順のロットの列として管理する。 */
public final class FifoInventoryValuationMethod implements InventoryValuationMethod {

    public static FifoInventoryValuationMethod of() {
        return new FifoInventoryValuationMethod();
    }

    @Override
    public List<InventoryLedgerEntry> valuate(List<InventoryMovement> movements) {
        Deque<InventoryLot> lots = new ArrayDeque<>();
        List<InventoryLedgerEntry> entries = new ArrayList<>();

        for (InventoryMovement movement : movements) {
            switch (movement) {
                case InventoryMovement.Receipt receipt -> {
                    InventoryLot lot = new InventoryLot(receipt.quantity(), receipt.unitCost());
                    lots.addLast(lot);
                    entries.add(new InventoryLedgerEntry(
                            receipt.date(), receipt.description(),
                            List.of(lot), List.of(), List.copyOf(lots)));
                }
                case InventoryMovement.Issue issue -> {
                    List<InventoryLot> consumed = consume(lots, issue.quantity());
                    entries.add(new InventoryLedgerEntry(
                            issue.date(), issue.description(),
                            List.of(), consumed, List.copyOf(lots)));
                }
            }
        }
        return entries;
    }

    private List<InventoryLot> consume(Deque<InventoryLot> lots, int quantity) {
        List<InventoryLot> consumed = new ArrayList<>();
        int remaining = quantity;
        while (remaining > 0) {
            InventoryLot front = lots.pollFirst();
            if (front == null) {
                throw new AccountingException("在庫不足: 払出数量が残高を超えている");
            }
            if (front.quantity() > remaining) {
                consumed.add(new InventoryLot(remaining, front.unitCost()));
                lots.addFirst(new InventoryLot(front.quantity() - remaining, front.unitCost()));
                remaining = 0;
            } else {
                consumed.add(front);
                remaining -= front.quantity();
            }
        }
        return consumed;
    }
}
