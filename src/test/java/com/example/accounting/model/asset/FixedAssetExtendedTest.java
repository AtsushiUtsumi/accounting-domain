package com.example.accounting.model.asset;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.statement.Ledger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.journal;
import static com.example.accounting.support.Journals.normal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 月割償却・生産高比例法・除却・廃棄・圧縮記帳。期待値は問題集の解答例ではなく、
 * 一般的なテキストの処理にもとづく(未検証)。会計期間は4月1日〜3月31日。
 */
@Tag("unverified")
class FixedAssetExtendedTest {

    private final AccountingPeriod period2026 = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
    private final AccountingPeriod period2027 = period2026.nextPeriod();
    private final AccountingPeriod period2028 = period2027.nextPeriod();

    private static FixedAsset asset(Account assetAccount, Account accumulated, long cost, LocalDate acquired,
                                    int life, long residual, DepreciationPolicy policy) {
        return new FixedAsset(FixedAssetId.newId(), assetAccount.name(), assetAccount, accumulated,
                StandardAccounts.DEPRECIATION_EXPENSE, yen(cost), acquired, life, yen(residual), policy);
    }

    private static FixedAsset equipmentAcquiredInOctober() {
        return asset(StandardAccounts.EQUIPMENT, StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT,
                600_000, LocalDate.of(2026, 10, 1), 5, 0, StraightLineDepreciationPolicy.of());
    }

    /** 10月1日に取得した備品600,000円(定額法・5年・残存0)。初年度は6か月分 120,000 × 6/12 = 60,000円。 */
    @Test
    void monthly_firstPeriodIsProratedFromTheAcquisitionMonth() {
        FixedAsset asset = equipmentAcquiredInOctober();

        Journal entry = asset.depreciationEntry(List.of(), period2026, period2026.endDate()).orElseThrow();

        assertThat(entry.debitTotal()).isEqualTo(yen(60_000));
        assertThat(entry.type()).isEqualTo(JournalType.ADJUSTING);
    }

    /** 1か月未満の端数は1か月とする。10月15日取得でも6か月分。 */
    @Test
    void monthly_partialMonthCountsAsAFullMonth() {
        FixedAsset asset = asset(StandardAccounts.EQUIPMENT, StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT,
                600_000, LocalDate.of(2026, 10, 15), 5, 0, StraightLineDepreciationPolicy.of());

        Journal entry = asset.depreciationEntry(List.of(), period2026, period2026.endDate()).orElseThrow();

        assertThat(entry.debitTotal()).isEqualTo(yen(60_000));
    }

    /** 定率法(償却率0.4)で7月1日取得の1,000,000円。初年度は 400,000 × 9/12 = 300,000円。 */
    @Test
    void monthly_decliningBalanceIsProratedTheSameWay() {
        FixedAsset asset = asset(StandardAccounts.EQUIPMENT, StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT,
                1_000_000, LocalDate.of(2026, 7, 1), 5, 0,
                DecliningBalanceDepreciationPolicy.of(new BigDecimal("0.4")));

        Journal entry = asset.depreciationEntry(List.of(), period2026, period2026.endDate()).orElseThrow();

        assertThat(entry.debitTotal()).isEqualTo(yen(300_000));
    }

