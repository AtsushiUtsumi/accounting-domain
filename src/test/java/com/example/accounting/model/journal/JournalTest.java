package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalTest {

    @Test
    void balancedJournal_canBeCreated() {
        Journal journal = Journal.of(
                JournalId.newId(),
                LocalDate.of(2026, 4, 1),
                "現金600,000円を出資して開業した",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                        JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000))
                )
        );

        assertThat(journal.debitTotal()).isEqualTo(Money.ofYen(600_000));
        assertThat(journal.creditTotal()).isEqualTo(Money.ofYen(600_000));
    }

    @Test
    void unbalancedJournal_isRejected() {
        assertThatThrownBy(() -> Journal.of(
                JournalId.newId(),
                LocalDate.of(2026, 4, 1),
                "不正な仕訳",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                        JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(500_000))
                )
        )).isInstanceOf(AccountingException.class);
    }

    @Test
    void singleLineJournal_isRejected() {
        assertThatThrownBy(() -> Journal.of(
                JournalId.newId(),
                LocalDate.of(2026, 4, 1),
                "不正な仕訳",
                JournalType.NORMAL,
                List.of(JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)))
        )).isInstanceOf(AccountingException.class);
    }
}
