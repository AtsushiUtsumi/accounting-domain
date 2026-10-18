package com.example.accounting.model.asset;

import com.example.accounting.model.money.Money;

/**
 * 当期の利用量にもとづいて償却費を計算する方法(生産高比例法など)。期間(月数)ではなく利用量で決まるため、
 * {@link FixedAsset#depreciationEntryByUsage} から呼ぶ。
 */
public interface UsageBasedDepreciationPolicy extends DepreciationPolicy {

    /**
     * 当期の利用量に応じた減価償却費(償却限度額でのキャップはしない)。
     *
     * @param asset         対象の固定資産
     * @param usageThisPeriod 当期の利用量(生産量・走行距離など)
     */
    Money depreciationForUsage(FixedAsset asset, long usageThisPeriod);
}
