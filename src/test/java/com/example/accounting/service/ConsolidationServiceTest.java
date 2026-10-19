package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.consolidation.CompanyId;
import com.example.accounting.model.consolidation.ConsolidatedBalanceSheet;
import com.example.accounting.model.consolidation.IndividualStatements;
import com.example.accounting.model.consolidation.IntercompanyBalance;
import com.example.accounting.model.consolidation.IntercompanyTransaction;
import com.example.accounting.model.consolidation.Subsidiary;
import com.example.accounting.model.consolidation.UnrealizedProfit;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.journal.PostedJournal;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.journal;
import static com.example.accounting.support.Journals.normal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * フェーズ14(連結会計)。期待値は問題集の解答例ではなく、実装者が一般的なテキストの処理にもとづいて
 * 作った数字(未検証)。
 *
 * <p>共通の設定: P社は 2025-03-31 に S社株式の 60% を 3,000,000円で取得して支配を獲得した。
 * 支配獲得日の S社の資本は資本金 3,000,000円・繰越利益剰余金 1,000,000円。のれんは10年で定額償却する。
 * のれん = 3,000,000 − 4,000,000 × 60% = 600,000円、非支配株主持分 = 4,000,000 × 40% = 1,600,000円。
 */
@Tag("unverified")
class ConsolidationServiceTest {

    private static final CompanyId P = CompanyId.newId();
    private static final CompanyId S = CompanyId.newId();
    private static final LocalDate ACQUISITION = LocalDate.of(2025, 3, 31);

    private final ConsolidationService service = new ConsolidationService();

    private static Subsidiary subsidiary() {
        return new Subsidiary(S, ACQUISITION, new BigDecimal("0.6"), yen(3_000_000),
                Map.of(StandardAccounts.CAPITAL_STOCK, yen(3_000_000),
                        StandardAccounts.RETAINED_EARNINGS, yen(1_000_000)),
                10);
    }

    private static Journal opening(LocalDate date, JournalLine... lines) {
        return journal(date, JournalType.OPENING, lines);
    }

    // ---- 支配獲得日 ----

    /** 支配獲得日(当期末)の連結。S社の損益計算書は連結せず、貸借対照表だけを合算する。 */
    @Test
    void atAcquisitionDate_consolidatesBalanceSheetOnly() {
        LocalDate start = LocalDate.of(2024, 4, 1);
        IndividualStatements parent = new IndividualStatements(P, List.of(
                opening(start, dr(StandardAccounts.CASH, 4_500_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 4_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 500_000)),
                normal(start, dr(StandardAccounts.CASH, 500_000), cr(StandardAccounts.SALES, 500_000)),
                normal(ACQUISITION, dr(StandardAccounts.SUBSIDIARY_STOCKS, 3_000_000), cr(StandardAccounts.CASH, 3_000_000))));
        IndividualStatements sub = new IndividualStatements(S, List.of(
                opening(start, dr(StandardAccounts.CASH, 3_500_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 3_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 500_000)),
                normal(start, dr(StandardAccounts.CASH, 500_000), cr(StandardAccounts.SALES, 500_000))));

        ConsolidationResult result = service.consolidate(
                ConsolidationInput.of(ACQUISITION, parent, List.of(subsidiary()), List.of(sub)));

        assertThat(result.adjustments()).hasSize(1);
        assertThat(result.adjustments().getFirst().lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CAPITAL_STOCK, 3_000_000),
                dr(StandardAccounts.RETAINED_EARNINGS, 1_000_000),
                dr(StandardAccounts.GOODWILL, 600_000),
                cr(StandardAccounts.SUBSIDIARY_STOCKS, 3_000_000),
                cr(StandardAccounts.NON_CONTROLLING_INTERESTS, 1_600_000));

