package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.period.AccountingPeriodRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * テスト用のインメモリ実装。saveAll は全件を検証してから保存する(全件か0件か)。
 * 検証は、追記のみ(同じ ID を二度保存しない)と、会計期間の version の一致。
 */
final class InMemoryJournalRepository implements JournalRepository {

    private final Map<JournalId, PostedJournal> journals = new LinkedHashMap<>();
    private final AccountingPeriodRepository periods;

    InMemoryJournalRepository(AccountingPeriodRepository periods) {
        this.periods = periods;
    }

    @Override
    public void save(PostedJournal journal) {
        saveAll(List.of(journal));
    }

    @Override
    public void saveAll(List<PostedJournal> newJournals) {
        for (PostedJournal journal : newJournals) {
            if (journals.containsKey(journal.id())) {
                throw new AccountingException("journal already exists (append-only): " + journal.id().value());
            }
            AccountingPeriod stored = periods.findById(journal.periodId())
                    .orElseThrow(() -> new AccountingException("unknown period: " + journal.periodId().value()));
            if (stored.version() != journal.periodVersion()) {
                throw new AccountingException("period was changed after posting (closed?): " + stored.id().value());
            }
        }
        for (PostedJournal journal : newJournals) {
            journals.put(journal.id(), journal);
        }
    }

    @Override
    public Optional<PostedJournal> findById(JournalId id) {
        return Optional.ofNullable(journals.get(id));
    }

    @Override
    public List<PostedJournal> findByPeriod(AccountingPeriod period) {
        List<PostedJournal> result = new ArrayList<>();
        for (PostedJournal journal : journals.values()) {
            if (journal.periodId().equals(period.id())) {
                result.add(journal);
            }
        }
        return result;
    }
}
