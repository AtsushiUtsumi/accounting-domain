package com.example.accounting.model.consolidation;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.money.Money;

/**
 * 連結会社間の債権債務(売掛金と買掛金、貸付金と借入金など)。連結では相殺消去する。
 *
 * @param creditor               債権を持つ会社(子会社ならアップストリームとして貸倒引当金の修正を非支配株主に按分する)
 * @param allowance              この債権に対して債権者が期末に設定した貸倒引当金
 * @param allowanceAtBeginning   前期末の連結会社間の債権に対して設定していた貸倒引当金(当期首の修正に使う)
 */
public record IntercompanyBalance(
        CompanyId creditor,
        Account receivable,
        Account payable,
        Money amount,
        Money allowance,
        Money allowanceAtBeginning
) {

    public IntercompanyBalance {
        if (creditor == null || receivable == null || payable == null) {
            throw new AccountingException("creditor, receivable and payable are required");
        }
        if (amount == null || amount.isNegative()) {
            throw new AccountingException("amount must not be negative");
        }
        if (allowance == null || allowance.isNegative() || allowanceAtBeginning == null || allowanceAtBeginning.isNegative()) {
            throw new AccountingException("allowance must not be negative");
        }
    }

    /** 貸倒引当金の修正がない債権債務。 */
    public static IntercompanyBalance of(CompanyId creditor, Account receivable, Account payable, Money amount) {
        return new IntercompanyBalance(creditor, receivable, payable, amount, Money.ZERO, Money.ZERO);
    }

    public IntercompanyBalance withAllowance(Money allowance, Money allowanceAtBeginning) {
        return new IntercompanyBalance(creditor, receivable, payable, amount, allowance, allowanceAtBeginning);
    }
}
