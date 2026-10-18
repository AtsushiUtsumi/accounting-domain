package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.period.AccountingPeriod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.journal;
import static com.example.accounting.support.Journals.normal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 記帳(会計期間に仕訳を載せる)のルール。会計期間は2026年4月1日〜2027年3月31日。 */
class PostedJournalTest {

    private final AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

    private static Journal saleOn(LocalDate date) {
        return normal(date, dr(StandardAccounts.CASH, 1_000), cr(StandardAccounts.SALES, 1_000));
    }

    @Test
    void post_toAnOpenPeriodWithinItsDates() {
        Journal journal = saleOn(LocalDate.of(2026, 7, 1));

        PostedJournal posted = PostedJournal.post(period, journal);

        assertThat(posted.journal()).isEqualTo(journal);
        assertThat(posted.periodId()).isEqualTo(period.id());
        assertThat(posted.periodVersion()).isEqualTo(period.version());
    }

    @Test
    void post_acceptsTheFirstAndLastDayOfThePeriod() {
        assertThat(PostedJournal.post(period, saleOn(LocalDate.of(2026, 4, 1)))).isNotNull();
        assertThat(PostedJournal.post(period, saleOn(LocalDate.of(2027, 3, 31)))).isNotNull();
    }

    @Test
    void post_toAClosedPeriod_isRejected() {
        AccountingPeriod closed = period.close();

        assertThatThrownBy(() -> PostedJournal.post(closed, saleOn(LocalDate.of(2026, 7, 1))))
                .isInstanceOf(AccountingException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void post_outsideThePeriod_isRejected() {
        assertThatThrownBy(() -> PostedJournal.post(period, saleOn(LocalDate.of(2026, 3, 31))))
                .isInstanceOf(AccountingException.class);
        assertThatThrownBy(() -> PostedJournal.post(period, saleOn(LocalDate.of(2027, 4, 1))))
                .isInstanceOf(AccountingException.class);
    }

    @Test
    void postAll_rejectsAllWhenOneIsInvalid() {
        List<Journal> journals = List.of(saleOn(LocalDate.of(2026, 7, 1)), saleOn(LocalDate.of(2027, 4, 1)));

        assertThatThrownBy(() -> PostedJournal.postAll(period, journals)).isInstanceOf(AccountingException.class);
    }

    /** 開始仕訳・再振替仕訳は期首、損益振替仕訳は期末の日付でしか記帳できない(期待値は未検証)。 */
    @Test
    @Tag("unverified")
    void post_restrictsTheDateBySomeJournalTypes() {
        LocalDate midYear = LocalDate.of(2026, 7, 1);
        for (JournalType type : List.of(JournalType.OPENING, JournalType.REVERSING, JournalType.CLOSING)) {
            Journal journal = journal(midYear, type, dr(StandardAccounts.CASH, 1_000), cr(StandardAccounts.SALES, 1_000));
            assertThatThrownBy(() -> PostedJournal.post(period, journal))
                    .as(type.name())
                    .isInstanceOf(AccountingException.class);
        }

        assertThat(PostedJournal.post(period, journal(period.startDate(), JournalType.OPENING,
                dr(StandardAccounts.CASH, 1_000), cr(StandardAccounts.CAPITAL_STOCK, 1_000)))).isNotNull();
        assertThat(PostedJournal.post(period, journal(period.startDate(), JournalType.REVERSING,
                dr(StandardAccounts.ACCRUED_EXPENSE, 1_000), cr(StandardAccounts.INSURANCE_EXPENSE, 1_000)))).isNotNull();
        assertThat(PostedJournal.post(period, journal(period.endDate(), JournalType.CLOSING,
                dr(StandardAccounts.SALES, 1_000), cr(StandardAccounts.PROFIT_AND_LOSS, 1_000)))).isNotNull();
    }

    /** 決算整理は期末以外の日付でも記帳できる(期中売却した固定資産の、売却日までの減価償却など)。 */
    @Test
    void post_allowsAdjustingEntriesOnAnyDateWithinThePeriod() {
        Journal depreciationUntilSale = journal(LocalDate.of(2026, 6, 30), JournalType.ADJUSTING,
                dr(StandardAccounts.DEPRECIATION_EXPENSE, 1_000),
                cr(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, 1_000));

        assertThat(PostedJournal.post(period, depreciationUntilSale)).isNotNull();
    }

    /** 復元は会計期間の状態を検証しない(締めた期の仕訳を読み出すため)。 */
    @Test
    void restore_doesNotCheckThePeriodStatus() {
        AccountingPeriod closed = period.close();
        Journal journal = saleOn(LocalDate.of(2026, 7, 1));

        PostedJournal restored = PostedJournal.restore(journal, closed.id(), 0L);

        assertThat(restored.journal()).isEqualTo(journal);
    }
}
