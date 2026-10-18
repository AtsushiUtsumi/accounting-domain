package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.Side;
import com.example.accounting.model.money.Money;

/**
 * 仕訳の1行。財務諸表の導出を純粋計算にするため、{@link Account} そのものを保持する
 * (AccountCode ではない)。
 */
public record JournalLine(Account account, Side side, Money amount) {

    public JournalLine {
        if (account == null) {
            throw new AccountingException("account is required");
        }
        if (side == null) {
            throw new AccountingException("side is required");
        }
        if (amount == null) {
            throw new AccountingException("amount is required");
        }
        if (!amount.isPositive()) {
            throw new AccountingException("amount must be positive: " + amount.amountInYen());
        }
        if (!account.active()) {
            throw new AccountingException("inactive account cannot be used: " + account.code().value());
        }
    }

    public static JournalLine debit(Account account, Money amount) {
        return new JournalLine(account, Side.DEBIT, amount);
    }

    public static JournalLine credit(Account account, Money amount) {
        return new JournalLine(account, Side.CREDIT, amount);
    }
}
