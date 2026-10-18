package com.example.accounting.service;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.inventory.InventoryLedgerEntry;
import com.example.accounting.model.inventory.InventoryLot;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
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

/**
 * フェーズ7〜11の期中取引(引当金の取崩し・売上原価対立法・割賦購入・債券の端数利息・有価証券の一部売却・
 * 電子記録債権の割引)。期待値は問題集の解答例ではなく、一般的なテキストの処理にもとづく(未検証)。
 */
@Tag("unverified")
class TransactionServicesExtendedTest {

    private final LocalDate date = LocalDate.of(2026, 7, 10);

    /** 賞与350,000円を当座預金で支給。賞与引当金300,000円を充当し、不足50,000円は賞与。 */
    @Test
    void provisionUse_coversUpToTheBalanceAndExpensesTheRest() {
        List<Journal> journals = List.of(journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.BONUS_PROVISION_EXPENSE, 300_000), cr(StandardAccounts.PROVISION_FOR_BONUSES, 300_000)));

        Journal use = new ProvisionService().use(
                ProvisionType.BONUSES, yen(350_000), StandardAccounts.CHECKING_ACCOUNT, journals, date);

        assertThat(use.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.PROVISION_FOR_BONUSES, 300_000),
                dr(StandardAccounts.BONUSES, 50_000),
                cr(StandardAccounts.CHECKING_ACCOUNT, 350_000));
    }

    /** 売上原価対立法。原価30,000円(@3,000 × 10個)の商品を50,000円で掛け販売。 */
    @Test
    void perpetualInventory_saleMovesCostToCostOfGoodsSold() {
        InventoryLedgerEntry issue = new InventoryLedgerEntry(date, "払出", List.of(),
                List.of(new InventoryLot(10, yen(3_000))), List.of());

        Journal sale = new PerpetualInventoryService().recordSale(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(50_000), issue, date);

        assertThat(sale.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ACCOUNTS_RECEIVABLE, 50_000),
                cr(StandardAccounts.SALES, 50_000),
                dr(StandardAccounts.COST_OF_GOODS_SOLD, 30_000),
                cr(StandardAccounts.MERCHANDISE, 30_000));
    }

    @Test
    void perpetualInventory_salesReturnReversesSaleAndCost() {
        Journal salesReturn = new PerpetualInventoryService().recordSalesReturn(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(5_000), yen(3_000), date);

        assertThat(salesReturn.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.SALES, 5_000),
                cr(StandardAccounts.ACCOUNTS_RECEIVABLE, 5_000),
                dr(StandardAccounts.MERCHANDISE, 3_000),
                cr(StandardAccounts.COST_OF_GOODS_SOLD, 3_000));
    }

    /**
     * 割賦購入。備品(現金購入価額480,000円)を、営業外支払手形4枚(各125,000円、計500,000円)で購入。
     * 利息相当額20,000円を前払利息とし、支払のつど5,000円を支払利息へ振り替える。
     */
    @Test
    void installmentPurchase_spreadsInterestEvenly() {
        InstallmentPurchaseService service = new InstallmentPurchaseService();
        InstallmentPurchaseService.InstallmentPlan plan = new InstallmentPurchaseService.InstallmentPlan(
                StandardAccounts.EQUIPMENT, yen(480_000), yen(500_000), 4, StandardAccounts.NON_TRADE_NOTES_PAYABLE);

        Journal purchase = service.purchase(plan, date);
        assertThat(purchase.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.EQUIPMENT, 480_000),
                dr(StandardAccounts.PREPAID_INTEREST, 20_000),
                cr(StandardAccounts.NON_TRADE_NOTES_PAYABLE, 500_000));

        List<Journal> journals = new ArrayList<>(List.of(purchase));
        for (int n = 1; n <= 4; n++) {
            journals.add(service.payInstallment(plan, n, StandardAccounts.CHECKING_ACCOUNT, date.plusMonths(n)));
        }
        assertThat(journals.get(1).lines()).contains(
                dr(StandardAccounts.NON_TRADE_NOTES_PAYABLE, 125_000),
                dr(StandardAccounts.INTEREST_EXPENSE, 5_000));
        assertThat(Ledger.of(StandardAccounts.PREPAID_INTEREST, journals).balance()).isEqualTo(yen(0));
        assertThat(Ledger.of(StandardAccounts.NON_TRADE_NOTES_PAYABLE, journals).balance()).isEqualTo(yen(0));
        assertThat(Ledger.of(StandardAccounts.INTEREST_EXPENSE, journals).balance()).isEqualTo(yen(20_000));
    }

    @Test
    void installmentPlan_putsTheRemainderInTheLastInstallment() {
        InstallmentPurchaseService.InstallmentPlan plan = new InstallmentPurchaseService.InstallmentPlan(
                StandardAccounts.EQUIPMENT, yen(90_000), yen(100_000), 3, StandardAccounts.OTHER_PAYABLES);

        assertThat(plan.installmentAmount(1)).isEqualTo(yen(33_333));
        assertThat(plan.installmentAmount(3)).isEqualTo(yen(33_334));
        assertThat(plan.interestFor(3)).isEqualTo(yen(3_334));
    }

    /** 端数利息。額面1,000,000円・年利7.3%、前回利払日6月30日、売買日8月12日(43日)。73,000 × 43/365 = 8,600円。 */
    @Test
    void bond_accruedInterestIsDayCountedOver365() {
        assertThat(new BondService().accruedInterest(yen(1_000_000), new BigDecimal("0.073"),
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 8, 12))).isEqualTo(yen(8_600));
    }

    @Test
    void bond_purchaseWithAccruedInterestDebitsInterestOnSecurities() {
        Journal purchase = new BondService().purchaseWithAccruedInterest(
                StandardAccounts.TRADING_SECURITIES, yen(980_000), yen(8_600), StandardAccounts.CHECKING_ACCOUNT, date);

        assertThat(purchase.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.TRADING_SECURITIES, 980_000),
                dr(StandardAccounts.INTEREST_ON_SECURITIES, 8_600),
                cr(StandardAccounts.CHECKING_ACCOUNT, 988_600));
    }

    /** 株式300株(帳簿960,000円)のうち100株を350,000円で売却。帳簿価額320,000円、売却益30,000円。 */
    @Test
    void tradingSecurities_sellPortionUsesTheAverageCost() {
        List<Journal> journals = List.of(normal(LocalDate.of(2026, 5, 1),
                dr(StandardAccounts.TRADING_SECURITIES, 960_000), cr(StandardAccounts.CASH, 960_000)));

        Journal sale = new TradingSecuritiesService().sellPortion(
                StandardAccounts.CASH, yen(350_000), 100, 300, journals, date);

        assertThat(sale.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CASH, 350_000),
                cr(StandardAccounts.TRADING_SECURITIES, 320_000),
                cr(StandardAccounts.GAIN_ON_SALE_OF_SECURITIES, 30_000));
    }

    /** 売買目的の債券(帳簿980,000円)を990,000円 + 端数利息8,600円で売却。 */
    @Test
    void tradingSecurities_sellBondWithAccruedInterest() {
        List<Journal> journals = List.of(normal(LocalDate.of(2026, 5, 1),
                dr(StandardAccounts.TRADING_SECURITIES, 980_000), cr(StandardAccounts.CASH, 980_000)));

        Journal sale = new TradingSecuritiesService().sellPortion(
                StandardAccounts.CASH, yen(990_000), yen(8_600), 1, 1, journals, date);

        assertThat(sale.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CASH, 998_600),
                cr(StandardAccounts.TRADING_SECURITIES, 980_000),
                cr(StandardAccounts.GAIN_ON_SALE_OF_SECURITIES, 10_000),
                cr(StandardAccounts.INTEREST_ON_SECURITIES, 8_600));
    }

    /** 電子記録債権200,000円を割り引き、割引料2,000円を差し引いた額が当座預金に入金。 */
    @Test
    void electronicReceivable_discountUsesItsOwnLossAccount() {
        Journal discount = new NotesReceivableService().discountElectronicReceivable(
                StandardAccounts.CHECKING_ACCOUNT, yen(200_000), yen(2_000), date);

        assertThat(discount.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CHECKING_ACCOUNT, 198_000),
                dr(StandardAccounts.LOSS_ON_SALE_OF_ELECTRONIC_RECEIVABLES, 2_000),
                cr(StandardAccounts.ELECTRONICALLY_RECORDED_RECEIVABLES, 200_000));
    }
}
