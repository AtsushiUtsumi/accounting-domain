package com.example.accounting.model.asset;

import com.example.accounting.model.money.Money;
import com.example.accounting.model.money.RoundingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 定額法。毎期(取得価額-残存価額)÷耐用年数で一定額を償却する。 */
public final class StraightLineDepreciationPolicy implements DepreciationPolicy {

    private final RoundingPolicy roundingPolicy;

    private StraightLineDepreciationPolicy(RoundingPolicy roundingPolicy) {
        this.roundingPolicy = roundingPolicy;
    }

    public static StraightLineDepreciationPolicy of() {
        return new StraightLineDepreciationPolicy(RoundingPolicy.roundDown());
    }

    public static StraightLineDepreciationPolicy of(RoundingPolicy roundingPolicy) {
        return new StraightLineDepreciationPolicy(roundingPolicy);
    }

    @Override
    public Money depreciationFor(FixedAsset asset, Money accumulatedDepreciationBeforeThisPeriod) {
        BigDecimal depreciableAmount = BigDecimal.valueOf(asset.depreciableAmount().amountInYen());
        BigDecimal perYear = depreciableAmount.divide(
                BigDecimal.valueOf(asset.usefulLifeYears()), 10, RoundingMode.HALF_UP);
        return roundingPolicy.apply(perYear);
    }
}
