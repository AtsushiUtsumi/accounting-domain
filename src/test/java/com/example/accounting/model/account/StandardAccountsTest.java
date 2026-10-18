package com.example.accounting.model.account;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 標準科目の初期セットの構造(会計ルールの正しさではなく、重複や保護の有無)を確かめる。 */
class StandardAccountsTest {

    @Test
    void codesAreUnique() {
        Set<AccountCode> codes = new HashSet<>();
        for (Account account : StandardAccounts.ALL) {
            assertThat(codes.add(account.code())).as("duplicate code " + account.code().value()).isTrue();
        }
    }

    @Test
    void allAccountsAreStandardAndActive() {
        assertThat(StandardAccounts.ALL).allMatch(Account::standard).allMatch(Account::active);
    }

    @Test
    void protectedAccountsArePresent() {
        assertThat(StandardAccounts.ALL)
                .contains(StandardAccounts.PROFIT_AND_LOSS, StandardAccounts.RETAINED_EARNINGS);
    }
}
