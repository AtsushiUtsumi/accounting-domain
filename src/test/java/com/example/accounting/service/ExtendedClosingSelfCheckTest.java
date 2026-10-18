package com.example.accounting.service;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountType;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.journal.PostedJournal;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.statement.BalanceSheet;
import com.example.accounting.model.statement.EquityChangeReason;
import com.example.accounting.model.statement.IncomeStatement;
import com.example.accounting.model.statement.Ledger;
import com.example.accounting.model.statement.StatementOfChangesInEquity;
import com.example.accounting.model.statement.TrialBalance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.journal;
import static com.example.accounting.support.Journals.normal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * フェーズ5〜13で追加した科目・区分(評価・換算差額等、別途積立金、引当金)を含めて決算を締めても、
 * CLAUDE.md の自己検証(収益・費用の残高ゼロ、資産 = 負債 + 純資産、期末残高 = 次期の期首残高、
 * 当期純利益 = 繰越利益剰余金の増加額)が成り立つことを確かめる。
 */
class ExtendedClosingSelfCheckTest {

    private final AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
    private final AdjustingEntryService adjusting = new AdjustingEntryService();

    private List<Journal> journalsBeforeClosing() {
        LocalDate end = period.endDate();
        List<Journal> journals = new ArrayList<>(List.of(
                journal(period.startDate(), JournalType.OPENING,
                        dr(StandardAccounts.CASH, 2_000_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 1_500_000),
                        cr(StandardAccounts.RETAINED_EARNINGS, 500_000)),
                normal(LocalDate.of(2026, 5, 1),
                        dr(StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, 500_000), cr(StandardAccounts.CASH, 500_000)),
                normal(LocalDate.of(2026, 6, 1), dr(StandardAccounts.CASH, 800_000), cr(StandardAccounts.SALES, 800_000)),
                normal(LocalDate.of(2026, 6, 2),
                        dr(StandardAccounts.PURCHASES, 400_000), cr(StandardAccounts.CHECKING_ACCOUNT, 400_000)),
                normal(LocalDate.of(2026, 6, 30),
                        dr(StandardAccounts.RETAINED_EARNINGS, 100_000), cr(StandardAccounts.GENERAL_RESERVE, 100_000))));
        journals.add(adjusting.reclassifyBankOverdraft(StandardAccounts.CHECKING_ACCOUNT, journals, end).orElseThrow());
        journals.add(adjusting.valuateAvailableForSaleSecurities(
                StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, yen(530_000), journals, end).orElseThrow());
        journals.add(adjusting.adjustProvision(ProvisionType.BONUSES, yen(50_000), journals, end).orElseThrow());
        return journals;
    }

    @Test
    void closingKeepsTheSelfChecks() {
        List<Journal> journals = journalsBeforeClosing();
        Money netIncome = IncomeStatement.of(journals).netIncome();
        Money retainedEarningsBefore = Ledger.of(StandardAccounts.RETAINED_EARNINGS, journals).balance();

        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, journals));
        List<Journal> afterClosing = new ArrayList<>(journals);
        afterClosing.addAll(PostedJournal.journalsOf(result.closingEntries()));

        // 収益・費用の残高がすべてゼロ
        TrialBalance trialBalance = TrialBalance.of(afterClosing);
        for (Map.Entry<Account, TrialBalance.Row> row : trialBalance.rows().entrySet()) {
            AccountType type = row.getKey().type();
            if (type == AccountType.REVENUE || type == AccountType.EXPENSE) {
                assertThat(row.getValue().netBalance(type)).as(row.getKey().name()).isEqualTo(Money.ZERO);
            }
        }

        // 資産 = 負債 + 純資産(評価・換算差額等を含む)
        BalanceSheet balanceSheet = BalanceSheet.of(afterClosing);
        assertThat(balanceSheet.isBalanced()).isTrue();
        assertThat(balanceSheet.equity())
                .containsEntry(StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES, yen(30_000));

        // 当期純利益 = 繰越利益剰余金の増加額
        assertThat(netIncome).isEqualTo(yen(350_000));
        assertThat(Ledger.of(StandardAccounts.RETAINED_EARNINGS, afterClosing).balance().minus(retainedEarningsBefore))
                .isEqualTo(netIncome);

        // 前期の期末残高 = 次期の期首残高
        BalanceSheet opening = BalanceSheet.of(PostedJournal.journalsOf(result.openingEntries()));
        assertThat(opening.assets()).isEqualTo(withoutZeros(balanceSheet.assets()));
        assertThat(opening.liabilities()).isEqualTo(withoutZeros(balanceSheet.liabilities()));
        assertThat(opening.equity()).isEqualTo(withoutZeros(balanceSheet.equity()));
    }

    /** 株主資本等変動計算書: 別途積立金の積立は計数の変動、評価差額は株主資本以外の変動として分かれる。 */
    @Test
    void statementOfChangesInEquityClassifiesTheNewReasons() {
        List<Journal> journals = journalsBeforeClosing();
        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, journals));
        List<Journal> afterClosing = new ArrayList<>(journals);
        afterClosing.addAll(PostedJournal.journalsOf(result.closingEntries()));

        StatementOfChangesInEquity statement = StatementOfChangesInEquity.of(afterClosing);

        assertThat(statement.reconciles()).isTrue();
        assertThat(statement.changesByReason().get(EquityChangeReason.TRANSFER_WITHIN_EQUITY))
                .containsEntry(StandardAccounts.GENERAL_RESERVE, yen(100_000))
                .containsEntry(StandardAccounts.RETAINED_EARNINGS, yen(-100_000));
        assertThat(statement.changesByReason().get(EquityChangeReason.VALUATION_CHANGE))
                .containsEntry(StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES, yen(30_000));
        assertThat(statement.changesByReason().get(EquityChangeReason.NET_INCOME))
                .containsEntry(StandardAccounts.RETAINED_EARNINGS, yen(350_000));
    }

    private static Map<Account, Money> withoutZeros(Map<Account, Money> balances) {
        Map<Account, Money> result = new HashMap<>();
        balances.forEach((account, amount) -> {
            if (!amount.isZero()) {
                result.put(account, amount);
            }
        });
        return result;
    }
}
