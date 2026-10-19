package com.example.accounting.model.consolidation;

import java.util.Objects;
import java.util.UUID;

/** 会社の識別子。連結で親会社・子会社を区別するのに使う。DB の採番に依存しない。 */
public record CompanyId(UUID value) {

    public CompanyId {
        Objects.requireNonNull(value, "value is required");
    }

    public static CompanyId newId() {
        return new CompanyId(UUID.randomUUID());
    }

    public static CompanyId of(String value) {
        return new CompanyId(UUID.fromString(value));
    }
}
