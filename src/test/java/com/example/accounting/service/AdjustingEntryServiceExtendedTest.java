package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.inventory.InventoryCount;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.securities.HeldToMaturityBond;
import com.example.accounting.model.statement.Ledger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.journal;
import static com.example.accounting.support.Journals.normal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * フェーズ6〜10で追加した決算整理(当座借越・再振替の対象・棚卸減耗・引当金・償却原価法・その他有価証券)。
 * 期待値は問題集の解答例ではなく、一般的なテキストの処理にもとづく(未検証)。
 */
@Tag("unverified")
class AdjustingEntryServiceExtendedTest {

    private final AdjustingEntryService service = new AdjustingEntryService();
    private final LocalDate periodEnd = LocalDate.of(2027, 3, 31);
    private final LocalDate nextPeriodStart = LocalDate.of(2027, 4, 1);

    /** 当座預金の残高が30,000円の貸方(借越)なら、当座借越へ振り替える。 */
    @Test
    void reclassifyBankOverdraft_movesACreditBalanceToBankOverdraft() {
        List<Journal> journals = List.of(
                normal(LocalDate.of(2026, 5, 1), dr(StandardAccounts.CHECKING_ACCOUNT, 100_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 100_000)),
                normal(LocalDate.of(2026, 6, 1), dr(StandardAccounts.PURCHASES, 130_000),
                        cr(StandardAccounts.CHECKING_ACCOUNT, 130_000)));

        Journal entry = service.reclassifyBankOverdraft(StandardAccounts.CHECKING_ACCOUNT, journals, periodEnd)
                .orElseThrow();

        assertThat(entry.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CHECKING_ACCOUNT, 30_000),
                cr(StandardAccounts.BANK_OVERDRAFT, 30_000));
    }

    @Test
    void reclassifyBankOverdraft_doesNothingForADebitBalance() {
        List<Journal> journals = List.of(normal(LocalDate.of(2026, 5, 1),
                dr(StandardAccounts.CHECKING_ACCOUNT, 100_000), cr(StandardAccounts.CAPITAL_STOCK, 100_000)));

        assertThat(service.reclassifyBankOverdraft(StandardAccounts.CHECKING_ACCOUNT, journals, periodEnd)).isEmpty();
    }

    /** 当座借越・貯蔵品の決算整理は翌期首に再振替する。通常仕訳(除却で生じた貯蔵品など)は対象外。 */
    @Test
    void reverseAccruals_includesOverdraftAndSuppliesButOnlyAdjustingJournals() {
        Journal overdraft = journal(periodEnd, JournalType.ADJUSTING,
                dr(StandardAccounts.CHECKING_ACCOUNT, 30_000), cr(StandardAccounts.BANK_OVERDRAFT, 30_000));
        Journal supplies = journal(periodEnd, JournalType.ADJUSTING,
                dr(StandardAccounts.SUPPLIES, 2_000), cr(StandardAccounts.TAXES_AND_DUES, 2_000));
        Journal retirement = normal(LocalDate.of(2026, 9, 1),
                dr(StandardAccounts.SUPPLIES, 10_000), cr(StandardAccounts.EQUIPMENT, 10_000));

        List<Journal> reversed = service.reverseAccruals(List.of(overdraft, supplies, retirement), nextPeriodStart);

        assertThat(reversed).hasSize(2);
        assertThat(reversed.get(1).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.TAXES_AND_DUES, 2_000), cr(StandardAccounts.SUPPLIES, 2_000));
    }

    /**
     * 棚卸減耗損・商品評価損(三分法)。帳簿100個・実地95個、原価@1,000円・正味売却価額@900円。
     * 棚卸減耗損 = 5個 × 1,000 = 5,000円、商品評価損 = (1,000 − 900) × 95個 = 9,500円。
     * 期末の繰越商品は 95個 × 900 = 85,500円になる。
     */
    @Test
    void recognizeInventoryLosses_shrinkageAndValuation() {
        InventoryCount count = new InventoryCount(100, 95, yen(1_000), yen(900));
        List<Journal> journals = new ArrayList<>(
                service.recognizeCostOfSales(yen(0), count.bookAmount(), periodEnd));

        List<Journal> losses = service.recognizeInventoryLosses(count, periodEnd);
        journals.addAll(losses);

        assertThat(losses.get(0).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.INVENTORY_SHRINKAGE_LOSS, 5_000),
                cr(StandardAccounts.MERCHANDISE_INVENTORY, 5_000));
        assertThat(losses.get(1).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.INVENTORY_VALUATION_LOSS, 9_500),
                cr(StandardAccounts.MERCHANDISE_INVENTORY, 9_500));
        assertThat(Ledger.of(StandardAccounts.MERCHANDISE_INVENTORY, journals).balance()).isEqualTo(yen(85_500));
    }

    @Test
    void recognizeInventoryLosses_noValuationLossWhenNetRealizableValueIsHigher() {
        InventoryCount count = new InventoryCount(10, 10, yen(1_000), yen(1_200));

        assertThat(service.recognizeInventoryLosses(count, periodEnd)).isEmpty();
    }

    /** 賞与引当金(残高0)を300,000円に。差額補充で300,000円を繰り入れる。 */
    @Test
    void adjustProvision_suppliesTheShortfall() {
        Journal entry = service.adjustProvision(ProvisionType.BONUSES, yen(300_000), List.of(), periodEnd)
                .orElseThrow();

        assertThat(entry.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.BONUS_PROVISION_EXPENSE, 300_000),
                cr(StandardAccounts.PROVISION_FOR_BONUSES, 300_000));
    }

    /** 修繕引当金の残高50,000円に対し必要額30,000円。過剰な20,000円を戻し入れる。 */
    @Test
    void adjustProvision_reversesTheExcess() {
        List<Journal> journals = List.of(journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.REPAIR_PROVISION_EXPENSE, 50_000), cr(StandardAccounts.PROVISION_FOR_REPAIRS, 50_000)));

        Journal entry = service.adjustProvision(ProvisionType.REPAIRS, yen(30_000), journals, periodEnd).orElseThrow();

        assertThat(entry.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.PROVISION_FOR_REPAIRS, 20_000),
                cr(StandardAccounts.REPAIR_PROVISION_REVERSAL, 20_000));
    }

    /** 商品保証引当金の洗替法。残高40,000円を戻し入れ、必要額60,000円を改めて繰り入れる。 */
    @Test
    void replaceProvision_reversesAllThenProvidesTheRequiredAmount() {
        List<Journal> journals = new ArrayList<>(List.of(journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.WARRANTY_PROVISION_EXPENSE, 40_000),
                cr(StandardAccounts.PROVISION_FOR_PRODUCT_WARRANTIES, 40_000))));

        List<Journal> entries = service.replaceProvision(
                ProvisionType.PRODUCT_WARRANTIES, yen(60_000), journals, periodEnd);
        journals.addAll(entries);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.PROVISION_FOR_PRODUCT_WARRANTIES, 40_000),
                cr(StandardAccounts.WARRANTY_PROVISION_REVERSAL, 40_000));
        assertThat(Ledger.of(StandardAccounts.PROVISION_FOR_PRODUCT_WARRANTIES, journals).balance())
                .isEqualTo(yen(60_000));
    }

    /** 退職給付引当金は費用の額を与えて繰り入れる。差額補充で減らそうとすると戻入の科目がないため例外。 */
    @Test
    void retirementBenefits_areAccruedByExpenseAmount() {
        Journal entry = service.accrueRetirementBenefits(yen(150_000), periodEnd);

        assertThat(entry.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.RETIREMENT_BENEFIT_EXPENSE, 150_000),
                cr(StandardAccounts.PROVISION_FOR_RETIREMENT_BENEFITS, 150_000));
        assertThatThrownBy(() -> service.adjustProvision(
                ProvisionType.RETIREMENT_BENEFITS, yen(100_000), List.of(entry), periodEnd))
                .isInstanceOf(AccountingException.class);
    }

    /** 既存の貸倒引当金の差額補充法は、共通部品に移しても同じ結果になる。 */
    @Test
    void allowanceForDoubtfulAccounts_stillWorksThroughTheSharedCalculator() {
        List<Journal> journals = List.of(journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.BAD_DEBT_EXPENSE, 5_000), cr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 5_000)));

        Optional<Journal> entry = service.adjustAllowanceForDoubtfulAccounts(yen(20_000), journals, periodEnd);

        assertThat(entry.orElseThrow().lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.BAD_DEBT_EXPENSE, 15_000),
                cr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 15_000));
    }

    /**
     * 満期保有目的債券(償却原価法・定額法)。額面1,000,000円を970,000円で2026年4月1日に取得、
     * 満期2031年3月31日(60か月)。毎期 30,000 × 12/60 = 6,000円を増額し、満期で額面に一致する。
     */
    @Test
    void amortizeHeldToMaturityBond_reachesFaceValueAtMaturity() {
        HeldToMaturityBond bond = new HeldToMaturityBond(StandardAccounts.HELD_TO_MATURITY_BONDS,
                yen(1_000_000), yen(970_000), LocalDate.of(2026, 4, 1), LocalDate.of(2031, 3, 31));
        List<Journal> journals = new ArrayList<>(List.of(normal(LocalDate.of(2026, 4, 1),
                dr(StandardAccounts.HELD_TO_MATURITY_BONDS, 970_000), cr(StandardAccounts.CASH, 970_000))));

        Journal first = service.amortizeHeldToMaturityBond(bond, journals, periodEnd).orElseThrow();
        assertThat(first.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.HELD_TO_MATURITY_BONDS, 6_000),
                cr(StandardAccounts.INTEREST_ON_SECURITIES, 6_000));

        journals.add(first);
        for (int year = 2028; year <= 2031; year++) {
            journals.add(service.amortizeHeldToMaturityBond(bond, journals, LocalDate.of(year, 3, 31)).orElseThrow());
        }
        assertThat(bond.bookValue(journals)).isEqualTo(yen(1_000_000));
    }

    /** 期中取得(10月1日)なら、初年度は6か月分 30,000 × 6/60 = 3,000円。 */
    @Test
    void amortizeHeldToMaturityBond_isProratedForAMidYearAcquisition() {
        HeldToMaturityBond bond = new HeldToMaturityBond(StandardAccounts.HELD_TO_MATURITY_BONDS,
                yen(1_000_000), yen(970_000), LocalDate.of(2026, 10, 1), LocalDate.of(2031, 9, 30));
        List<Journal> journals = List.of(normal(LocalDate.of(2026, 10, 1),
                dr(StandardAccounts.HELD_TO_MATURITY_BONDS, 970_000), cr(StandardAccounts.CASH, 970_000)));

        assertThat(service.amortizeHeldToMaturityBond(bond, journals, periodEnd).orElseThrow().debitTotal())
                .isEqualTo(yen(3_000));
    }

    /**
     * その他有価証券(全部純資産直入法・洗替方式)。取得原価500,000円、期末の時価530,000円。
     * 評価差額30,000円を純資産に計上し、翌期首の再振替で取得原価に戻る。
     */
    @Test
    void valuateAvailableForSaleSecurities_isReversedAtTheNextPeriodStart() {
        List<Journal> journals = new ArrayList<>(List.of(normal(LocalDate.of(2026, 6, 1),
                dr(StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, 500_000), cr(StandardAccounts.CASH, 500_000))));

        Journal valuation = service.valuateAvailableForSaleSecurities(
                StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, yen(530_000), journals, periodEnd).orElseThrow();
        journals.add(valuation);

        assertThat(valuation.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, 30_000),
                cr(StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES, 30_000));

        journals.addAll(service.reverseAccruals(List.of(valuation), nextPeriodStart));
        assertThat(Ledger.of(StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, journals).balance())
                .isEqualTo(yen(500_000));
        assertThat(Ledger.of(StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES, journals)
                .balance()).isEqualTo(yen(0));
    }

    @Test
    void valuateAvailableForSaleSecurities_recordsADecreaseAsADebitToTheValuationDifference() {
        List<Journal> journals = List.of(normal(LocalDate.of(2026, 6, 1),
                dr(StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, 500_000), cr(StandardAccounts.CASH, 500_000)));

        Journal valuation = service.valuateAvailableForSaleSecurities(
                StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, yen(480_000), journals, periodEnd).orElseThrow();

        assertThat(valuation.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES, 20_000),
                cr(StandardAccounts.AVAILABLE_FOR_SALE_SECURITIES, 20_000));
    }
}
