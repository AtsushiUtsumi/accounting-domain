package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.money.Money;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 貸借対照表。仕訳一覧から毎回導出する(保存しない)。
 *
 * <p>収益・費用の損益振替(決算整理後の損益振替仕訳)が既に行われていることを前提とする。
 * 振替前の仕訳一覧を渡すと、収益・費用が純資産に反映されないため資産=負債+純資産は成立しない。
 */
public record BalanceSheet(Map<Account, Money> assets, Map<Account, Money> liabilities, Map<Account, Money> equity) {

    public BalanceSheet {
        assets = Map.copyOf(assets);
        liabilities = Map.copyOf(liabilities);
        equity = Map.copyOf(equity);
    }

    public static BalanceSheet of(List<Journal> journals) {
        TrialBalance trialBalance = TrialBalance.of(journals);

        Map<Account, Money> assets = new LinkedHashMap<>();
        Map<Account, Money> liabilities = new LinkedHashMap<>();
        Map<Account, Money> equity = new LinkedHashMap<>();

        for (Map.Entry<Account, TrialBalance.Row> entry : trialBalance.rows().entrySet()) {
            Account account = entry.getKey();
            Money balance = entry.getValue().netBalance(account.type());

            switch (account.category()) {
                case CURRENT_ASSET, FIXED_ASSET -> assets.put(account, balance);
                case CURRENT_LIABILITY, FIXED_LIABILITY -> liabilities.put(account, balance);
                case CAPITAL, RETAINED_EARNINGS, VALUATION_AND_TRANSLATION_ADJUSTMENTS -> equity.put(account, balance);
                default -> {
                    // P/L の各区分と PROFIT_AND_LOSS(損益勘定)は B/S には表示しない。
                }
            }
        }

        return new BalanceSheet(assets, liabilities, equity);
    }

    public Money totalAssets() {
        return sum(assets.values());
    }

    public Money totalLiabilities() {
        return sum(liabilities.values());
    }

    public Money totalEquity() {
        return sum(equity.values());
    }

    public boolean isBalanced() {
        return totalAssets().equals(totalLiabilities().plus(totalEquity()));
    }

    private static Money sum(Iterable<Money> amounts) {
        Money total = Money.ZERO;
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }
}
