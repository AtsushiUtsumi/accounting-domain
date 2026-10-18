package com.example.accounting.model.period;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 月割の月数。期待値は問題集の解答例ではなく、一般的なテキストの「1か月未満は1か月とする」にもとづく(未検証)。
 */
@Tag("unverified")
class MonthCountTest {

    private final AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

    @Test
    void inclusive_countsBothEndMonths() {
        assertThat(MonthCount.inclusive(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 3, 31))).isEqualTo(6);
    }

    @Test
    void inclusive_countsAPartialMonthAsAFullMonth() {
        assertThat(MonthCount.inclusive(LocalDate.of(2026, 10, 15), LocalDate.of(2027, 3, 31))).isEqualTo(6);
    }

    @Test
    void withinPeriod_clipsToThePeriod() {
        assertThat(MonthCount.withinPeriod(period, LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 1))).isEqualTo(12);
        assertThat(MonthCount.withinPeriod(period, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30))).isEqualTo(3);
        assertThat(MonthCount.withinPeriod(period, LocalDate.of(2027, 4, 1), LocalDate.of(2027, 5, 1))).isZero();
    }
}
