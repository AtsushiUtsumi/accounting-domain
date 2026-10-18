package com.example.accounting.model.statement;

/** キャッシュ・フロー計算書上の活動区分。 */
public enum CashFlowActivity {
    OPERATING,
    INVESTING,
    FINANCING,
    /** 現金自体や、損益勘定・現金過不足などキャッシュ・フロー計算書の対象にならない科目。 */
    EXCLUDED
}
