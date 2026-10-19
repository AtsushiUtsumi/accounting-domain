package com.example.accounting.service;

import com.example.accounting.model.consolidation.ConsolidatedBalanceSheet;
import com.example.accounting.model.consolidation.ConsolidatedIncomeStatement;
import com.example.accounting.model.consolidation.ConsolidationWorksheet;
import com.example.accounting.model.journal.Journal;

import java.util.List;

/**
 * 連結の結果。連結修正仕訳と、それを反映した連結精算表・連結損益計算書・連結貸借対照表。
 * 連結修正仕訳は個別の帳簿に記帳しないので、保存する必要はない(毎期、個別財務諸表から作り直す)。
 */
public record ConsolidationResult(
        List<Journal> adjustments,
        ConsolidationWorksheet worksheet,
        ConsolidatedIncomeStatement incomeStatement,
        ConsolidatedBalanceSheet balanceSheet
) {

    public ConsolidationResult {
        adjustments = List.copyOf(adjustments);
    }
}
