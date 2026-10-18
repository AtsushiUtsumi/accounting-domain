package com.example.accounting.model.inventory;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 商品有高帳(A商品)。
 * 前月繰越10個@100円、仕入30個@120円、売上25個、仕入25個@123円、売上30個。
 */
class InventoryValuationMethodTest {

    private static List<InventoryMovement> movements() {
        return List.of(
                new InventoryMovement.Receipt(LocalDate.of(2026, 10, 1), "前月繰越", 10, Money.ofYen(100)),
                new InventoryMovement.Receipt(LocalDate.of(2026, 10, 5), "仕入", 30, Money.ofYen(120)),
                new InventoryMovement.Issue(LocalDate.of(2026, 10, 12), "売上", 25),
                new InventoryMovement.Receipt(LocalDate.of(2026, 10, 20), "仕入", 25, Money.ofYen(123)),
                new InventoryMovement.Issue(LocalDate.of(2026, 10, 25), "売上", 30)
        );
    }

    @Test
    void fifo_splitsAnIssueAcrossLotsWhenUnitCostsDiffer() {
        List<InventoryLedgerEntry> entries = FifoInventoryValuationMethod.of().valuate(movements());

        assertThat(entries).hasSize(5);

        InventoryLedgerEntry firstSale = entries.get(2);
        assertThat(firstSale.issues()).extracting(InventoryLot::quantity, lot -> lot.unitCost().amountInYen())
                .containsExactly(tuple(10, 100L), tuple(15, 120L));
        assertThat(firstSale.issueAmount()).isEqualTo(Money.ofYen(2_800));
        assertThat(firstSale.balance()).containsExactly(new InventoryLot(15, Money.ofYen(120)));

        InventoryLedgerEntry secondSale = entries.get(4);
        assertThat(secondSale.issues()).extracting(InventoryLot::quantity, lot -> lot.unitCost().amountInYen())
                .containsExactly(tuple(15, 120L), tuple(15, 123L));
        assertThat(secondSale.issueAmount()).isEqualTo(Money.ofYen(3_645));
        assertThat(secondSale.balance()).containsExactly(new InventoryLot(10, Money.ofYen(123)));
    }

    @Test
    void fifo_receivedTotalMinusIssuedTotalEqualsEndingBalance() {
        List<InventoryLedgerEntry> entries = FifoInventoryValuationMethod.of().valuate(movements());

        Money totalReceived = entries.stream().map(InventoryLedgerEntry::receiptAmount).reduce(Money.ZERO, Money::plus);
        Money totalIssued = entries.stream().map(InventoryLedgerEntry::issueAmount).reduce(Money.ZERO, Money::plus);
        InventoryLedgerEntry last = entries.get(entries.size() - 1);

        assertThat(totalReceived).isEqualTo(Money.ofYen(7_675));
        assertThat(totalReceived.minus(totalIssued)).isEqualTo(last.balanceAmount());
        assertThat(last.balanceAmount()).isEqualTo(Money.ofYen(1_230));
        assertThat(last.balanceQuantity()).isEqualTo(10);
    }

    @Test
    void fifo_issuingMoreThanOnHand_isRejected() {
        FifoInventoryValuationMethod method = FifoInventoryValuationMethod.of();
        List<InventoryMovement> tooMuch = List.of(
                new InventoryMovement.Receipt(LocalDate.of(2026, 10, 1), "前月繰越", 10, Money.ofYen(100)),
                new InventoryMovement.Issue(LocalDate.of(2026, 10, 2), "売上", 20)
        );

        assertThatThrownBy(() -> method.valuate(tooMuch)).isInstanceOf(AccountingException.class);
    }

    @Test
    void movingAverage_recomputesUnitCostOnEveryReceipt() {
        List<InventoryLedgerEntry> entries = MovingAverageInventoryValuationMethod.of().valuate(movements());

        InventoryLedgerEntry afterSecondReceipt = entries.get(1);
        assertThat(afterSecondReceipt.balance()).containsExactly(new InventoryLot(40, Money.ofYen(115)));

        InventoryLedgerEntry firstSale = entries.get(2);
        assertThat(firstSale.issues()).containsExactly(new InventoryLot(25, Money.ofYen(115)));
        assertThat(firstSale.issueAmount()).isEqualTo(Money.ofYen(2_875));
        assertThat(firstSale.balance()).containsExactly(new InventoryLot(15, Money.ofYen(115)));

        InventoryLedgerEntry afterThirdReceipt = entries.get(3);
        assertThat(afterThirdReceipt.balance()).containsExactly(new InventoryLot(40, Money.ofYen(120)));

        InventoryLedgerEntry secondSale = entries.get(4);
        assertThat(secondSale.issues()).containsExactly(new InventoryLot(30, Money.ofYen(120)));
        assertThat(secondSale.issueAmount()).isEqualTo(Money.ofYen(3_600));
        assertThat(secondSale.balance()).containsExactly(new InventoryLot(10, Money.ofYen(120)));
    }

    @Test
    void movingAverage_receivedTotalMinusIssuedTotalEqualsEndingBalance() {
        List<InventoryLedgerEntry> entries = MovingAverageInventoryValuationMethod.of().valuate(movements());

        Money totalReceived = entries.stream().map(InventoryLedgerEntry::receiptAmount).reduce(Money.ZERO, Money::plus);
        Money totalIssued = entries.stream().map(InventoryLedgerEntry::issueAmount).reduce(Money.ZERO, Money::plus);
        InventoryLedgerEntry last = entries.get(entries.size() - 1);

        assertThat(totalReceived).isEqualTo(Money.ofYen(7_675));
        assertThat(totalReceived.minus(totalIssued)).isEqualTo(last.balanceAmount());
        assertThat(last.balanceAmount()).isEqualTo(Money.ofYen(1_200));
        assertThat(last.balanceQuantity()).isEqualTo(10);
    }

    @Test
    void movingAverage_issuingMoreThanOnHand_isRejected() {
        MovingAverageInventoryValuationMethod method = MovingAverageInventoryValuationMethod.of();
        List<InventoryMovement> tooMuch = List.of(
                new InventoryMovement.Receipt(LocalDate.of(2026, 10, 1), "前月繰越", 10, Money.ofYen(100)),
                new InventoryMovement.Issue(LocalDate.of(2026, 10, 2), "売上", 20)
        );

        assertThatThrownBy(() -> method.valuate(tooMuch)).isInstanceOf(AccountingException.class);
    }
}
