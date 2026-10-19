package com.example.accounting.model.account;

/**
 * B/S・P/L の標準区分。三表の集計と、P/L の5段階利益の計算に使う。
 * PROFIT_AND_LOSS・SUSPENSE は決算までに必ずゼロにする一時的な勘定専用で、
 * B/S・P/L いずれにも表示しない。
 */
public enum AccountCategory {
    CURRENT_ASSET,
    FIXED_ASSET,
    CURRENT_LIABILITY,
    FIXED_LIABILITY,
    CAPITAL,
    RETAINED_EARNINGS,
    /** 評価・換算差額等(その他有価証券評価差額金など)。株主資本以外の純資産。 */
    VALUATION_AND_TRANSLATION_ADJUSTMENTS,
    /** 非支配株主持分。連結貸借対照表の純資産のうち、親会社以外の株主の持分。連結専用。 */
    NON_CONTROLLING_INTERESTS,
    SALES,
    COST_OF_SALES,
    SG_AND_A,
    NON_OPERATING_INCOME,
    NON_OPERATING_EXPENSE,
    EXTRAORDINARY_INCOME,
    EXTRAORDINARY_LOSS,
    INCOME_TAX_EXPENSE,
    /** 非支配株主に帰属する当期純利益。連結損益計算書で当期純利益から差し引く。連結専用。 */
    NON_CONTROLLING_INTERESTS_PROFIT,
    PROFIT_AND_LOSS,
    /** 現金過不足など、原因判明や決算までの一時的な食い違いを記録する勘定。 */
    SUSPENSE
}
