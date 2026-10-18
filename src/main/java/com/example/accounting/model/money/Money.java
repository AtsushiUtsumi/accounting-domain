package com.example.accounting.model.money;

import java.math.BigDecimal;

/** 円単位の金額。常に円固定で、小数(銭)は扱わない。 */
public record Money(long amountInYen) implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    public static Money ofYen(long amountInYen) {
        return new Money(amountInYen);
    }

    public Money plus(Money other) {
        return new Money(this.amountInYen + other.amountInYen);
    }

    public Money minus(Money other) {
        return new Money(this.amountInYen - other.amountInYen);
    }

    public Money negate() {
        return new Money(-this.amountInYen);
    }

    public boolean isZero() {
        return amountInYen == 0L;
    }

    public boolean isPositive() {
        return amountInYen > 0L;
    }

    public boolean isNegative() {
        return amountInYen < 0L;
    }

    /** 小数が出る計算(比率配分など)の結果を、指定した {@link RoundingPolicy} で円に丸める。 */
    public Money times(BigDecimal multiplier, RoundingPolicy policy) {
        BigDecimal raw = BigDecimal.valueOf(amountInYen).multiply(multiplier);
        return policy.apply(raw);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.amountInYen, other.amountInYen);
    }
}
