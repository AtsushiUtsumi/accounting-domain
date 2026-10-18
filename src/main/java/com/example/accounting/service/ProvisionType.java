package com.example.accounting.service;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;

/**
 * 貸倒引当金以外の引当金の種類と、それぞれが使う科目の組。
 * 貸倒引当金は当期発生・前期発生の区別があるため、{@link BadDebtService} と
 * {@link AdjustingEntryService#adjustAllowanceForDoubtfulAccounts} で別に扱う。
 */
public enum ProvisionType {
    /** 賞与引当金。 */
    BONUSES(
            StandardAccounts.PROVISION_FOR_BONUSES,
            StandardAccounts.BONUS_PROVISION_EXPENSE,
            StandardAccounts.BONUS_PROVISION_REVERSAL,
            StandardAccounts.BONUSES),
    /** 修繕引当金。 */
    REPAIRS(
            StandardAccounts.PROVISION_FOR_REPAIRS,
            StandardAccounts.REPAIR_PROVISION_EXPENSE,
            StandardAccounts.REPAIR_PROVISION_REVERSAL,
            StandardAccounts.REPAIR_EXPENSE),
    /** 商品保証引当金。 */
    PRODUCT_WARRANTIES(
            StandardAccounts.PROVISION_FOR_PRODUCT_WARRANTIES,
            StandardAccounts.WARRANTY_PROVISION_EXPENSE,
            StandardAccounts.WARRANTY_PROVISION_REVERSAL,
            StandardAccounts.WARRANTY_EXPENSE),
    /** 退職給付引当金。戻入はしない(繰入額は費用の額として与える)。 */
    RETIREMENT_BENEFITS(
            StandardAccounts.PROVISION_FOR_RETIREMENT_BENEFITS,
            StandardAccounts.RETIREMENT_BENEFIT_EXPENSE,
            null,
            StandardAccounts.RETIREMENT_BENEFIT_EXPENSE);

    private final Account provisionAccount;
    private final Account provisionExpenseAccount;
    private final Account reversalAccount;
    private final Account expenseOnUseAccount;

    ProvisionType(Account provisionAccount, Account provisionExpenseAccount,
                  Account reversalAccount, Account expenseOnUseAccount) {
        this.provisionAccount = provisionAccount;
        this.provisionExpenseAccount = provisionExpenseAccount;
        this.reversalAccount = reversalAccount;
        this.expenseOnUseAccount = expenseOnUseAccount;
    }

    /** 引当金の勘定(負債)。 */
    public Account provisionAccount() {
        return provisionAccount;
    }

    /** 決算で繰り入れるときの費用(〇〇引当金繰入)。 */
    public Account provisionExpenseAccount() {
        return provisionExpenseAccount;
    }

    /** 引当金が過剰なときの戻入(収益)。戻入をしない種類は null。 */
    public Account reversalAccount() {
        return reversalAccount;
    }

    /** 支払時に引当金で足りない分を計上する費用(賞与・修繕費など)。 */
    public Account expenseOnUseAccount() {
        return expenseOnUseAccount;
    }
}
