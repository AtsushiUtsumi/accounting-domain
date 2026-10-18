package com.example.accounting.model.account;

/** 勘定科目の5要素区分。作成後に変更できない。 */
public enum AccountType {
    ASSET(Side.DEBIT),
    LIABILITY(Side.CREDIT),
    EQUITY(Side.CREDIT),
    REVENUE(Side.CREDIT),
    EXPENSE(Side.DEBIT);

    private final Side normalSide;

    AccountType(Side normalSide) {
        this.normalSide = normalSide;
    }

    /** 残高が増える側(資産・費用は借方、負債・純資産・収益は貸方)。 */
    public Side normalSide() {
        return normalSide;
    }
}
