package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;

/**
 * 勘定科目をキャッシュ・フロー計算書の活動区分に割り当てる方法。会社ごとに扱いが異なり得るため
 * 差し替え可能にする。
 */
public interface CashFlowClassifier {
    CashFlowActivity classify(Account account);
}
