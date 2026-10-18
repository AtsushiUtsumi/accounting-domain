package com.example.accounting.model.account;

/** 仕訳の借方・貸方。 */
public enum Side {
    DEBIT,
    CREDIT;

    public Side opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