    /**
     * 期中売却。2026年10月取得の備品を2028年6月30日に400,000円で売却。
     * 償却累計額 = 60,000(6か月) + 120,000(12か月) + 30,000(4〜6月の3か月) = 210,000円、
     * 帳簿価額 390,000円、売却益 10,000円。
     */
    @Test
    void monthly_depreciationUpToTheSaleDateThenDisposal() {
        FixedAsset asset = equipmentAcquiredInOctober();
        List<Journal> journals = new ArrayList<>();
        journals.add(asset.depreciationEntry(journals, period2026, period2026.endDate()).orElseThrow());
        journals.add(asset.depreciationEntry(journals, period2027, period2027.endDate()).orElseThrow());
        LocalDate saleDate = LocalDate.of(2028, 6, 30);
        Journal untilSale = asset.depreciationEntry(journals, period2028, saleDate).orElseThrow();
        journals.add(untilSale);

        assertThat(untilSale.debitTotal()).isEqualTo(yen(30_000));
        assertThat(asset.accumulatedDepreciation(journals)).isEqualTo(yen(210_000));

        Journal sale = asset.disposalEntry(journals, saleDate, StandardAccounts.OTHER_RECEIVABLES, yen(400_000));
        assertThat(sale.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, 210_000),
                dr(StandardAccounts.OTHER_RECEIVABLES, 400_000),
                cr(StandardAccounts.EQUIPMENT, 600_000),
                cr(StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS, 10_000));
    }

    @Test
    void monthly_noDepreciationForAPeriodBeforeAcquisition() {
        FixedAsset asset = asset(StandardAccounts.EQUIPMENT, StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT,
                600_000, LocalDate.of(2027, 5, 1), 5, 0, StraightLineDepreciationPolicy.of());

        assertThat(asset.depreciationEntry(List.of(), period2026, period2026.endDate())).isEmpty();
    }

    /**
     * 生産高比例法。車両1,000,000円(残存100,000円)、総走行可能距離90,000km、当期18,000km。
     * 900,000 × 18,000 / 90,000 = 180,000円。
     */
    @Test
    void unitsOfProduction_depreciatesByUsage() {
        FixedAsset vehicle = asset(StandardAccounts.VEHICLES, StandardAccounts.ACCUMULATED_DEPRECIATION_VEHICLES,
                1_000_000, LocalDate.of(2026, 4, 1), 5, 100_000, UnitsOfProductionDepreciationPolicy.of(90_000));

        Journal entry = vehicle.depreciationEntryByUsage(List.of(), 18_000, period2026.endDate()).orElseThrow();

        assertThat(entry.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.DEPRECIATION_EXPENSE, 180_000),
                cr(StandardAccounts.ACCUMULATED_DEPRECIATION_VEHICLES, 180_000));
    }

    @Test
    void unitsOfProduction_cannotBeUsedWithoutUsage() {
        FixedAsset vehicle = asset(StandardAccounts.VEHICLES, StandardAccounts.ACCUMULATED_DEPRECIATION_VEHICLES,
                1_000_000, LocalDate.of(2026, 4, 1), 5, 100_000, UnitsOfProductionDepreciationPolicy.of(90_000));

        assertThatThrownBy(() -> vehicle.depreciationEntry(List.of(), period2026.endDate()))
                .hasMessageContaining("usage");
    }

    private static Journal accumulated(long amount) {
        return journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.DEPRECIATION_EXPENSE, amount),
                cr(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, amount));
    }

    /**
     * 除却。備品600,000円(償却累計額480,000円)、見積処分価額30,000円。
     * 貯蔵品30,000円、固定資産除却損 = 120,000 − 30,000 = 90,000円。
     */
    @Test
    void retirement_recordsSuppliesAndLoss() {
        FixedAsset asset = equipmentAcquiredInOctober();

        Journal retirement = asset.retirementEntry(List.of(accumulated(480_000)), LocalDate.of(2030, 4, 1), yen(30_000));

        assertThat(retirement.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, 480_000),
                dr(StandardAccounts.SUPPLIES, 30_000),
                dr(StandardAccounts.LOSS_ON_RETIREMENT_OF_FIXED_ASSETS, 90_000),
                cr(StandardAccounts.EQUIPMENT, 600_000));
    }

    /** 廃棄。帳簿価額120,000円 + 廃棄費用5,000円(現金払い) = 固定資産廃棄損125,000円。 */
    @Test
    void abandonment_includesDisposalCostInTheLoss() {
        FixedAsset asset = equipmentAcquiredInOctober();

        Journal abandonment = asset.abandonmentEntry(
                List.of(accumulated(480_000)), LocalDate.of(2030, 4, 1), yen(5_000), StandardAccounts.CASH);

        assertThat(abandonment.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT, 480_000),
                dr(StandardAccounts.LOSS_ON_ABANDONMENT_OF_FIXED_ASSETS, 125_000),
                cr(StandardAccounts.EQUIPMENT, 600_000),
                cr(StandardAccounts.CASH, 5_000));
    }

    /**
     * 圧縮記帳(直接減額方式)。国庫補助金1,000,000円を受け、建物3,000,000円(定額法・20年・残存0)を取得。
     * 圧縮損1,000,000円、以後は圧縮後の2,000,000円をもとに年100,000円を償却する。
     */
    @Test
    void compression_reducesTheCostAndTheBaseOfDepreciation() {
        LocalDate acquired = LocalDate.of(2026, 4, 1);
        FixedAsset building = asset(StandardAccounts.BUILDINGS, StandardAccounts.ACCUMULATED_DEPRECIATION_BUILDINGS,
                3_000_000, acquired, 20, 0, StraightLineDepreciationPolicy.of());
        Journal subsidy = normal(acquired, dr(StandardAccounts.CASH, 1_000_000),
                cr(StandardAccounts.GAIN_ON_GOVERNMENT_SUBSIDIES, 1_000_000));
        Journal purchase = normal(acquired, dr(StandardAccounts.BUILDINGS, 3_000_000),
                cr(StandardAccounts.CASH, 3_000_000));

        FixedAsset.Compression compression = building.compress(yen(1_000_000), acquired);
        FixedAsset compressed = compression.compressedAsset();

        assertThat(compression.entry().lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.LOSS_ON_COMPRESSION_OF_FIXED_ASSETS, 1_000_000),
                cr(StandardAccounts.BUILDINGS, 1_000_000));
        assertThat(compressed.acquisitionCost()).isEqualTo(yen(2_000_000));
        List<Journal> journals = List.of(subsidy, purchase, compression.entry());
        assertThat(Ledger.of(StandardAccounts.BUILDINGS, journals).balance()).isEqualTo(yen(2_000_000));
        assertThat(compressed.depreciationEntry(journals, period2026.endDate()).orElseThrow().debitTotal())
                .isEqualTo(Money.ofYen(100_000));
    }
}
