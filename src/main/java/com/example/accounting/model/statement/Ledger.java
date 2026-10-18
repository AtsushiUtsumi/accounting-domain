package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.Side;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.money.Money;

import java.util.List;

/**
 * 1つの勘定科目についての元帳。保存はせず、仕訳一覧から毎回導出する。
 */
public record Ledger(Account account, List<JournalLine> entries) {

    public Ledger {
        entries = List.copyOf(entries);
    }

    public static Ledger of(Account account, List<Journal> journals) {
        List<JournalLine> entries = journals.stream()
                .flatMap(journal -> journal.lines().stream())
                .filter(line -> line.account().equals(account))
                .toList();
        return new Ledger(account, entries);
    }

    public Money debitTotal() {
        return totalOf(Side.DEBIT);
    }

    public Money creditTotal() {
        return totalOf(Side.CREDIT);
    }

    /** 科目の正常な増加側を基準にした残高(正常な向きなら正、逆なら負)。 */
    public Money balance() {
        Money debit = debitTotal();
        Money credit = creditTotal();
        return switch (account.type().normalSide()) {
            case DEBIT -> debit.minus(credit);
            case CREDIT -> credit.minus(debit);
        };
    }

    private Money totalOf(Side side) {
        Money total = Money.ZERO;
        for (JournalLine entry : entries) {
            if (entry.side() == side) {
                total = total.plus(entry.amount());
            }
        }
        return total;
    }
}
