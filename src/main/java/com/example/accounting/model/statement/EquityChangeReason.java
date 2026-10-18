package com.example.accounting.model.statement;

/** 株主資本等変動計算書における当期変動額の事由。この範囲(単体・商業簿記)で生じ得るものに限る。 */
public enum EquityChangeReason {
    /** 新株の発行(増資・吸収合併による発行を含む)。資本金・資本準備金・その他資本剰余金が増加する。 */
    ISSUANCE_OF_SHARES,
    /** 剰余金の配当。繰越利益剰余金(またはその他資本剰余金)が減少し、準備金が増加する。 */
    DIVIDENDS,
    /** 当期純利益(または純損失)の損益振替。繰越利益剰余金が変動する。 */
    NET_INCOME,
    /** 株主資本の計数の変動(任意積立金の積立・取崩しなど、純資産の科目の間の振替)。 */
    TRANSFER_WITHIN_EQUITY,
    /** 株主資本以外の項目の当期変動額(その他有価証券評価差額金の増減。純額で表示する)。 */
    VALUATION_CHANGE,
    /** 上記のいずれにも当たらない変動(この範囲では想定していない)。 */
    OTHER
}
