package com.example.accounting.model.statement;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountType;
import com.example.accounting.model.account.Side;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.money.Money;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合計試算表。仕訳一覧から毎回導出する(保存しない)。
 */
public record TrialBalance(Map<Account, Row> rows) {

    public record Row(Money debitTotal, Money creditTotal) {

        /** 科目の正常な増加側を基準にした残高(正常な向きなら正、逆なら負)。 */
        public Money netBalance(AccountType type) {
            return switch (type.normalSide()) {
                case DEBIT -> debitTotal.minus(creditTotal);
                case CREDIT -> creditTotal.minus(debitTotal);
            };
        }
    }

    public TrialBalance {
        rows = Map.copyOf(rows);
    }

    public static TrialBalance of(List<Journal> journals) {
        Map<Account, Money> debitTotals = new LinkedHashMap<>();
        Map<Account, Money> creditTotals = new LinkedHashMap<>();

        for (Journal journal : journals) {
            for (JournalLine line : journal.lines()) {
                Map<Account, Money> target = line.side() == Side.DEBIT ? debitTotals : creditTotals;
                target.merge(line.account(), line.amount(), Money::plus);
            }
        }

        Map<Account, Row> rows = new LinkedHashMap<>();
        for (Account account : unionOfKeys(debitTotals, creditTotals)) {
            Money debit = debitTotals.getOrDefault(account, Money.ZERO);
            Money credit = creditTotals.getOrDefault(account, Money.ZERO);
            rows.put(account, new Row(debit, credit));
        }
        return new TrialBalance(rows);
    }

    private static List<Account> unionOfKeys(Map<Account, Money> a, Map<Account, Money> b) {
        Map<Account, Boolean> seen = new LinkedHashMap<>();
        a.keySet().forEach(k -> seen.put(k, true));
        b.keySet().forEach(k -> seen.put(k, true));
        return List.copyOf(seen.keySet());
    }

    public Money debitTotal() {
        Money total = Money.ZERO;
        for (Row row : rows.values()) {
            total = total.plus(row.debitTotal());
        }
        return total;
    }

    public Money creditTotal() {
        Money total = Money.ZERO;
        for (Row row : rows.values()) {
            total = total.plus(row.creditTotal());
        }
        return total;
    }

    public boolean isBalanced() {
        return debitTotal().equals(creditTotal());
    }

    public void requireBalanced() {
        if (!isBalanced()) {
            throw new AccountingException(
                    "trial balance is not balanced: debit=%d, credit=%d"
                            .formatted(debitTotal().amountInYen(), creditTotal().amountInYen()));
        }
    }
}
