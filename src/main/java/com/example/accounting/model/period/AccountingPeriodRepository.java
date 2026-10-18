package com.example.accounting.model.period;


import java.util.Optional;

/**
 * 会計期間の保存先。{@link AccountingPeriod#version()} を使った楽観ロックの検証は実装側の責務
 * (ライブラリは version を保持するだけ)。
 */
public interface AccountingPeriodRepository {

    Optional<AccountingPeriod> findById(AccountingPeriodId id);

    void save(AccountingPeriod period);
}
