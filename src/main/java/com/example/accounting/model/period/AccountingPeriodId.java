package com.example.accounting.model.period;

import java.util.Objects;
import java.util.UUID;

/** 会計期間の識別子。 */
public record AccountingPeriodId(UUID value) {

    public AccountingPeriodId {
        Objects.requireNonNull(value, "value is required");
    }

    public static AccountingPeriodId newId() {
        return new AccountingPeriodId(UUID.randomUUID());
    }

    public static AccountingPeriodId of(String value) {
        return new AccountingPeriodId(UUID.fromString(value));
    }
}
