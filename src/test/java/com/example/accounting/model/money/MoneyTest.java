package com.example.accounting.model.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void plus_addsAmounts() {
        assertThat(Money.ofYen(100).plus(Money.ofYen(200))).isEqualTo(Money.ofYen(300));
    }

    @Test
    void minus_subtractsAmounts() {
        assertThat(Money.ofYen(300).minus(Money.ofYen(100))).isEqualTo(Money.ofYen(200));
    }

    @Test
    void zero_isZero() {
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.ofYen(1).isZero()).isFalse();
    }

    @Test
    void negative_isNegative() {
        assertThat(Money.ofYen(-1).isNegative()).isTrue();
        assertThat(Money.ofYen(1).isNegative()).isFalse();
    }
}
