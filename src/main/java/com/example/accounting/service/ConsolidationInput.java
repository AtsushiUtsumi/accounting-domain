package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.consolidation.IndividualStatements;
import com.example.accounting.model.consolidation.IntercompanyBalance;
import com.example.accounting.model.consolidation.IntercompanyTransaction;
import com.example.accounting.model.consolidation.Subsidiary;
import com.example.accounting.model.consolidation.UnrealizedProfit;

import java.time.LocalDate;
import java.util.List;

/**
 * 連結の入力。連結決算日、親会社と子会社の個別財務諸表、子会社の支配獲得時の情報、連結会社間の取引の資料。
 *
 * @param fiscalYearEnd 連結決算日(親会社・子会社の決算日は同じとする。会計期間は1年)
 */
public record ConsolidationInput(
        LocalDate fiscalYearEnd,
        IndividualStatements parent,
        List<Subsidiary> subsidiaries,
        List<IndividualStatements> subsidiaryStatements,
        List<IntercompanyBalance> intercompanyBalances,
        List<IntercompanyTransaction> intercompanyTransactions,
        List<UnrealizedProfit> unrealizedProfits
) {

    public ConsolidationInput {
        if (fiscalYearEnd == null || parent == null) {
            throw new AccountingException("fiscalYearEnd and parent are required");
        }
        subsidiaries = List.copyOf(subsidiaries);
        subsidiaryStatements = List.copyOf(subsidiaryStatements);
        intercompanyBalances = List.copyOf(intercompanyBalances);
        intercompanyTransactions = List.copyOf(intercompanyTransactions);
        unrealizedProfits = List.copyOf(unrealizedProfits);
    }

    /** 連結会社間の取引がない(資本連結だけの)入力。 */
    public static ConsolidationInput of(LocalDate fiscalYearEnd, IndividualStatements parent,
                                        List<Subsidiary> subsidiaries, List<IndividualStatements> subsidiaryStatements) {
        return new ConsolidationInput(fiscalYearEnd, parent, subsidiaries, subsidiaryStatements,
                List.of(), List.of(), List.of());
    }

    public ConsolidationInput withIntercompanyBalances(List<IntercompanyBalance> balances) {
        return new ConsolidationInput(fiscalYearEnd, parent, subsidiaries, subsidiaryStatements,
                balances, intercompanyTransactions, unrealizedProfits);
    }

    public ConsolidationInput withIntercompanyTransactions(List<IntercompanyTransaction> transactions) {
        return new ConsolidationInput(fiscalYearEnd, parent, subsidiaries, subsidiaryStatements,
                intercompanyBalances, transactions, unrealizedProfits);
    }

    public ConsolidationInput withUnrealizedProfits(List<UnrealizedProfit> profits) {
        return new ConsolidationInput(fiscalYearEnd, parent, subsidiaries, subsidiaryStatements,
                intercompanyBalances, intercompanyTransactions, profits);
    }
}
