package com.example.accounting.model.asset;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 備品(取得価額500,000円、耐用年数5年、残存価額0円)の減価償却(間接法)。
 */
class FixedAssetTest {

    private static FixedAsset equipment(DepreciationPolicy policy) {
        return new FixedAsset(
                FixedAssetId.newId(), "備品",
                StandardAccounts.EQUIPMENT,
                StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT,
                StandardAccounts.DEPRECIATION_EXPENSE,
                Money.ofYen(500_000),
                LocalDate.of(2026, 4, 1),
                5,
                Money.ZERO,
                policy
        );
    }

    @Test
    void straightLine_firstYearDepreciationIsOneFifthOfDepreciableAmount() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());

        Journal entry = asset.depreciationEntry(List.of(), LocalDate.of(2027, 3, 31)).orElseThrow();

        assertThat(entry.debitTotal()).isEqualTo(Money.ofYen(100_000));
        assertThat(asset.accumulatedDepreciation(List.of(entry))).isEqualTo(Money.ofYen(100_000));
        assertThat(asset.bookValue(List.of(entry))).isEqualTo(Money.ofYen(400_000));
    }

    @Test
    void straightLine_secondYearDepreciationIsTheSameAmount() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());
        Journal year1 = asset.depreciationEntry(List.of(), LocalDate.of(2027, 3, 31)).orElseThrow();

        Journal year2 = asset.depreciationEntry(List.of(year1), LocalDate.of(2028, 3, 31)).orElseThrow();

        assertThat(year2.debitTotal()).isEqualTo(Money.ofYen(100_000));
        assertThat(asset.accumulatedDepreciation(List.of(year1, year2))).isEqualTo(Money.ofYen(200_000));
    }

    @Test
    void straightLine_isCappedAtTheDepreciableLimit() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());
        Journal alreadyDepreciated = Journal.of(
                com.example.accounting.model.journal.JournalId.newId(),
                LocalDate.of(2030, 3, 31), "過去の減価償却(テスト用に累計480,000円まで進めておく)",
                com.example.accounting.model.journal.JournalType.ADJUSTING,
                List.of(
                        com.example.accounting.model.journal.JournalLine.debit(
                                StandardAccounts.DEPRECIATION_EXPENSE, Money.ofYen(480_000)),
                        com.example.accounting.model.journal.JournalLine.credit(
                                StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, Money.ofYen(480_000))
                )
        );

        Journal finalYear = asset.depreciationEntry(List.of(alreadyDepreciated), LocalDate.of(2031, 3, 31))
                .orElseThrow();

        assertThat(finalYear.debitTotal()).isEqualTo(Money.ofYen(20_000));
        assertThat(asset.depreciationEntry(List.of(alreadyDepreciated, finalYear), LocalDate.of(2032, 3, 31)))
                .isEmpty();
    }

    @Test
    void decliningBalance_depreciatesOnTheRemainingBookValue() {
        FixedAsset asset = equipment(DecliningBalanceDepreciationPolicy.of(new BigDecimal("0.2")));

        Journal year1 = asset.depreciationEntry(List.of(), LocalDate.of(2027, 3, 31)).orElseThrow();
        assertThat(year1.debitTotal()).isEqualTo(Money.ofYen(100_000));

        Journal year2 = asset.depreciationEntry(List.of(year1), LocalDate.of(2028, 3, 31)).orElseThrow();
        assertThat(year2.debitTotal()).isEqualTo(Money.ofYen(80_000));
    }

    /**
     * 備品(取得価額500,000円)を2年分(定額法で200,000円)償却した後、帳簿価額300,000円で売却する。
     */
    @Test
    void disposalEntry_recordsGain_whenProceedsExceedBookValue() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());
        Journal year1 = asset.depreciationEntry(List.of(), LocalDate.of(2027, 3, 31)).orElseThrow();
        Journal year2 = asset.depreciationEntry(List.of(year1), LocalDate.of(2028, 3, 31)).orElseThrow();
        List<Journal> history = List.of(year1, year2);

        assertThat(asset.bookValue(history)).isEqualTo(Money.ofYen(300_000));

        Journal disposal = asset.disposalEntry(
                history, LocalDate.of(2028, 4, 1), StandardAccounts.CASH, Money.ofYen(350_000));

        assertThat(disposal.debitTotal()).isEqualTo(Money.ofYen(550_000));
        assertThat(disposal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT);
            assertThat(line.amount()).isEqualTo(Money.ofYen(200_000));
        });
        assertThat(disposal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.CASH);
            assertThat(line.amount()).isEqualTo(Money.ofYen(350_000));
        });
        assertThat(disposal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.EQUIPMENT);
            assertThat(line.amount()).isEqualTo(Money.ofYen(500_000));
        });
        assertThat(disposal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS);
            assertThat(line.amount()).isEqualTo(Money.ofYen(50_000));
        });
        assertThat(disposal.lines()).noneSatisfy(line ->
                assertThat(line.account()).isEqualTo(StandardAccounts.LOSS_ON_DISPOSAL_OF_FIXED_ASSETS));
    }

    @Test
    void disposalEntry_recordsLoss_whenProceedsAreBelowBookValue() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());
        Journal year1 = asset.depreciationEntry(List.of(), LocalDate.of(2027, 3, 31)).orElseThrow();
        Journal year2 = asset.depreciationEntry(List.of(year1), LocalDate.of(2028, 3, 31)).orElseThrow();
        List<Journal> history = List.of(year1, year2);

        Journal disposal = asset.disposalEntry(
                history, LocalDate.of(2028, 4, 1), StandardAccounts.CASH, Money.ofYen(250_000));

        assertThat(disposal.lines()).anySatisfy(line -> {
            assertThat(line.account()).isEqualTo(StandardAccounts.LOSS_ON_DISPOSAL_OF_FIXED_ASSETS);
            assertThat(line.amount()).isEqualTo(Money.ofYen(50_000));
        });
        assertThat(disposal.lines()).noneSatisfy(line ->
                assertThat(line.account()).isEqualTo(StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS));
    }

    @Test
    void disposalEntry_recordsNeitherGainNorLoss_whenProceedsEqualBookValue() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());
        Journal year1 = asset.depreciationEntry(List.of(), LocalDate.of(2027, 3, 31)).orElseThrow();
        Journal year2 = asset.depreciationEntry(List.of(year1), LocalDate.of(2028, 3, 31)).orElseThrow();
        List<Journal> history = List.of(year1, year2);

        Journal disposal = asset.disposalEntry(
                history, LocalDate.of(2028, 4, 1), StandardAccounts.OTHER_RECEIVABLES, Money.ofYen(300_000));

        assertThat(disposal.lines()).noneSatisfy(line ->
                assertThat(line.account()).isEqualTo(StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS));
        assertThat(disposal.lines()).noneSatisfy(line ->
                assertThat(line.account()).isEqualTo(StandardAccounts.LOSS_ON_DISPOSAL_OF_FIXED_ASSETS));
        assertThat(disposal.debitTotal()).isEqualTo(Money.ofYen(500_000));
    }

    @Test
    void disposalEntry_negativeProceeds_isRejected() {
        FixedAsset asset = equipment(StraightLineDepreciationPolicy.of());

        assertThatThrownBy(() -> asset.disposalEntry(
                List.of(), LocalDate.of(2028, 4, 1), StandardAccounts.CASH, Money.ofYen(-1)))
                .isInstanceOf(AccountingException.class);
    }

    @Test
    void residualValueGreaterThanAcquisitionCost_isRejected() {
        assertThatThrownBy(() -> new FixedAsset(
                FixedAssetId.newId(), "備品",
                StandardAccounts.EQUIPMENT,
                StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT,
                StandardAccounts.DEPRECIATION_EXPENSE,
                Money.ofYen(500_000),
                LocalDate.of(2026, 4, 1),
                5,
                Money.ofYen(600_000),
                StraightLineDepreciationPolicy.of()
        )).isInstanceOf(AccountingException.class);
    }
}
