package com.example.accounting.model.account;

import com.example.accounting.exception.AccountingException;

/** 会社が自由に付けるグルーピング用タグ。利益計算には影響しない。 */
public record AccountTag(String value) {

    public AccountTag {
        if (value == null || value.isBlank()) {
            throw new AccountingException("AccountTag must not be blank");
        }
    }

    public static AccountTag of(String value) {
        return new AccountTag(value);
    }
}
