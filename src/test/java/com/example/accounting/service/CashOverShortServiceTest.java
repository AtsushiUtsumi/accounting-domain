package com.example.accounting.service;

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

class CashOverShortServiceTest {

    private final CashOverShortService service = new CashOverShortService();

    private static Journal openingCash(long amount) {
        return Journal.of(
                JournalId.newId(), LocalDate.of(2026, 4, 1), "現金の元入れ",
                JournalType.OPENING,
                List.of(
                        JournalLine.debit(StandardAccounts.CASH, Money.ofYen(amount)),
                        JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(amount))
                )
        );
    }

    /**
     * 帳簿上の現金残高50,000円に対し、実際の手許有高が47,000円(3,000円不足)。
     * 原因判明分2,000円(保険料の記帳漏れ)を振り替え、残り1,000円は決算時に雑損へ振り替える。
     */
    @Test
    void shortage_isRecordedThenPartiallyExplainedThenSettledAsMiscellaneousLoss() {
        List<Journal> journals = new ArrayList<>();
        journals.add(openingCash(50_000));

        Journal discovery = service.recordDiscrepancy(
                Money.ofYen(47_000), journals, LocalDate.of(2026, 9, 30)).orElseThrow();
        assertThat(discovery.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.CASH_OVER_SHORT);
            assertThat(line.amount()).isEqualTo(Money.ofYen(3_000));
        });
        journals.add(discovery);

        Journal causeIdentified = Journal.of(
                JournalId.newId(), LocalDate.of(2026, 10, 5), "保険料の記帳漏れが判明",
                JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.INSURANCE_EXPENSE, Money.ofYen(2_000)),
                        JournalLine.credit(StandardAccounts.CASH_OVER_SHORT, Money.ofYen(2_000))
                )
        );
        journals.add(causeIdentified);

        Journal settlement = service.settleAtPeriodEnd(journals, LocalDate.of(2027, 3, 31)).orElseThrow();
        assertThat(settlement.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.MISCELLANEOUS_LOSS);
            assertThat(line.amount()).isEqualTo(Money.ofYen(1_000));
        });
        journals.add(settlement);

        assertThat(Ledger.of(StandardAccounts.CASH_OVER_SHORT, journals).balance()).isEqualTo(Money.ZERO);
    }

    /**
     * 帳簿上の現金残高50,000円に対し、実際の手許有高が53,000円(3,000円過剰)。
     * 原因不明のまま決算を迎え、全額を雑収入へ振り替える。
     */
    @Test
    void overage_isRecordedThenSettledAsMiscellaneousIncomeWhenUnexplained() {
        List<Journal> journals = new ArrayList<>();
        journals.add(openingCash(50_000));

        Journal discovery = service.recordDiscrepancy(
                Money.ofYen(53_000), journals, LocalDate.of(2026, 9, 30)).orElseThrow();
        assertThat(discovery.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.CASH);
            assertThat(line.amount()).isEqualTo(Money.ofYen(3_000));
        });
        journals.add(discovery);

        Journal settlement = service.settleAtPeriodEnd(journals, LocalDate.of(2027, 3, 31)).orElseThrow();
        assertThat(settlement.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.MISCELLANEOUS_INCOME);
            assertThat(line.amount()).isEqualTo(Money.ofYen(3_000));
        });
        journals.add(settlement);

        assertThat(Ledger.of(StandardAccounts.CASH_OVER_SHORT, journals).balance()).isEqualTo(Money.ZERO);
    }

    @Test
    void recordDiscrepancy_noDifference_producesNoJournal() {
        List<Journal> journals = List.of(openingCash(50_000));

        assertThat(service.recordDiscrepancy(Money.ofYen(50_000), journals, LocalDate.of(2026, 9, 30)))
                .isEmpty();
    }

    @Test
    void settleAtPeriodEnd_noRemainingBalance_producesNoJournal() {
        assertThat(service.settleAtPeriodEnd(List.of(), LocalDate.of(2027, 3, 31))).isEmpty();
    }
}
