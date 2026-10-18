package com.example.accounting.model.asset;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.money.RoundingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 生産高比例法。(取得価額-残存価額)×当期の利用量÷総利用可能量で償却する。
 * 期間ではなく利用量で決まるため、月割はしない。
 */
public final class UnitsOfProductionDepreciationPolicy implements UsageBasedDepreciationPolicy {

    private final long totalEstimatedUsage;
    private final RoundingPolicy roundingPolicy;

    private UnitsOfProductionDepreciationPolicy(long totalEstimatedUsage, RoundingPolicy roundingPolicy) {
        if (totalEstimatedUsage <= 0) {
            throw new AccountingException("totalEstimatedUsage must be positive");
        }
        this.totalEstimatedUsage = totalEstimatedUsage;
        this.roundingPolicy = roundingPolicy;
    }

    public static UnitsOfProductionDepreciationPolicy of(long totalEstimatedUsage) {
        return new UnitsOfProductionDepreciationPolicy(totalEstimatedUsage, RoundingPolicy.roundDown());
    }

    public static UnitsOfProductionDepreciationPolicy of(long totalEstimatedUsage, RoundingPolicy roundingPolicy) {
        return new UnitsOfProductionDepreciationPolicy(totalEstimatedUsage, roundingPolicy);
    }

    /** 利用量がわからないと計算できないため、常に例外。{@link #depreciationForUsage} を使うこと。 */
    @Override
    public Money depreciationFor(FixedAsset asset, Money accumulatedDepreciationBeforeThisPeriod) {
        throw new AccountingException("units-of-production method requires usage; use depreciationEntryByUsage");
    }

    @Override
    public Money depreciationForUsage(FixedAsset asset, long usageThisPeriod) {
        if (usageThisPeriod < 0) {
            throw new AccountingException("usageThisPeriod must not be negative");
        }
        BigDecimal raw = BigDecimal.valueOf(asset.depreciableAmount().amountInYen())
                .multiply(BigDecimal.valueOf(usageThisPeriod))
                .divide(BigDecimal.valueOf(totalEstimatedUsage), 10, RoundingMode.HALF_UP);
        return roundingPolicy.apply(raw);
    }
}
