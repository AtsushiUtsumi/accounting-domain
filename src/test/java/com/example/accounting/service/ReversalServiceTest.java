package com.example.accounting.service;

import com.example.accounting.model.account.Side;
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

class ReversalServiceTest {

    @Test
    void reverse_flipsDebitAndCreditOfEachLine() {
        Journal original = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 4, 10), "商品の掛け仕入(金額を誤入力)",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(300_000)),
                        JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(300_000))
                )
        );

        Journal reversal = new ReversalService().reverse(JournalId.newId(), LocalDate.of(2026, 4, 11), original);

        assertThat(reversal.type()).isEqualTo(JournalType.REVERSAL);
        assertThat(reversal.reversalOf()).isEqualTo(original.id());
        assertThat(reversal.lines()).extracting(JournalLine::side)
                .containsExactly(Side.CREDIT, Side.DEBIT);
        assertThat(reversal.debitTotal()).isEqualTo(original.creditTotal());
        assertThat(reversal.creditTotal()).isEqualTo(original.debitTotal());

        // 元の仕訳と取消仕訳を合算すると、各科目の増減は完全に打ち消される。
        List<Journal> combined = List.of(original, reversal);
        assertThat(com.example.accounting.model.statement.Ledger.of(StandardAccounts.PURCHASES, combined).balance())
                .isEqualTo(Money.ZERO);
        assertThat(com.example.accounting.model.statement.Ledger.of(StandardAccounts.ACCOUNTS_PAYABLE, combined).balance())
                .isEqualTo(Money.ZERO);
    }
}
