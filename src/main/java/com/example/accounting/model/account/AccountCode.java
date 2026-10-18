package com.example.accounting.model.account;

import com.example.accounting.exception.AccountingException;

/** 勘定科目の識別子。科目の同一性はこの値で判定する(名称は変更可)。 */
public record AccountCode(String value) {

    public AccountCode {
        if (value == null || value.isBlank()) {
            throw new AccountingException("AccountCode must not be blank");
        }
    }

    public static AccountCode of(String value) {
        return new AccountCode(value);
    }
}
