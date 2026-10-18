package com.example.accounting.model.asset;

import com.example.accounting.model.money.Money;

/** 1期分の減価償却費を計算する方法(定額法・定率法など)。差し替え可能にするためインターフェースにする。 */
public interface DepreciationPolicy {

    /**
     * この期の減価償却費(償却限度額でのキャップはしない。キャップは {@link FixedAsset} の責務)。
     *
     * @param asset                              対象の固定資産
     * @param accumulatedDepreciationBeforeThisPeriod この期の開始時点での累計償却額
     */
    Money depreciationFor(FixedAsset asset, Money accumulatedDepreciationBeforeThisPeriod);
}
