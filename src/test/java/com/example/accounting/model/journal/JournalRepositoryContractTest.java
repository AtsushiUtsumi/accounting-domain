package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.service.ClosingResult;
import com.example.accounting.service.ClosingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.normal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * リポジトリの契約(追記のみ・saveAll は全件か0件か・期間での検索・会計期間の version の検証)を、
 * インメモリ実装で確かめる。
 */
class JournalRepositoryContractTest {

    private final InMemoryAccountingPeriodRepository periods = new InMemoryAccountingPeriodRepository();
    private final JournalRepository repository = new InMemoryJournalRepository(periods);
    private final AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

    {
        periods.save(period);
    }

    private PostedJournal posted(LocalDate date, long amount) {
        return PostedJournal.post(period, normal(date,
                dr(StandardAccounts.CASH, amount), cr(StandardAccounts.SALES, amount)));
    }

    @Test
    void findByPeriod_returnsOnlyJournalsPostedToThePeriod() {
        PostedJournal inside = posted(LocalDate.of(2027, 3, 31), 1_000);
        AccountingPeriod next = period.nextPeriod();
        periods.save(next);
        PostedJournal other = PostedJournal.post(next, normal(LocalDate.of(2027, 4, 1),
                dr(StandardAccounts.CASH, 2_000), cr(StandardAccounts.SALES, 2_000)));
        repository.saveAll(List.of(inside, other));

        assertThat(repository.findByPeriod(period)).containsExactly(inside);
    }

    @Test
    void saveAll_savesNothingWhenAnyJournalIsRejected() {
        PostedJournal existing = posted(LocalDate.of(2026, 5, 1), 1_000);
        repository.save(existing);
        PostedJournal fresh = posted(LocalDate.of(2026, 5, 2), 3_000);

        assertThatThrownBy(() -> repository.saveAll(List.of(fresh, existing)))
                .isInstanceOf(AccountingException.class);
        assertThat(repository.findById(fresh.id())).isEmpty();
    }

    /** 記帳した後に期間が締められたら、その記帳は保存できない(記帳と締めの競合)。 */
    @Test
    void saveAll_rejectsAJournalPostedBeforeThePeriodWasClosed() {
        PostedJournal postedWhileOpen = posted(LocalDate.of(2026, 12, 1), 5_000);
        periods.save(period.close());

        assertThatThrownBy(() -> repository.save(postedWhileOpen))
                .isInstanceOf(AccountingException.class)
                .hasMessageContaining("closed");
        assertThat(repository.findById(postedWhileOpen.id())).isEmpty();
    }

    /**
     * 締めの結果は、ClosingResult の Javadoc の順序(損益振替 → 当期 → 次期 → 開始仕訳)で保存できる。
     * 締めた期の仕訳も、その後に読み出せる。
     */
    @Test
    void closingResult_canBeSavedInTheDocumentedOrder() {
        PostedJournal sale = posted(LocalDate.of(2026, 6, 1), 10_000);
        repository.save(sale);

        ClosingResult result = new ClosingService().close(period, repository.findByPeriod(period));
        repository.saveAll(result.closingEntries());
        periods.save(result.closedPeriod());
        periods.save(result.nextPeriod());
        repository.saveAll(result.openingEntries());

        assertThat(repository.findByPeriod(period)).hasSize(1 + result.closingEntries().size());
        assertThat(repository.findByPeriod(result.nextPeriod())).containsExactlyElementsOf(result.openingEntries());
    }

    @Test
    void closingResult_cannotBeSavedAfterThePeriodIsAlreadyStoredAsClosed() {
        repository.save(posted(LocalDate.of(2026, 6, 1), 10_000));
        ClosingResult result = new ClosingService().close(period, repository.findByPeriod(period));
        periods.save(result.closedPeriod());

        assertThatThrownBy(() -> repository.saveAll(result.closingEntries()))
                .isInstanceOf(AccountingException.class);
    }
}