        assertThat(result.incomeStatement().incomeStatement().sales()).isEqualTo(yen(500_000));
        ConsolidatedBalanceSheet bs = result.balanceSheet();
        assertThat(bs.assets()).containsOnly(
                Map.entry(StandardAccounts.CASH, yen(6_000_000)), Map.entry(StandardAccounts.GOODWILL, yen(600_000)));
        assertThat(bs.retainedEarnings()).isEqualTo(yen(1_000_000));
        assertThat(bs.nonControllingInterests()).isEqualTo(yen(1_600_000));
        assertThat(bs.isBalanced()).isTrue();
    }

    // ---- 支配獲得後1年目 ----

    /**
     * 2026-03 期。S社: 当期純利益 800,000円、配当 200,000円。P社: 売上 1,000,000円、S社からの受取配当金 120,000円。
     */
    @Test
    void firstYear_amortizesGoodwillAndAllocatesProfitAndDividends() {
        ConsolidationResult result = service.consolidate(firstYearInput());

        assertThat(result.adjustments()).extracting(Journal::description).containsExactly(
                "開始仕訳: 投資と資本の相殺消去",
                "のれんの償却",
                "子会社の当期純利益の非支配株主持分への振替",
                "子会社の配当金の修正");
        assertThat(result.adjustments().get(1).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.GOODWILL_AMORTIZATION, 60_000), cr(StandardAccounts.GOODWILL, 60_000));
        assertThat(result.adjustments().get(2).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS, 320_000),
                cr(StandardAccounts.NON_CONTROLLING_INTERESTS, 320_000));
        assertThat(result.adjustments().get(3).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.DIVIDEND_INCOME, 120_000),
                dr(StandardAccounts.NON_CONTROLLING_INTERESTS, 80_000),
                cr(StandardAccounts.RETAINED_EARNINGS, 200_000));

        assertThat(result.incomeStatement().netIncome()).isEqualTo(yen(1_740_000));
        assertThat(result.incomeStatement().profitAttributableToNonControllingInterests()).isEqualTo(yen(320_000));
        assertThat(result.incomeStatement().profitAttributableToOwnersOfParent()).isEqualTo(yen(1_420_000));

        ConsolidatedBalanceSheet bs = result.balanceSheet();
        assertThat(bs.totalAssets()).isEqualTo(yen(8_260_000));
        assertThat(bs.assets()).containsEntry(StandardAccounts.GOODWILL, yen(540_000))
                .doesNotContainKey(StandardAccounts.SUBSIDIARY_STOCKS);
        assertThat(bs.retainedEarnings()).isEqualTo(yen(2_420_000));
        // 非支配株主持分 = S社の期末の資本 4,600,000 × 40%
        assertThat(bs.nonControllingInterests()).isEqualTo(yen(1_840_000));
        assertThat(bs.isBalanced()).isTrue();
        assertThat(result.worksheet().isAdjustmentBalanced()).isTrue();
    }

    private static ConsolidationInput firstYearInput() {
        LocalDate start = LocalDate.of(2025, 4, 1);
        IndividualStatements parent = new IndividualStatements(P, List.of(
                opening(start, dr(StandardAccounts.CASH, 2_000_000), dr(StandardAccounts.SUBSIDIARY_STOCKS, 3_000_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 4_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 1_000_000)),
                normal(start, dr(StandardAccounts.CASH, 1_000_000), cr(StandardAccounts.SALES, 1_000_000)),
                normal(start, dr(StandardAccounts.CASH, 120_000), cr(StandardAccounts.DIVIDEND_INCOME, 120_000))));
        IndividualStatements sub = new IndividualStatements(S, List.of(
                opening(start, dr(StandardAccounts.CASH, 4_000_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 3_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 1_000_000)),
                normal(start, dr(StandardAccounts.CASH, 800_000), cr(StandardAccounts.SALES, 800_000)),
                normal(start, dr(StandardAccounts.RETAINED_EARNINGS, 200_000), cr(StandardAccounts.CASH, 200_000))));
        return ConsolidationInput.of(LocalDate.of(2026, 3, 31), parent, List.of(subsidiary()), List.of(sub));
    }

    // ---- 支配獲得後2年目(開始仕訳) ----

    /**
     * 2027-03 期。前期以前の連結修正を開始仕訳で作り直す: のれん償却 60,000円、
     * S社の利益剰余金の増加 (1,600,000 − 1,000,000) × 40% = 240,000円を非支配株主持分へ。
     */
    @Test
    void secondYear_recreatesPriorAdjustmentsAsOpeningEntries() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        IndividualStatements parent = new IndividualStatements(P, List.of(
                opening(start, dr(StandardAccounts.CASH, 3_120_000), dr(StandardAccounts.SUBSIDIARY_STOCKS, 3_000_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 4_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 2_120_000)),
                normal(start, dr(StandardAccounts.CASH, 1_000_000), cr(StandardAccounts.SALES, 1_000_000))));
        IndividualStatements sub = new IndividualStatements(S, List.of(
                opening(start, dr(StandardAccounts.CASH, 4_600_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 3_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 1_600_000)),
                normal(start, dr(StandardAccounts.CASH, 500_000), cr(StandardAccounts.SALES, 500_000))));

        ConsolidationResult result = service.consolidate(
                ConsolidationInput.of(LocalDate.of(2027, 3, 31), parent, List.of(subsidiary()), List.of(sub)));

        assertThat(result.adjustments()).extracting(Journal::description).containsExactly(
                "開始仕訳: 投資と資本の相殺消去",
                "開始仕訳: 前期以前ののれん償却",
                "開始仕訳: 前期以前の子会社の利益剰余金の増減の非支配株主持分への振替",
                "のれんの償却",
                "子会社の当期純利益の非支配株主持分への振替");
        assertThat(result.adjustments().get(1).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.RETAINED_EARNINGS, 60_000), cr(StandardAccounts.GOODWILL, 60_000));
        assertThat(result.adjustments().get(2).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.RETAINED_EARNINGS, 240_000), cr(StandardAccounts.NON_CONTROLLING_INTERESTS, 240_000));

        assertThat(result.incomeStatement().profitAttributableToOwnersOfParent()).isEqualTo(yen(1_240_000));
        ConsolidatedBalanceSheet bs = result.balanceSheet();
        assertThat(bs.assets()).containsEntry(StandardAccounts.GOODWILL, yen(480_000));
        assertThat(bs.nonControllingInterests()).isEqualTo(yen(2_040_000));
        // 期首の連結利益剰余金 2,420,000(前期の連結 B/S と一致)+ 親会社株主に帰属する当期純利益 1,240,000
        assertThat(bs.retainedEarnings()).isEqualTo(yen(3_660_000));
        assertThat(bs.isBalanced()).isTrue();
    }

    // ---- 成果連結 ----

    @Test
    void eliminatesIntercompanyBalancesTransactionsAndUnrealizedProfits() {
        ConsolidationInput input = firstYearInput()
                .withIntercompanyTransactions(List.of(
                        new IntercompanyTransaction(StandardAccounts.SALES, StandardAccounts.PURCHASES, yen(400_000))))
                .withIntercompanyBalances(List.of(
                        IntercompanyBalance.of(P, StandardAccounts.ACCOUNTS_RECEIVABLE, StandardAccounts.ACCOUNTS_PAYABLE,
                                yen(150_000)).withAllowance(yen(3_000), Money.ZERO)))
                .withUnrealizedProfits(List.of(
                        new UnrealizedProfit.Inventory(P, StandardAccounts.MERCHANDISE, yen(20_000), Money.ZERO),
                        new UnrealizedProfit.Inventory(S, StandardAccounts.MERCHANDISE, Money.ZERO, yen(10_000)),
                        new UnrealizedProfit.Land(S, yen(50_000), LocalDate.of(2025, 10, 1))));

        ConsolidationResult result = service.consolidate(input);
        List<JournalLine> lines = result.adjustments().stream().flatMap(j -> j.lines().stream()).toList();

        assertThat(lines).contains(
                // 取引高
                dr(StandardAccounts.SALES, 400_000), cr(StandardAccounts.PURCHASES, 400_000),
                // 債権債務と貸倒引当金(ダウンストリームなので按分しない)
                dr(StandardAccounts.ACCOUNTS_PAYABLE, 150_000), cr(StandardAccounts.ACCOUNTS_RECEIVABLE, 150_000),
                dr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 3_000), cr(StandardAccounts.BAD_DEBT_EXPENSE, 3_000),
                // 期末棚卸(ダウンストリーム)
                dr(StandardAccounts.COST_OF_GOODS_SOLD, 20_000), cr(StandardAccounts.MERCHANDISE, 20_000),
                // 期首棚卸(アップストリーム): 開始仕訳・実現・按分
                dr(StandardAccounts.RETAINED_EARNINGS, 10_000), cr(StandardAccounts.MERCHANDISE, 10_000),
                dr(StandardAccounts.MERCHANDISE, 10_000), cr(StandardAccounts.COST_OF_GOODS_SOLD, 10_000),
                dr(StandardAccounts.NON_CONTROLLING_INTERESTS, 4_000), cr(StandardAccounts.RETAINED_EARNINGS, 4_000),
                dr(StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS, 4_000),
                cr(StandardAccounts.NON_CONTROLLING_INTERESTS, 4_000),
                // 土地(アップストリーム、当期売却)
                dr(StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS, 50_000), cr(StandardAccounts.LAND, 50_000),
                dr(StandardAccounts.NON_CONTROLLING_INTERESTS, 20_000),
                cr(StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS, 20_000));

        // 320,000(S社の利益の40%)+ 4,000(期首棚卸の実現)− 20,000(土地の売却益の消去)
        assertThat(result.incomeStatement().profitAttributableToNonControllingInterests()).isEqualTo(yen(304_000));
        assertThat(result.balanceSheet().isBalanced()).isTrue();
        assertThat(result.worksheet().isAdjustmentBalanced()).isTrue();
    }

    /** アップストリームの債権の貸倒引当金: 前期分は開始仕訳、当期の増加分は貸倒引当金繰入の修正。 */
    @Test
    void upstreamAllowanceAdjustment_isAllocatedToNonControllingInterests() {
        ConsolidationInput input = firstYearInput().withIntercompanyBalances(List.of(
                IntercompanyBalance.of(S, StandardAccounts.ACCOUNTS_RECEIVABLE, StandardAccounts.ACCOUNTS_PAYABLE,
                        yen(200_000)).withAllowance(yen(4_000), yen(1_000))));

        List<JournalLine> lines = service.consolidate(input).adjustments().stream()
                .flatMap(j -> j.lines().stream()).toList();

        assertThat(lines).contains(
                dr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 1_000), cr(StandardAccounts.RETAINED_EARNINGS, 1_000),
                dr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 3_000), cr(StandardAccounts.BAD_DEBT_EXPENSE, 3_000),
                dr(StandardAccounts.RETAINED_EARNINGS, 400), cr(StandardAccounts.NON_CONTROLLING_INTERESTS, 400),
                dr(StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS, 1_200),
                cr(StandardAccounts.NON_CONTROLLING_INTERESTS, 1_200));
    }

    // ---- その他 ----

    /** 取得原価が親会社持分より少ない(負ののれん)。支配獲得日は負ののれん発生益。 */
    @Test
    void negativeGoodwill_isGainAtAcquisition() {
        Subsidiary cheap = new Subsidiary(S, ACQUISITION, new BigDecimal("0.6"), yen(2_000_000),
                Map.of(StandardAccounts.CAPITAL_STOCK, yen(3_000_000), StandardAccounts.RETAINED_EARNINGS, yen(1_000_000)),
                10);
        LocalDate start = LocalDate.of(2024, 4, 1);
        IndividualStatements parent = new IndividualStatements(P, List.of(
                opening(start, dr(StandardAccounts.CASH, 2_000_000), dr(StandardAccounts.SUBSIDIARY_STOCKS, 2_000_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 4_000_000))));
        IndividualStatements sub = new IndividualStatements(S, List.of(
                opening(start, dr(StandardAccounts.CASH, 4_000_000),
                        cr(StandardAccounts.CAPITAL_STOCK, 3_000_000), cr(StandardAccounts.RETAINED_EARNINGS, 1_000_000))));

        ConsolidationResult result = service.consolidate(
                ConsolidationInput.of(ACQUISITION, parent, List.of(cheap), List.of(sub)));

        assertThat(result.adjustments().getFirst().lines())
                .contains(cr(StandardAccounts.GAIN_ON_BARGAIN_PURCHASE, 400_000))
                .doesNotContain(dr(StandardAccounts.GOODWILL, 400_000));
        assertThat(result.balanceSheet().isBalanced()).isTrue();
    }

    @Test
    void consolidationJournalCannotBePostedToIndividualBooks() {
        Journal adjustment = service.consolidate(firstYearInput()).adjustments().getFirst();
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2025, 4, 1));

        assertThatThrownBy(() -> PostedJournal.post(period, adjustment)).isInstanceOf(AccountingException.class);
    }

    @Test
    void rejectsAcquisitionInTheMiddleOfAFiscalYear() {
        Subsidiary midYear = new Subsidiary(S, LocalDate.of(2024, 9, 30), new BigDecimal("0.6"), yen(3_000_000),
                Map.of(StandardAccounts.CAPITAL_STOCK, yen(4_000_000)), 10);
        ConsolidationInput input = firstYearInput();
        ConsolidationInput invalid = ConsolidationInput.of(input.fiscalYearEnd(), input.parent(),
                List.of(midYear), input.subsidiaryStatements());

        assertThatThrownBy(() -> service.consolidate(invalid)).isInstanceOf(AccountingException.class);
    }

    @Test
    void rejectsUnrealizedProfitOfNonConsolidatedCompany() {
        ConsolidationInput input = firstYearInput().withUnrealizedProfits(List.of(
                new UnrealizedProfit.Land(CompanyId.newId(), yen(10_000), LocalDate.of(2025, 10, 1))));

        assertThatThrownBy(() -> service.consolidate(input)).isInstanceOf(AccountingException.class);
    }

    @Test
    void individualStatementsMustBeBeforeClosing() {
        Account pl = StandardAccounts.PROFIT_AND_LOSS;
        Journal closing = journal(LocalDate.of(2026, 3, 31), JournalType.CLOSING,
                dr(StandardAccounts.SALES, 100), cr(pl, 100));

        assertThatThrownBy(() -> new IndividualStatements(P, List.of(closing))).isInstanceOf(AccountingException.class);
    }
}
