package com.example.accounting.model.consolidation;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.money.Money;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 連結貸借対照表。連結精算表から毎回導出する(保存しない)。
 *
 * <p>連結精算表は損益振替の前の残高なので、利益剰余金は「精算表の利益剰余金 + 親会社株主に帰属する当期純利益」
 * で求める({@link #retainedEarnings()})。非支配株主持分は株主資本とは別に表示する。
 *
 * @param shareholdersEquity 株主資本と評価・換算差額等の科目(利益剰余金には当期純利益を含まない)
 */
public record ConsolidatedBalanceSheet(
        Map<Account, Money> assets,
        Map<Account, Money> liabilities,
        Map<Account, Money> shareholdersEquity,
        Money profitAttributableToOwnersOfParent,
        Money nonControllingInterests
) {

    public ConsolidatedBalanceSheet {
        assets = Map.copyOf(assets);
        liabilities = Map.copyOf(liabilities);
        shareholdersEquity = Map.copyOf(shareholdersEquity);
    }

    public static ConsolidatedBalanceSheet of(ConsolidationWorksheet worksheet, ConsolidatedIncomeStatement incomeStatement) {
        Map<Account, Money> assets = new LinkedHashMap<>();
        Map<Account, Money> liabilities = new LinkedHashMap<>();
        Map<Account, Money> equity = new LinkedHashMap<>();
        Money nonControllingInterests = Money.ZERO;

        for (Map.Entry<Account, ConsolidationWorksheet.Row> entry : worksheet.rows().entrySet()) {
            Account account = entry.getKey();
            Money balance = entry.getValue().consolidated();
            switch (account.category()) {
                case CURRENT_ASSET, FIXED_ASSET -> putIfNonZero(assets, account, balance);
                case CURRENT_LIABILITY, FIXED_LIABILITY -> putIfNonZero(liabilities, account, balance);
                case CAPITAL, RETAINED_EARNINGS, VALUATION_AND_TRANSLATION_ADJUSTMENTS ->
                        putIfNonZero(equity, account, balance);
                case NON_CONTROLLING_INTERESTS -> nonControllingInterests = nonControllingInterests.plus(balance);
                default -> {
                    // 損益計算書の科目は連結損益計算書へ。
                }
            }
        }
        return new ConsolidatedBalanceSheet(assets, liabilities, equity,
                incomeStatement.profitAttributableToOwnersOfParent(), nonControllingInterests);
    }

    public Money totalAssets() {
        return sum(assets);
    }

    public Money totalLiabilities() {
        return sum(liabilities);
    }

    /** 利益剰余金(利益準備金・任意積立金を含む)。親会社株主に帰属する当期純利益を含む。 */
    public Money retainedEarnings() {
        Money total = profitAttributableToOwnersOfParent;
        for (Map.Entry<Account, Money> entry : shareholdersEquity.entrySet()) {
            if (entry.getKey().category() == AccountCategory.RETAINED_EARNINGS) {
                total = total.plus(entry.getValue());
            }
        }
        return total;
    }

    /** 純資産合計(株主資本 + 評価・換算差額等 + 非支配株主持分)。 */
    public Money totalEquity() {
        return sum(shareholdersEquity).plus(profitAttributableToOwnersOfParent).plus(nonControllingInterests);
    }

    public boolean isBalanced() {
        return totalAssets().equals(totalLiabilities().plus(totalEquity()));
    }

    private static void putIfNonZero(Map<Account, Money> target, Account account, Money balance) {
        if (!balance.isZero()) {
            target.put(account, balance);
        }
    }

    private static Money sum(Map<Account, Money> amounts) {
        Money total = Money.ZERO;
        for (Money amount : amounts.values()) {
            total = total.plus(amount);
        }
        return total;
    }
}
