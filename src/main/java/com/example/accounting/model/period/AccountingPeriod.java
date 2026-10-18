package com.example.accounting.model.period;

import com.example.accounting.exception.AccountingException;

import java.time.LocalDate;

/**
 * 会計期間。集約ルート。期間は1年固定(期首から1年後の前日を期末とする)。
 * version は楽観ロック用で、このライブラリは保持するだけで検証しない。
 */
public record AccountingPeriod(
        AccountingPeriodId id,
        LocalDate startDate,
        LocalDate endDate,
        PeriodStatus status,
        long version
) {

    public AccountingPeriod {
        if (id == null) {
            throw new AccountingException("id is required");
        }
        if (startDate == null) {
            throw new AccountingException("startDate is required");
        }
        if (endDate == null) {
            throw new AccountingException("endDate is required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new AccountingException("endDate must be after startDate");
        }
        if (status == null) {
            throw new AccountingException("status is required");
        }
    }

    public static AccountingPeriod startingAt(LocalDate startDate) {
        return new AccountingPeriod(
                AccountingPeriodId.newId(),
                startDate,
                startDate.plusYears(1).minusDays(1),
                PeriodStatus.OPEN,
                0L
        );
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean isOpen() {
        return status == PeriodStatus.OPEN;
    }

    public AccountingPeriod close() {
        if (status == PeriodStatus.CLOSED) {
            throw new AccountingException("period is already closed: " + id.value());
        }
        return new AccountingPeriod(id, startDate, endDate, PeriodStatus.CLOSED, version + 1);
    }

    /** この期間の翌日から始まる、次の1年間の期間を生成する。 */
    public AccountingPeriod nextPeriod() {
        return startingAt(endDate.plusDays(1));
    }
}
