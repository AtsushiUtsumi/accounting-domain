package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 剰余金の配当。利益準備金の積立額 = min(配当額×1/10, 資本金×1/4-(資本準備金+利益準備金))。
 */
class DividendServiceTest {

    private final DividendService service = new DividendService();

    @Test
    void declareDividends_appropriatesOneTenthWhenWellBelowTheCeiling() {
        Journal journal = service.declareDividends(
                Money.ofYen(200_000),
                Money.ofYen(1_500_000),
                Money.ofYen(100_000),
                Money.ofYen(50_000),
                LocalDate.of(2026, 6, 1));

        assertThat(journal.debitTotal()).isEqualTo(Money.ofYen(220_000));
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.RETAINED_EARNINGS);
            assertThat(line.amount()).isEqualTo(Money.ofYen(220_000));
        });
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.DIVIDENDS_PAYABLE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(200_000));
        });
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.STATUTORY_RESERVE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(20_000));
        });
    }

    @Test
    void declareDividends_capsAppropriationAtTheRemainingRoomUnderTheCeiling() {
        Journal journal = service.declareDividends(
                Money.ofYen(200_000),
                Money.ofYen(1_000_000),
                Money.ofYen(200_000),
                Money.ofYen(40_000),
                LocalDate.of(2026, 6, 1));

        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.STATUTORY_RESERVE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(10_000));
        });
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.RETAINED_EARNINGS);
            assertThat(line.amount()).isEqualTo(Money.ofYen(210_000));
        });
    }

    @Test
    void declareDividends_appropriatesNothingWhenAlreadyAtTheCeiling() {
        Journal journal = service.declareDividends(
                Money.ofYen(200_000),
                Money.ofYen(400_000),
                Money.ofYen(60_000),
                Money.ofYen(50_000),
                LocalDate.of(2026, 6, 1));

        assertThat(journal.lines()).noneSatisfy(line ->
                assertThat(line.account()).isEqualTo(StandardAccounts.STATUTORY_RESERVE));
        assertThat(journal.debitTotal()).isEqualTo(Money.ofYen(200_000));
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.DIVIDENDS_PAYABLE);
            assertThat(line.amount()).isEqualTo(Money.ofYen(200_000));
        });
    }

    @Test
    void declareDividends_nonPositiveAmount_isRejected() {
        assertThatThrownBy(() -> service.declareDividends(
                Money.ZERO, Money.ofYen(1_000_000), Money.ofYen(100_000), Money.ofYen(50_000),
                LocalDate.of(2026, 6, 1)))
                .isInstanceOf(AccountingException.class);
    }
}
