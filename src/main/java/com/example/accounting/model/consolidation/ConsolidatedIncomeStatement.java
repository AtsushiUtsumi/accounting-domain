package com.example.accounting.model.consolidation;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.IncomeStatement;

import java.util.EnumMap;
import java.util.Map;

/**
 * 連結損益計算書。個別の損益計算書と同じ5段階利益で当期純利益まで出し、そこから
 * 非支配株主に帰属する当期純利益を差し引いて、親会社株主に帰属する当期純利益を出す。
 * 連結精算表から毎回導出する(保存しない)。
 */
public record ConsolidatedIncomeStatement(
        IncomeStatement incomeStatement,
        Money profitAttributableToNonControllingInterests
) {

    public static ConsolidatedIncomeStatement of(ConsolidationWorksheet worksheet) {
        Map<AccountCategory, Money> totals = new EnumMap<>(AccountCategory.class);
        for (Map.Entry<Account, ConsolidationWorksheet.Row> entry : worksheet.rows().entrySet()) {
            totals.merge(entry.getKey().category(), entry.getValue().consolidated(), Money::plus);
        }
        IncomeStatement incomeStatement = new IncomeStatement(
                totals.getOrDefault(AccountCategory.SALES, Money.ZERO),
                totals.getOrDefault(AccountCategory.COST_OF_SALES, Money.ZERO),
                totals.getOrDefault(AccountCategory.SG_AND_A, Money.ZERO),
                totals.getOrDefault(AccountCategory.NON_OPERATING_INCOME, Money.ZERO),
                totals.getOrDefault(AccountCategory.NON_OPERATING_EXPENSE, Money.ZERO),
                totals.getOrDefault(AccountCategory.EXTRAORDINARY_INCOME, Money.ZERO),
                totals.getOrDefault(AccountCategory.EXTRAORDINARY_LOSS, Money.ZERO),
                totals.getOrDefault(AccountCategory.INCOME_TAX_EXPENSE, Money.ZERO));
        return new ConsolidatedIncomeStatement(
                incomeStatement,
                totals.getOrDefault(AccountCategory.NON_CONTROLLING_INTERESTS_PROFIT, Money.ZERO));
    }

    /** 当期純利益(非支配株主に帰属する分を含む)。 */
    public Money netIncome() {
        return incomeStatement.netIncome();
    }

    /** 親会社株主に帰属する当期純利益。 */
    public Money profitAttributableToOwnersOfParent() {
        return netIncome().minus(profitAttributableToNonControllingInterests);
    }
}
