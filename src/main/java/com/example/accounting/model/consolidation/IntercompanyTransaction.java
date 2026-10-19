package com.example.accounting.model.consolidation;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.money.Money;

/**
 * 連結会社間の取引高(売上高と仕入・売上原価、受取利息と支払利息など)。連結では相殺消去する。
 *
 * @param revenue 売った側(受け取った側)の収益の科目
 * @param expense 買った側(支払った側)の費用の科目
 */
public record IntercompanyTransaction(Account revenue, Account expense, Money amount) {

    public IntercompanyTransaction {
        if (revenue == null || expense == null) {
            throw new AccountingException("revenue and expense are required");
        }
        if (amount == null || !amount.isPositive()) {
            throw new AccountingException("amount must be positive");
        }
    }
}
