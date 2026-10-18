package com.example.accounting.model.period;

/** 会計期間の状態。CLOSED の期には仕訳を追加できない。 */
public enum PeriodStatus {
    OPEN,
    CLOSED
}
