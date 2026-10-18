package com.example.accounting.service;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.normal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * フェーズ12・13(その他資本剰余金からの配当・吸収合併・収益認識)。期待値は問題集の解答例ではなく、
 * 一般的なテキストの処理にもとづく(未検証)。
 */
@Tag("unverified")
class EquityAndRevenueServicesTest {

    private final LocalDate date = LocalDate.of(2026, 6, 25);

    /**
     * その他資本剰余金から1,000,000円を配当。資本金10,000,000円、資本準備金1,500,000円、利益準備金500,000円。
     * 積立の余地 = 2,500,000 − 2,000,000 = 500,000円、配当の1/10 = 100,000円 → 資本準備金に100,000円。
     */
    @Test
    void dividendsFromCapitalSurplus_appropriateToCapitalSurplus() {
        Journal dividends = new DividendService().declareDividendsFromCapitalSurplus(
                yen(1_000_000), yen(10_000_000), yen(1_500_000), yen(500_000), date);

        assertThat(dividends.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.OTHER_CAPITAL_SURPLUS, 1_100_000),
                cr(StandardAccounts.DIVIDENDS_PAYABLE, 1_000_000),
                cr(StandardAccounts.CAPITAL_SURPLUS, 100_000));
    }

    /**
     * 吸収合併。売掛金3,000,000円・建物5,000,000円(時価)と買掛金2,000,000円を受け入れ、
     * 資本金4,000,000円・資本準備金2,500,000円を発行。対価6,500,000 − 純資産6,000,000 = のれん500,000円。
     */
    @Test
    void merger_recordsGoodwillForExcessConsideration() {
        Journal merger = new MergerService().merge(
                List.of(dr(StandardAccounts.ACCOUNTS_RECEIVABLE, 3_000_000), dr(StandardAccounts.BUILDINGS, 5_000_000)),
                List.of(cr(StandardAccounts.ACCOUNTS_PAYABLE, 2_000_000)),
                List.of(cr(StandardAccounts.CAPITAL_STOCK, 4_000_000), cr(StandardAccounts.CAPITAL_SURPLUS, 2_500_000)),
                date);

        assertThat(merger.lines()).contains(dr(StandardAccounts.GOODWILL, 500_000));
        assertThat(merger.debitTotal()).isEqualTo(yen(8_500_000));
    }

    @Test
    void merger_recordsGainOnBargainPurchaseForShortConsideration() {
        Journal merger = new MergerService().merge(
                List.of(dr(StandardAccounts.ACCOUNTS_RECEIVABLE, 3_000_000), dr(StandardAccounts.BUILDINGS, 5_000_000)),
                List.of(cr(StandardAccounts.ACCOUNTS_PAYABLE, 2_000_000)),
                List.of(cr(StandardAccounts.CAPITAL_STOCK, 5_800_000)),
                date);

        assertThat(merger.lines()).contains(cr(StandardAccounts.GAIN_ON_BARGAIN_PURCHASE, 200_000));
    }

    /** 売上割戻しが3,000円見込まれる掛売上100,000円。売上97,000円、返金負債3,000円。 */
    @Test
    void saleWithExpectedRefund_splitsSalesAndRefundLiability() {
        Journal sale = new RevenueRecognitionService().recordSaleWithExpectedRefund(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(100_000), yen(3_000), date);

        assertThat(sale.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCOUNTS_RECEIVABLE, 100_000),
                cr(StandardAccounts.SALES, 97_000),
                cr(StandardAccounts.REFUND_LIABILITIES, 3_000));
    }

    /** 商品A(30,000円)を先に引き渡し、B(50,000円)の引渡しで初めて請求できる契約。 */
    @Test
    void contractAsset_isBilledWhenTheContractIsCompleted() {
        RevenueRecognitionService service = new RevenueRecognitionService();
        Journal deliveryOfA = service.recognizeContractAsset(yen(30_000), date);

        Journal deliveryOfB = service.billCompletedContract(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(50_000), List.of(deliveryOfA), date.plusDays(10));

        assertThat(deliveryOfA.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CONTRACT_ASSETS, 30_000), cr(StandardAccounts.SALES, 30_000));
        assertThat(deliveryOfB.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCOUNTS_RECEIVABLE, 80_000),
                cr(StandardAccounts.CONTRACT_ASSETS, 30_000),
                cr(StandardAccounts.SALES, 50_000));
    }

    /** 役務収益200,000円の計上と、仕掛品に集めた費用120,000円の役務原価への振替。 */
    @Test
    void serviceRevenue_movesWorkInProcessToCostOfServices() {
        Journal deferral = normal(date, dr(StandardAccounts.WORK_IN_PROCESS, 120_000), cr(StandardAccounts.SALARIES, 120_000));

        Journal revenue = new RevenueRecognitionService().recognizeServiceRevenue(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(200_000), yen(120_000), date.plusDays(30));

        assertThat(revenue.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCOUNTS_RECEIVABLE, 200_000),
                cr(StandardAccounts.SERVICE_REVENUE, 200_000),
                dr(StandardAccounts.COST_OF_SERVICES, 120_000),
                cr(StandardAccounts.WORK_IN_PROCESS, 120_000));
        assertThat(deferral.debitTotal()).isEqualTo(yen(120_000));
    }
}
