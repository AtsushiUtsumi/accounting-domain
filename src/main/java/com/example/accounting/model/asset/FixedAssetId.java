package com.example.accounting.model.asset;

import java.util.Objects;
import java.util.UUID;

/** 固定資産の識別子。 */
public record FixedAssetId(UUID value) {

    public FixedAssetId {
        Objects.requireNonNull(value, "value is required");
    }

    public static FixedAssetId newId() {
        return new FixedAssetId(UUID.randomUUID());
    }

    public static FixedAssetId of(String value) {
        return new FixedAssetId(UUID.fromString(value));
    }
}
