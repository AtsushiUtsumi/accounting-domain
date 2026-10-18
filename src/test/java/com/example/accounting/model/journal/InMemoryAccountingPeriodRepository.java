package com.example.accounting.model.journal;

import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.period.AccountingPeriodId;
import com.example.accounting.model.period.AccountingPeriodRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** テスト用のインメモリ実装。最後に保存した状態(version を含む)を持つだけ。 */
final class InMemoryAccountingPeriodRepository implements AccountingPeriodRepository {

    private final Map<AccountingPeriodId, AccountingPeriod> periods = new HashMap<>();

    @Override
    public Optional<AccountingPeriod> findById(AccountingPeriodId id) {
        return Optional.ofNullable(periods.get(id));
    }

    @Override
    public void save(AccountingPeriod period) {
        periods.put(period.id(), period);
    }
}
