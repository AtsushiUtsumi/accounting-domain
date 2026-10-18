package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.Ledger;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingSecuritiesServiceTest {

    private final TradingSecuritiesService service = new TradingSecuritiesService();

    private static Journal purchase(long cost) {
        return Journal.of(
                JournalId.newId(), LocalDate.of(2026, 5, 1), "売買目的有価証券の購入",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.TRADING_SECURITIES, Money.ofYen(cost)),
                        JournalLine.credit(StandardAccounts.CASH, Money.ofYen(cost))
                )
        );
    }

    /** 取得原価80,000円の有価証券を期末に時価90,000円で評価し、評価益10,000円を計上する(切放方式)。 */
    @Test
    void valuateAtPeriodEnd_recordsUnrealizedGain_whenFairValueExceedsBookValue() {
        List<Journal> journals = List.of(purchase(80_000));

        Journal valuation = service.valuateAtPeriodEnd(
                Money.ofYen(90_000), journals, LocalDate.of(2027, 3, 31)).orElseThrow();

        assertThat(valuation.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.TRADING_SECURITIES);
            assertThat(line.amount()).isEqualTo(Money.ofYen(10_000));
        });
        assertThat(valuation.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.UNREALIZED_GAIN_ON_SECURITIES);
            assertThat(line.amount()).isEqualTo(Money.ofYen(10_000));
        });

        List<Journal> all = new ArrayList<>(journals);
        all.add(valuation);
        assertThat(Ledger.of(StandardAccounts.TRADING_SECURITIES, all).balance()).isEqualTo(Money.ofYen(90_000));
    }

    /** 取得原価80,000円の有価証券を期末に時価70,000円で評価し、評価損10,000円を計上する。 */
    @Test
    void valuateAtPeriodEnd_recordsUnrealizedLoss_whenFairValueIsBelowBookValue() {
        List<Journal> journals = List.of(purchase(80_000));

        Journal valuation = service.valuateAtPeriodEnd(
                Money.ofYen(70_000), journals, LocalDate.of(2027, 3, 31)).orElseThrow();

        assertThat(valuation.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.UNREALIZED_LOSS_ON_SECURITIES);
            assertThat(line.amount()).isEqualTo(Money.ofYen(10_000));
        });

        List<Journal> all = new ArrayList<>(journals);
        all.add(valuation);
        assertThat(Ledger.of(StandardAccounts.TRADING_SECURITIES, all).balance()).isEqualTo(Money.ofYen(70_000));
    }

    @Test
    void valuateAtPeriodEnd_noDifference_producesNoJournal() {
        List<Journal> journals = List.of(purchase(80_000));

        assertThat(service.valuateAtPeriodEnd(Money.ofYen(80_000), journals, LocalDate.of(2027, 3, 31)))
                .isEmpty();
    }

    /**
     * 評価替え後の帳簿価額90,000円の有価証券を95,000円で売却し、売却益5,000円を計上する。
     * (前年度末に時価90,000円まで評価替えし、切放方式で翌期も90,000円のまま引き継いでいる想定)
     */
    @Test
    void sell_recordsGain_whenProceedsExceedBookValue() {
        List<Journal> journals = new ArrayList<>();
        journals.add(purchase(80_000));
        journals.add(service.valuateAtPeriodEnd(Money.ofYen(90_000), journals, LocalDate.of(2027, 3, 31))
                .orElseThrow());

        Journal sale = service.sell(StandardAccounts.CASH, Money.ofYen(95_000), journals, LocalDate.of(2027, 6, 1));

        assertThat(sale.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.GAIN_ON_SALE_OF_SECURITIES);
            assertThat(line.amount()).isEqualTo(Money.ofYen(5_000));
        });
        assertThat(sale.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.TRADING_SECURITIES);
            assertThat(line.amount()).isEqualTo(Money.ofYen(90_000));
        });

        journals.add(sale);
        assertThat(Ledger.of(StandardAccounts.TRADING_SECURITIES, journals).balance()).isEqualTo(Money.ZERO);
    }

    @Test
    void sell_recordsLoss_whenProceedsAreBelowBookValue() {
        List<Journal> journals = List.of(purchase(80_000));

        Journal sale = service.sell(StandardAccounts.CASH, Money.ofYen(75_000), journals, LocalDate.of(2026, 6, 1));

        assertThat(sale.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.LOSS_ON_SALE_OF_SECURITIES);
            assertThat(line.amount()).isEqualTo(Money.ofYen(5_000));
        });
    }

    @Test
    void sell_withNoHoldings_isRejected() {
        assertThatThrownBy(() -> service.sell(StandardAccounts.CASH, Money.ofYen(1_000), List.of(), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(AccountingException.class);
    }
}
