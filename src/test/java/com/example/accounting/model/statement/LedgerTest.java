package com.example.accounting.model.statement;

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

class LedgerTest {

    @Test
    void balance_isComputedOnTheNormalSide() {
        List<Journal> journals = List.of(
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 1), "出資による開業",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                                JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 20), "買掛金の現金支払相当の現金減少",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(150_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(150_000))
                        )
                )
        );

        Ledger cashLedger = Ledger.of(StandardAccounts.CASH, journals);

        assertThat(cashLedger.debitTotal()).isEqualTo(Money.ofYen(600_000));
        assertThat(cashLedger.creditTotal()).isEqualTo(Money.ofYen(150_000));
        assertThat(cashLedger.balance()).isEqualTo(Money.ofYen(450_000));
    }
}
