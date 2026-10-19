package com.example.accounting.model.journal;

/** 仕訳の種類。 */
public enum JournalType {
    /** 通常の仕訳。 */
    NORMAL,
    /** 決算整理仕訳。 */
    ADJUSTING,
    /** 損益振替仕訳。 */
    CLOSING,
    /** 開始仕訳。 */
    OPENING,
    /** 再振替仕訳。 */
    REVERSING,
    /** 取消(赤黒処理の逆仕訳)。 */
    REVERSAL,
    /** 連結修正仕訳。個別の帳簿には記帳しない({@link PostedJournal#post} で受け付けない)。 */
    CONSOLIDATION
}
