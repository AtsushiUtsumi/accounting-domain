package com.example.accounting.model.statement;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「サンプル商店」4月の取引([[TrialBalanceTest]]と同じ6件)に、保険料の現金支払12,000円を追加し、
 * 決算整理で期末に2,000円分(次期分)を前払費用へ振り替える8桁精算表のテスト。
 *
 * <p>決算整理後: 保険料(P/L)は12,000-2,000=10,000円、前払費用(B/S)は2,000円。
 * 売上280,000円に対し仕入300,000円・保険料10,000円のため、当期純損失30,000円になる。
 */
class WorksheetTest {

    @Test
    void of_derivesAllFourColumnPairsAndSelfBalances() {
        List<Journal> beforeAdjustment = List.of(
                Journal.of(JournalId.newId(), LocalDate.of(2026, 4, 1), "出資による開業", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                                JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 4, 5), "銀行からの借入", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(200_000)),
                                JournalLine.credit(StandardAccounts.LOANS_PAYABLE, Money.ofYen(200_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 4, 10), "商品の掛け仕入", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(300_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(300_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 4, 15), "商品の掛け売上", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(280_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(280_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 4, 20), "買掛金の現金支払", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(150_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(150_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 4, 25), "売掛金の現金回収", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(180_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(180_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 5, 1), "保険料の現金支払", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.INSURANCE_EXPENSE, Money.ofYen(12_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(12_000))))
        );

        List<Journal> adjusting = List.of(
                Journal.of(JournalId.newId(), LocalDate.of(2027, 3, 31), "保険料の前払い(次期分2,000円)",
                        JournalType.ADJUSTING,
                        List.of(
                                JournalLine.debit(StandardAccounts.PREPAID_EXPENSE, Money.ofYen(2_000)),
                                JournalLine.credit(StandardAccounts.INSURANCE_EXPENSE, Money.ofYen(2_000))))
        );

        Worksheet worksheet = Worksheet.of(beforeAdjustment, adjusting);

        assertThat(worksheet.trialBalanceDebitTotal()).isEqualTo(Money.ofYen(1_722_000));
        assertThat(worksheet.trialBalanceCreditTotal()).isEqualTo(Money.ofYen(1_722_000));

        Worksheet.Row cash = worksheet.rows().get(StandardAccounts.CASH);
        assertThat(cash.trialBalanceDebit()).isEqualTo(Money.ofYen(980_000));
        assertThat(cash.trialBalanceCredit()).isEqualTo(Money.ofYen(162_000));
        assertThat(cash.balanceSheetDebit()).isEqualTo(Money.ofYen(818_000));
        assertThat(cash.balanceSheetCredit()).isEqualTo(Money.ZERO);

        Worksheet.Row prepaidExpense = worksheet.rows().get(StandardAccounts.PREPAID_EXPENSE);
        assertThat(prepaidExpense.trialBalanceDebit()).isEqualTo(Money.ZERO);
        assertThat(prepaidExpense.adjustmentDebit()).isEqualTo(Money.ofYen(2_000));
        assertThat(prepaidExpense.balanceSheetDebit()).isEqualTo(Money.ofYen(2_000));

        Worksheet.Row insurance = worksheet.rows().get(StandardAccounts.INSURANCE_EXPENSE);
        assertThat(insurance.trialBalanceDebit()).isEqualTo(Money.ofYen(12_000));
        assertThat(insurance.adjustmentCredit()).isEqualTo(Money.ofYen(2_000));
        assertThat(insurance.incomeStatementDebit()).isEqualTo(Money.ofYen(10_000));

        Worksheet.Row sales = worksheet.rows().get(StandardAccounts.SALES);
        assertThat(sales.incomeStatementCredit()).isEqualTo(Money.ofYen(280_000));

        Worksheet.Row purchases = worksheet.rows().get(StandardAccounts.PURCHASES);
        assertThat(purchases.incomeStatementDebit()).isEqualTo(Money.ofYen(300_000));

        assertThat(worksheet.incomeStatementDebitTotal()).isEqualTo(Money.ofYen(310_000));
        assertThat(worksheet.incomeStatementCreditTotal()).isEqualTo(Money.ofYen(280_000));
        assertThat(worksheet.balanceSheetDebitTotal()).isEqualTo(Money.ofYen(920_000));
        assertThat(worksheet.balanceSheetCreditTotal()).isEqualTo(Money.ofYen(950_000));

        assertThat(worksheet.netIncome()).isEqualTo(Money.ofYen(-30_000));
        assertThat(worksheet.isBalanced()).isTrue();
    }
}
