package com.example.accounting.model.asset;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.money.RoundingPolicy;

import java.math.BigDecimal;

/**
 * 定率法。(取得価額-その時点の累計償却額)×償却率で、年々減少する額を償却する。
 * 償却率は呼び出し側が与える(保証率・改定償却率など税法上の複雑な調整は対象外)。
 */
public final class DecliningBalanceDepreciationPolicy implements DepreciationPolicy {

    private final BigDecimal rate;
    private final RoundingPolicy roundingPolicy;

    private DecliningBalanceDepreciationPolicy(BigDecimal rate, RoundingPolicy roundingPolicy) {
        if (rate == null || rate.signum() <= 0) {
            throw new AccountingException("rate must be positive");
        }
        this.rate = rate;
        this.roundingPolicy = roundingPolicy;
    }

    public static DecliningBalanceDepreciationPolicy of(BigDecimal rate) {
        return new DecliningBalanceDepreciationPolicy(rate, RoundingPolicy.roundDown());
    }

    public static DecliningBalanceDepreciationPolicy of(BigDecimal rate, RoundingPolicy roundingPolicy) {
        return new DecliningBalanceDepreciationPolicy(rate, roundingPolicy);
    }

    @Override
    public Money depreciationFor(FixedAsset asset, Money accumulatedDepreciationBeforeThisPeriod) {
        long bookValue = asset.acquisitionCost().amountInYen() - accumulatedDepreciationBeforeThisPeriod.amountInYen();
        BigDecimal raw = BigDecimal.valueOf(bookValue).multiply(rate);
        return roundingPolicy.apply(raw);
    }
}
