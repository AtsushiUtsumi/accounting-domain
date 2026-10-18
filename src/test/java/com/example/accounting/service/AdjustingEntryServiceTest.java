package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.Ledger;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdjustingEntryServiceTest {

    private final AdjustingEntryService service = new AdjustingEntryService();

    /**
     * 売上原価の算定(三分法)。期首商品棚卸高80,000円、当期仕入300,000円、
     * 期末商品棚卸高50,000円のとき、売上原価は80,000+300,000-50,000=330,000円になる。
     */
    @Test
    void recognizeCostOfSales_adjustsPurchasesByBeginningAndEndingInventory() {
        // 期首商品棚卸高80,000円(前期から繰越商品として保有している状態を模した仕訳)。
        Journal beginningInventoryOnHand = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 4, 1), "期首の繰越商品残高",
                JournalType.OPENING,
                List.of(
                        JournalLine.debit(StandardAccounts.MERCHANDISE_INVENTORY, Money.ofYen(80_000)),
                        JournalLine.credit(StandardAccounts.CASH, Money.ofYen(80_000))
                )
        );
        Journal periodPurchase = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 5, 1), "当期の仕入",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(300_000)),
                        JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(300_000))
                )
        );

        List<Journal> adjusting = service.recognizeCostOfSales(
                Money.ofYen(80_000), Money.ofYen(50_000), LocalDate.of(2027, 3, 31));

        List<Journal> all = new ArrayList<>();
        all.add(beginningInventoryOnHand);
        all.add(periodPurchase);
        all.addAll(adjusting);

        assertThat(Ledger.of(StandardAccounts.PURCHASES, all).balance()).isEqualTo(Money.ofYen(330_000));
        assertThat(Ledger.of(StandardAccounts.MERCHANDISE_INVENTORY, all).balance()).isEqualTo(Money.ofYen(50_000));
    }

    @Test
    void recognizeCostOfSales_skipsZeroAmounts() {
        List<Journal> adjusting = service.recognizeCostOfSales(
                Money.ZERO, Money.ofYen(50_000), LocalDate.of(2027, 3, 31));

        assertThat(adjusting).hasSize(1);
    }

    /**
     * 貸倒引当金の差額補充法。売掛金期末残高に対する必要額20,000円に対し、
     * 現在残高が5,000円なら15,000円繰入、現在残高が30,000円なら10,000円取り崩す。
     */
    @Test
    void adjustAllowanceForDoubtfulAccounts_suppliesTheShortfall() {
        Journal existingAllowance = Journal.of(
                JournalId.newId(), LocalDate.of(2025, 3, 31), "前期末の貸倒引当金",
                JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.BAD_DEBT_EXPENSE, Money.ofYen(5_000)),
                        JournalLine.credit(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, Money.ofYen(5_000))
                )
        );

        var result = service.adjustAllowanceForDoubtfulAccounts(
                Money.ofYen(20_000), List.of(existingAllowance), LocalDate.of(2026, 3, 31));

        assertThat(result).isPresent();
        Journal journal = result.get();
        assertThat(journal.debitTotal()).isEqualTo(Money.ofYen(15_000));
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.BAD_DEBT_EXPENSE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(15_000));
        });
    }

    @Test
    void adjustAllowanceForDoubtfulAccounts_reversesTheExcess() {
        Journal existingAllowance = Journal.of(
                JournalId.newId(), LocalDate.of(2025, 3, 31), "前期末の貸倒引当金",
                JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.BAD_DEBT_EXPENSE, Money.ofYen(30_000)),
                        JournalLine.credit(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, Money.ofYen(30_000))
                )
        );

        var result = service.adjustAllowanceForDoubtfulAccounts(
                Money.ofYen(20_000), List.of(existingAllowance), LocalDate.of(2026, 3, 31));

        assertThat(result).isPresent();
        Journal journal = result.get();
        assertThat(journal.debitTotal()).isEqualTo(Money.ofYen(10_000));
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS_REVERSAL);
            assertThat(line.amount()).isEqualTo(Money.ofYen(10_000));
        });
    }

    /**
     * 法人税等の計上。確定額120,000円に対し仮払法人税等60,000円があれば、未払法人税等は60,000円。
     * 仮払がなければ全額が未払法人税等になる。仮払が確定額を超える場合は例外。
     */
    @Test
    void accrueIncomeTax_nettsAgainstPrepaidTax() {
        Journal interimPayment = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 11, 30), "法人税等の中間納付",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.PREPAID_CORPORATE_TAX, Money.ofYen(60_000)),
                        JournalLine.credit(StandardAccounts.CASH, Money.ofYen(60_000))
                )
        );

        Journal journal = service.accrueIncomeTax(
                Money.ofYen(120_000), List.of(interimPayment), LocalDate.of(2027, 3, 31));

        assertThat(journal.debitTotal()).isEqualTo(Money.ofYen(120_000));
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.ACCRUED_CORPORATE_TAX);
            assertThat(line.amount()).isEqualTo(Money.ofYen(60_000));
        });
    }

    @Test
    void accrueIncomeTax_withoutPrepayment_payablesTheFullAmount() {
        Journal journal = service.accrueIncomeTax(Money.ofYen(120_000), List.of(), LocalDate.of(2027, 3, 31));

        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.ACCRUED_CORPORATE_TAX);
            assertThat(line.amount()).isEqualTo(Money.ofYen(120_000));
        });
    }

    @Test
    void accrueIncomeTax_prepaidExceedingDeterminedAmount_isRejected() {
        Journal interimPayment = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 11, 30), "法人税等の中間納付",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.PREPAID_CORPORATE_TAX, Money.ofYen(150_000)),
                        JournalLine.credit(StandardAccounts.CASH, Money.ofYen(150_000))
                )
        );

        assertThatThrownBy(() -> service.accrueIncomeTax(
                Money.ofYen(120_000), List.of(interimPayment), LocalDate.of(2027, 3, 31)))
                .isInstanceOf(AccountingException.class);
    }

    /**
     * 消費税(税抜方式)の精算。仮受消費税80,000円・仮払消費税50,000円なら未払消費税30,000円、
     * 仮受30,000円・仮払50,000円(逆)なら未収消費税20,000円。
     */
    @Test
    void settleConsumptionTax_payable_whenReceivedExceedsPaid() {
        List<Journal> journals = List.of(
                Journal.of(JournalId.newId(), LocalDate.of(2026, 6, 1), "売上に伴う仮受消費税",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(880_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(800_000)),
                                JournalLine.credit(StandardAccounts.CONSUMPTION_TAX_RECEIVED, Money.ofYen(80_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 6, 2), "仕入に伴う仮払消費税",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(500_000)),
                                JournalLine.debit(StandardAccounts.CONSUMPTION_TAX_PAID, Money.ofYen(50_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(550_000))))
        );

        var result = service.settleConsumptionTax(journals, LocalDate.of(2027, 3, 31));

        assertThat(result).isPresent();
        assertThat(result.get().lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.CONSUMPTION_TAX_PAYABLE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(30_000));
        });
    }

    @Test
    void settleConsumptionTax_refundable_whenPaidExceedsReceived() {
        List<Journal> journals = List.of(
                Journal.of(JournalId.newId(), LocalDate.of(2026, 6, 1), "売上に伴う仮受消費税",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(330_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(300_000)),
                                JournalLine.credit(StandardAccounts.CONSUMPTION_TAX_RECEIVED, Money.ofYen(30_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 6, 2), "仕入に伴う仮払消費税",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(500_000)),
                                JournalLine.debit(StandardAccounts.CONSUMPTION_TAX_PAID, Money.ofYen(50_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(550_000))))
        );

        var result = service.settleConsumptionTax(journals, LocalDate.of(2027, 3, 31));

        assertThat(result).isPresent();
        assertThat(result.get().lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.CONSUMPTION_TAX_REFUNDABLE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(20_000));
        });
    }

    @Test
    void settleConsumptionTax_noActivity_producesNoJournal() {
        var result = service.settleConsumptionTax(List.of(), LocalDate.of(2027, 3, 31));

        assertThat(result).isEmpty();
    }

    /**
     * 再振替仕訳。前期末の決算整理仕訳「(借)前払費用12,000 / (貸)保険料12,000」を
     * 再振替すると「(借)保険料12,000 / (貸)前払費用12,000」になる。経過勘定以外の決算整理
     * (ここでは法人税等の計上)は再振替の対象にならない。
     */
    @Test
    void reverseAccruals_flipsOnlyAccrualAdjustingEntries() {
        Journal prepaidInsuranceAdjustment = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 3, 31), "保険料の前払い",
                JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.PREPAID_EXPENSE, Money.ofYen(12_000)),
                        JournalLine.credit(StandardAccounts.INSURANCE_EXPENSE, Money.ofYen(12_000))
                )
        );
        Journal incomeTaxAdjustment = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 3, 31), "法人税等の計上",
                JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.CORPORATE_TAX_EXPENSE, Money.ofYen(120_000)),
                        JournalLine.credit(StandardAccounts.ACCRUED_CORPORATE_TAX, Money.ofYen(120_000))
                )
        );

        List<Journal> reversed = service.reverseAccruals(
                List.of(prepaidInsuranceAdjustment, incomeTaxAdjustment), LocalDate.of(2026, 4, 1));

        assertThat(reversed).hasSize(1);
        Journal reversal = reversed.get(0);
        assertThat(reversal.type()).isEqualTo(JournalType.REVERSING);
        assertThat(reversal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.INSURANCE_EXPENSE);
            assertThat(line.side()).isEqualTo(com.example.accounting.model.account.Side.DEBIT);
            assertThat(line.amount()).isEqualTo(Money.ofYen(12_000));
        });
        assertThat(reversal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.PREPAID_EXPENSE);
            assertThat(line.side()).isEqualTo(com.example.accounting.model.account.Side.CREDIT);
            assertThat(line.amount()).isEqualTo(Money.ofYen(12_000));
        });
    }
}
