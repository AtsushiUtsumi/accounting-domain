package com.example.accounting.model.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 円未満の端数が出る計算結果を円に丸める方法。減価償却費の月割配分などで使う。 */
public interface RoundingPolicy {

    Money apply(BigDecimal rawAmountInYen);

    static RoundingPolicy roundDown() {
        return raw -> Money.ofYen(raw.setScale(0, RoundingMode.DOWN).longValueExact());
    }

    static RoundingPolicy roundHalfUp() {
        return raw -> Money.ofYen(raw.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }
}
