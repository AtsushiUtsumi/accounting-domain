package com.example.accounting.model.account;

import com.example.accounting.exception.AccountingException;

import java.util.Set;

/** 勘定科目。同一性は {@link AccountCode} で判定する(名称が違っても code が同じなら同じ科目)。 */
public record Account(
        AccountCode code,
        String name,
        AccountType type,
        AccountCategory category,
        Set<AccountTag> tags,
        boolean standard,
        boolean active
) {

    public Account {
        if (code == null) {
            throw new AccountingException("code is required");
        }
        if (name == null || name.isBlank()) {
            throw new AccountingException("name is required");
        }
        if (type == null) {
            throw new AccountingException("type is required");
        }
        if (category == null) {
            throw new AccountingException("category is required");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static Account of(AccountCode code, String name, AccountType type, AccountCategory category) {
        return new Account(code, name, type, category, Set.of(), false, true);
    }

    public Account rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new AccountingException("name is required");
        }
        return new Account(code, newName, type, category, tags, standard, active);
    }

    public Account withTags(Set<AccountTag> newTags) {
        return new Account(code, name, type, category, newTags, standard, active);
    }

    /** 標準科目は無効化できない。使用済みかどうかの判定はリポジトリ側の責務。 */
    public Account deactivate() {
        if (standard) {
            throw new AccountingException("standard account cannot be deactivated: " + code.value());
        }
        return new Account(code, name, type, category, tags, standard, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
            return false;
        }
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }
}
