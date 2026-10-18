package com.example.accounting.model.journal;

import java.util.Objects;
import java.util.UUID;

/** 仕訳の識別子。DB の採番に依存しない。 */
public record JournalId(UUID value) {

    public JournalId {
        Objects.requireNonNull(value, "value is required");
    }

    public static JournalId newId() {
        return new JournalId(UUID.randomUUID());
    }

    public static JournalId of(String value) {
        return new JournalId(UUID.fromString(value));
    }
}
