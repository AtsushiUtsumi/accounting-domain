package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.Ledger;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotesReceivableServiceTest {

    private final NotesReceivableService service = new NotesReceivableService();

    /**
     * 得意先の売掛金100,000円を約束手形で受け取り、その手形を銀行で割引に付し、
     * 割引料2,000円を差し引かれた残額98,000円を現金で受け取る。
     */
    @Test
    void discount_deductsTheFeeFromProceedsAndRemovesTheFullFaceValue() {
        Journal receiveNote = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 6, 1), "売掛金を約束手形で回収",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.NOTES_RECEIVABLE, Money.ofYen(100_000)),
                        JournalLine.credit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(100_000))
                )
        );

        Journal discounting = service.discount(
                StandardAccounts.CASH, Money.ofYen(100_000), Money.ofYen(2_000), LocalDate.of(2026, 6, 10));

        assertThat(discounting.debitTotal()).isEqualTo(Money.ofYen(100_000));
        assertThat(discounting.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.CASH);
            assertThat(line.amount()).isEqualTo(Money.ofYen(98_000));
        });
        assertThat(discounting.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.LOSS_ON_NOTE_DISCOUNTING);
            assertThat(line.amount()).isEqualTo(Money.ofYen(2_000));
        });

        List<Journal> all = List.of(receiveNote, discounting);
        assertThat(Ledger.of(StandardAccounts.NOTES_RECEIVABLE, all).balance()).isEqualTo(Money.ZERO);
    }

    @Test
    void discount_feeEqualToFaceValue_isRejected() {
        assertThatThrownBy(() -> service.discount(
                StandardAccounts.CASH, Money.ofYen(100_000), Money.ofYen(100_000), LocalDate.of(2026, 6, 10)))
                .isInstanceOf(AccountingException.class);
    }

    @Test
    void discount_negativeFee_isRejected() {
        assertThatThrownBy(() -> service.discount(
                StandardAccounts.CASH, Money.ofYen(100_000), Money.ofYen(-1), LocalDate.of(2026, 6, 10)))
                .isInstanceOf(AccountingException.class);
    }

    /**
     * 仕入先の買掛金50,000円を約束手形を振り出して支払い、期日に当座預金(ここでは現金で代用)から支払う。
     * 手形の振出・決済自体は計算を伴わないため、通常の Journal/JournalLine で組む。
     */
    @Test
    void notesPayable_issuedAndSettledWithPlainJournalEntries() {
        Journal issueNote = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 7, 1), "買掛金を約束手形で支払",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(50_000)),
                        JournalLine.credit(StandardAccounts.NOTES_PAYABLE, Money.ofYen(50_000))
                )
        );
        Journal settleNote = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 8, 31), "支払手形の期日決済",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.NOTES_PAYABLE, Money.ofYen(50_000)),
                        JournalLine.credit(StandardAccounts.CASH, Money.ofYen(50_000))
                )
        );

        List<Journal> all = List.of(issueNote, settleNote);

        assertThat(Ledger.of(StandardAccounts.NOTES_PAYABLE, all).balance()).isEqualTo(Money.ZERO);
        assertThat(Ledger.of(StandardAccounts.ACCOUNTS_PAYABLE, all).balance()).isEqualTo(Money.ofYen(-50_000));
        assertThat(Ledger.of(StandardAccounts.CASH, all).balance()).isEqualTo(Money.ofYen(-50_000));
    }
}
