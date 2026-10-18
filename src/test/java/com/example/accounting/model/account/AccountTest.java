package com.example.accounting.model.account;

import com.example.accounting.exception.AccountingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void equality_isDeterminedByCodeOnly() {
        Account a = Account.of(AccountCode.of("101"), "現金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);
        Account renamed = a.rename("キャッシュ");

        assertThat(renamed).isEqualTo(a);
        assertThat(renamed.name()).isEqualTo("キャッシュ");
    }

    @Test
    void standardAccount_cannotBeDeactivated() {
        assertThatThrownBy(StandardAccounts.CASH::deactivate)
                .isInstanceOf(AccountingException.class);
    }

    @Test
    void nonStandardAccount_canBeDeactivated() {
        Account a = Account.of(AccountCode.of("999"), "雑勘定", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

        Account deactivated = a.deactivate();

        assertThat(deactivated.active()).isFalse();
    }
}
