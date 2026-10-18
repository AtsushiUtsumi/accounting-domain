package com.example.accounting.service;

import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.statement.BalanceSheet;
import com.example.accounting.model.statement.IncomeStatement;
import com.example.accounting.model.statement.TrialBalance;

import java.util.List;

/** 仕訳一覧から三表を導出する入口。保存は行わない。 */
public final class FinancialStatementFactory {

    public TrialBalance trialBalance(List<Journal> journals) {
        return TrialBalance.of(journals);
    }

    public BalanceSheet balanceSheet(List<Journal> journals) {
        return BalanceSheet.of(journals);
    }

    public IncomeStatement incomeStatement(List<Journal> journals) {
        return IncomeStatement.of(journals);
    }
}
