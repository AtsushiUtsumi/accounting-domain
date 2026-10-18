package com.example.accounting.service;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.journal.PostedJournal;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.period.PeriodStatus;
import com.example.accounting.model.statement.BalanceSheet;
import com.example.accounting.model.statement.Ledger;
import com.example.accounting.model.statement.TrialBalance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「サンプル商店」4月の取引([[TrialBalanceTest]]と同じ取引)を締めるテスト。
 * 売上280,000円・仕入300,000円のため当期純損失20,000円になる。
 *
 * <p>CLAUDE.md が要求する自己検証をすべて含む:
 * 締め後の収益・費用残高ゼロ、締め後B/Sで資産=負債+純資産、
 * 前期末B/S残高=次期期首残高、当期純利益(損失)=繰越利益剰余金の増加額。
 */
class ClosingServiceTest {

    private static final List<Journal> APRIL_JOURNALS = List.of(
            Journal.of(
                    JournalId.newId(), LocalDate.of(2026, 4, 1), "出資による開業",
                    JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                            JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000))
                    )
            ),
            Journal.of(
                    JournalId.newId(), LocalDate.of(2026, 4, 5), "銀行からの借入",
                    JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.CASH, Money.ofYen(200_000)),
                            JournalLine.credit(StandardAccounts.LOANS_PAYABLE, Money.ofYen(200_000))
                    )
            ),
            Journal.of(
                    JournalId.newId(), LocalDate.of(2026, 4, 10), "商品の掛け仕入",
                    JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(300_000)),
                            JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(300_000))
                    )
            ),
            Journal.of(
                    JournalId.newId(), LocalDate.of(2026, 4, 15), "商品の掛け売上",
                    JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(280_000)),
                            JournalLine.credit(StandardAccounts.SALES, Money.ofYen(280_000))
                    )
            ),
            Journal.of(
                    JournalId.newId(), LocalDate.of(2026, 4, 20), "買掛金の現金支払",
                    JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(150_000)),
                            JournalLine.credit(StandardAccounts.CASH, Money.ofYen(150_000))
                    )
            ),
            Journal.of(
                    JournalId.newId(), LocalDate.of(2026, 4, 25), "売掛金の現金回収",
                    JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.CASH, Money.ofYen(180_000)),
                            JournalLine.credit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(180_000))
                    )
            )
    );

    @Test
    void close_zeroesOutRevenueAndExpenseAccounts() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, APRIL_JOURNALS));

        List<Journal> allJournals = combine(APRIL_JOURNALS, PostedJournal.journalsOf(result.closingEntries()));

        assertThat(Ledger.of(StandardAccounts.SALES, allJournals).balance()).isEqualTo(Money.ZERO);
        assertThat(Ledger.of(StandardAccounts.PURCHASES, allJournals).balance()).isEqualTo(Money.ZERO);
        assertThat(Ledger.of(StandardAccounts.PROFIT_AND_LOSS, allJournals).balance()).isEqualTo(Money.ZERO);
    }

    @Test
    void close_producesBalancedBalanceSheetAfterClosing() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, APRIL_JOURNALS));

        List<Journal> allJournals = combine(APRIL_JOURNALS, PostedJournal.journalsOf(result.closingEntries()));
        BalanceSheet balanceSheet = BalanceSheet.of(allJournals);

        assertThat(balanceSheet.isBalanced()).isTrue();
        assertThat(balanceSheet.totalAssets()).isEqualTo(Money.ofYen(930_000));
        assertThat(balanceSheet.totalLiabilities()).isEqualTo(Money.ofYen(350_000));
        assertThat(balanceSheet.totalEquity()).isEqualTo(Money.ofYen(580_000));
    }

    @Test
    void close_openingEntriesCarryForwardPriorPeriodEndingBalances() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, APRIL_JOURNALS));

        List<Journal> allJournals = combine(APRIL_JOURNALS, PostedJournal.journalsOf(result.closingEntries()));
        TrialBalance endOfPeriod = TrialBalance.of(allJournals);

        for (Account account : List.of(
                StandardAccounts.CASH, StandardAccounts.ACCOUNTS_RECEIVABLE,
                StandardAccounts.ACCOUNTS_PAYABLE, StandardAccounts.LOANS_PAYABLE,
                StandardAccounts.CAPITAL_STOCK, StandardAccounts.RETAINED_EARNINGS)) {
            Money endingBalance = endOfPeriod.rows().get(account).netBalance(account.type());
            Money openingBalance = Ledger.of(account, PostedJournal.journalsOf(result.openingEntries())).balance();
            assertThat(openingBalance)
                    .as("opening balance for %s", account.name())
                    .isEqualTo(endingBalance);
        }

        assertThat(result.closedPeriod().status()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(result.nextPeriod().startDate()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(result.nextPeriod().status()).isEqualTo(PeriodStatus.OPEN);
    }

    @Test
    void close_netIncomeEqualsChangeInRetainedEarnings() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

        Money retainedEarningsBefore = Ledger.of(StandardAccounts.RETAINED_EARNINGS, APRIL_JOURNALS).balance();

        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, APRIL_JOURNALS));
        List<Journal> allJournals = combine(APRIL_JOURNALS, PostedJournal.journalsOf(result.closingEntries()));
        Money retainedEarningsAfter = Ledger.of(StandardAccounts.RETAINED_EARNINGS, allJournals).balance();

        Money netIncome = com.example.accounting.model.statement.IncomeStatement.of(APRIL_JOURNALS).netIncome();

        assertThat(netIncome).isEqualTo(Money.ofYen(-20_000));
        assertThat(retainedEarningsAfter.minus(retainedEarningsBefore)).isEqualTo(netIncome);
    }

    @Test
    void close_onAlreadyClosedPeriod_isRejected() {
        AccountingPeriod open = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        List<PostedJournal> posted = PostedJournal.postAll(open, APRIL_JOURNALS);
        AccountingPeriod closed = open.close();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ClosingService().close(closed, posted))
                .isInstanceOf(com.example.accounting.exception.AccountingException.class);
    }

    /** 損益振替は締める前の当期(期末日付)に、開始仕訳は次期(期首日付)に記帳されている。 */
    @Test
    void close_postsClosingEntriesToThisPeriodAndOpeningEntriesToTheNext() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        ClosingResult result = new ClosingService().close(period, PostedJournal.postAll(period, APRIL_JOURNALS));

        assertThat(result.closingEntries()).isNotEmpty().allSatisfy(posted -> {
            assertThat(posted.periodId()).isEqualTo(period.id());
            assertThat(posted.periodVersion()).isEqualTo(period.version());
            assertThat(posted.date()).isEqualTo(period.endDate());
        });
        assertThat(result.openingEntries()).isNotEmpty().allSatisfy(posted -> {
            assertThat(posted.periodId()).isEqualTo(result.nextPeriod().id());
            assertThat(posted.date()).isEqualTo(result.nextPeriod().startDate());
        });
    }

    /** 他の期間に記帳された仕訳が混ざっていれば締められない。 */
    @Test
    void close_rejectsJournalsPostedToAnotherPeriod() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        AccountingPeriod other = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ClosingService().close(period, PostedJournal.postAll(other, APRIL_JOURNALS)))
                .isInstanceOf(com.example.accounting.exception.AccountingException.class);
    }

    private static List<Journal> combine(List<Journal> original, List<Journal> closing) {
        List<Journal> combined = new ArrayList<>(original);
        combined.addAll(closing);
        return combined;
    }
}
