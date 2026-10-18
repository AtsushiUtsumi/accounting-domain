package com.example.accounting.service;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialStatementFactoryTest {

    @Test
    void derivesAllThreeStatementsFromTheSameJournals() {
        List<Journal> journals = List.of(
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 1), "出資による開業",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                                JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000))
                        )
                )
        );

        FinancialStatementFactory factory = new FinancialStatementFactory();

        assertThat(factory.trialBalance(journals).isBalanced()).isTrue();
        assertThat(factory.balanceSheet(journals).isBalanced()).isTrue();
        assertThat(factory.incomeStatement(journals).netIncome()).isEqualTo(Money.ZERO);
    }
}
