package com.example.accounting.support;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.List;

/** テスト用: 仕訳を短く書くための補助。 */
public final class Journals {

    private Journals() {
    }

    public static Journal journal(LocalDate date, JournalType type, JournalLine... lines) {
        return Journal.of(JournalId.newId(), date, "テスト用の仕訳", type, List.of(lines));
    }

    public static Journal normal(LocalDate date, JournalLine... lines) {
        return journal(date, JournalType.NORMAL, lines);
    }

    public static JournalLine dr(Account account, long yen) {
        return JournalLine.debit(account, Money.ofYen(yen));
    }

    public static JournalLine cr(Account account, long yen) {
        return JournalLine.credit(account, Money.ofYen(yen));
    }

    public static Money yen(long amount) {
        return Money.ofYen(amount);
    }
}
