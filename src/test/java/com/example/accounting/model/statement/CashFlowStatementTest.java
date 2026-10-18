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
 * 前期末B/S(現金500,000・売掛金100,000・商品50,000・備品200,000 / 買掛金80,000・借入金100,000・
 * 資本金500,000・繰越利益剰余金170,000)から始まる当期の取引で、間接法C/Fを導出するテスト。
 *
 * <p>当期の取引: 掛け売上300,000円、掛け売上の回収250,000円、掛け仕入200,000円、
 * 買掛金の支払230,000円、減価償却費20,000円、備品の現金購入100,000円、借入金の現金返済40,000円、
 * 商品の現金購入10,000円。
 *
 * <p>当期純利益80,000円(売上300,000-仕入200,000-減価償却費20,000)。
 * 営業活動CF = 80,000+20,000(償却費加算)-50,000(売掛金増加)-10,000(商品増加)-30,000(買掛金減少)=10,000円。
 * 投資活動CF = -100,000円(備品購入)。財務活動CF = -40,000円(借入金返済)。
 * 現金増減 = 10,000-100,000-40,000 = -130,000円。期首500,000円+増減(-130,000)=期末370,000円。
 */
class CashFlowStatementTest {

    @Test
    void of_derivesIndirectMethodCashFlowAndReconcilesToEndingCash() {
        Journal openingBalances = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 4, 1), "前期末からの開始仕訳",
                JournalType.OPENING,
                List.of(
                        JournalLine.debit(StandardAccounts.CASH, Money.ofYen(500_000)),
                        JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(100_000)),
                        JournalLine.debit(StandardAccounts.MERCHANDISE_INVENTORY, Money.ofYen(50_000)),
                        JournalLine.debit(StandardAccounts.EQUIPMENT, Money.ofYen(200_000)),
                        JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(80_000)),
                        JournalLine.credit(StandardAccounts.LOANS_PAYABLE, Money.ofYen(100_000)),
                        JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(500_000)),
                        JournalLine.credit(StandardAccounts.RETAINED_EARNINGS, Money.ofYen(170_000))
                )
        );
        List<Journal> priorPeriodJournals = List.of(openingBalances);

        List<Journal> currentPeriodJournals = List.of(
                Journal.of(JournalId.newId(), LocalDate.of(2026, 5, 1), "商品の掛け売上", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(300_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(300_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 6, 1), "売掛金の回収", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(250_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(250_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 5, 10), "商品の掛け仕入", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(200_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(200_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 6, 10), "買掛金の支払", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(230_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(230_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2027, 3, 31), "減価償却費の計上", JournalType.ADJUSTING,
                        List.of(
                                JournalLine.debit(StandardAccounts.DEPRECIATION_EXPENSE, Money.ofYen(20_000)),
                                JournalLine.credit(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, Money.ofYen(20_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 7, 1), "備品の現金購入", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.EQUIPMENT, Money.ofYen(100_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(100_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 8, 1), "借入金の現金返済", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.LOANS_PAYABLE, Money.ofYen(40_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(40_000)))),
                Journal.of(JournalId.newId(), LocalDate.of(2026, 9, 1), "商品の現金購入", JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.MERCHANDISE_INVENTORY, Money.ofYen(10_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(10_000))))
        );

        CashFlowStatement cashFlow = CashFlowStatement.of(
                priorPeriodJournals, currentPeriodJournals, new StandardCashFlowClassifier());

        assertThat(cashFlow.netIncome()).isEqualTo(Money.ofYen(80_000));
        assertThat(cashFlow.depreciationAddBack()).isEqualTo(Money.ofYen(20_000));

        assertThat(cashFlow.operatingWorkingCapitalChanges())
                .containsEntry(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(-50_000))
                .containsEntry(StandardAccounts.MERCHANDISE_INVENTORY, Money.ofYen(-10_000))
                .containsEntry(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(-30_000));

        assertThat(cashFlow.operatingActivitiesCashFlow()).isEqualTo(Money.ofYen(10_000));
        assertThat(cashFlow.investingActivitiesCashFlow()).isEqualTo(Money.ofYen(-100_000));
        assertThat(cashFlow.financingActivitiesCashFlow()).isEqualTo(Money.ofYen(-40_000));
        assertThat(cashFlow.netChangeInCash()).isEqualTo(Money.ofYen(-130_000));

        assertThat(cashFlow.cashAtBeginningOfPeriod()).isEqualTo(Money.ofYen(500_000));
        assertThat(cashFlow.cashAtEndOfPeriod()).isEqualTo(Money.ofYen(370_000));
        assertThat(cashFlow.reconcilesToEndingCash()).isTrue();
    }
}
