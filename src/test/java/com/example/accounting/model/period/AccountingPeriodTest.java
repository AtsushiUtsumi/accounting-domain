package com.example.accounting.model.period;

import com.example.accounting.exception.AccountingException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountingPeriodTest {

    @Test
    void startingAt_setsOneYearPeriod() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(period.endDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(period.isOpen()).isTrue();
    }

    @Test
    void closingTwice_isRejected() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1)).close();

        assertThatThrownBy(period::close).isInstanceOf(AccountingException.class);
    }

    @Test
    void nextPeriod_startsTheDayAfterEndDate() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

        AccountingPeriod next = period.nextPeriod();

        assertThat(next.startDate()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(next.endDate()).isEqualTo(LocalDate.of(2028, 3, 31));
    }
}
