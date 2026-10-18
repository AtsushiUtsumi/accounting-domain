package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.money.Money;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 損益計算書。仕訳一覧から毎回導出する(保存しない)。
 * 売上高 → 売上総利益 → 営業利益 → 経常利益 → 税引前当期純利益 → 当期純利益 の5段階。
 */
public record IncomeStatement(
        Money sales,
        Money costOfSales,
        Money sgAndA,
        Money nonOperatingIncome,
        Money nonOperatingExpense,
        Money extraordinaryIncome,
        Money extraordinaryLoss,
        Money incomeTaxExpense
) {

    public static IncomeStatement of(List<Journal> journals) {
        TrialBalance trialBalance = TrialBalance.of(journals);

        Map<AccountCategory, Money> totals = new EnumMap<>(AccountCategory.class);
        for (Map.Entry<Account, TrialBalance.Row> entry : trialBalance.rows().entrySet()) {
            Account account = entry.getKey();
            Money balance = entry.getValue().netBalance(account.type());
            totals.merge(account.category(), balance, Money::plus);
        }

        return new IncomeStatement(
                totals.getOrDefault(AccountCategory.SALES, Money.ZERO),
                totals.getOrDefault(AccountCategory.COST_OF_SALES, Money.ZERO),
                totals.getOrDefault(AccountCategory.SG_AND_A, Money.ZERO),
                totals.getOrDefault(AccountCategory.NON_OPERATING_INCOME, Money.ZERO),
                totals.getOrDefault(AccountCategory.NON_OPERATING_EXPENSE, Money.ZERO),
                totals.getOrDefault(AccountCategory.EXTRAORDINARY_INCOME, Money.ZERO),
                totals.getOrDefault(AccountCategory.EXTRAORDINARY_LOSS, Money.ZERO),
                totals.getOrDefault(AccountCategory.INCOME_TAX_EXPENSE, Money.ZERO)
        );
    }

    public Money grossProfit() {
        return sales.minus(costOfSales);
    }

    public Money operatingIncome() {
        return grossProfit().minus(sgAndA);
    }

    public Money ordinaryIncome() {
        return operatingIncome().plus(nonOperatingIncome).minus(nonOperatingExpense);
    }

    public Money incomeBeforeIncomeTax() {
        return ordinaryIncome().plus(extraordinaryIncome).minus(extraordinaryLoss);
    }

    public Money netIncome() {
        return incomeBeforeIncomeTax().minus(incomeTaxExpense);
    }
}
