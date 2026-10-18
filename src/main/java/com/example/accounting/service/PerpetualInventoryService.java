package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.inventory.InventoryLedgerEntry;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * 売上原価対立法。販売のつど、売上と同時に商品の原価を商品から売上原価へ振り替える。保存は行わない。
 *
 * <p>三分法(仕入・繰越商品)とは混在させないこと。対立法では決算で {@link AdjustingEntryService#recognizeCostOfSales}
 * を使わず、棚卸減耗損・商品評価損は {@code inventoryAccount} に {@link StandardAccounts#MERCHANDISE} を指定して計上する。
 *
 * <p>仕入は計算を伴わないため、通常の仕訳で {@link StandardAccounts#MERCHANDISE} を借方に記録すればよい。
 */
public final class PerpetualInventoryService {

    /** 販売。払出の原価は商品有高帳の払出行({@link InventoryLedgerEntry#issueAmount()})を使う。 */
    public Journal recordSale(Account receivableAccount, Money salesAmount, InventoryLedgerEntry issueEntry,
                              LocalDate date) {
        if (issueEntry == null) {
            throw new AccountingException("issueEntry is required");
        }
        return recordSale(receivableAccount, salesAmount, issueEntry.issueAmount(), date);
    }

    /** 販売。売上を計上し、払い出した商品の原価を売上原価へ振り替える。 */
    public Journal recordSale(Account receivableAccount, Money salesAmount, Money costOfGoodsSold, LocalDate date) {
        validate(receivableAccount, salesAmount, costOfGoodsSold);
        return Journal.of(
                JournalId.newId(), date, "商品の販売(売上原価対立法)", JournalType.NORMAL,
                List.of(
                        JournalLine.debit(receivableAccount, salesAmount),
                        JournalLine.credit(StandardAccounts.SALES, salesAmount),
                        JournalLine.debit(StandardAccounts.COST_OF_GOODS_SOLD, costOfGoodsSold),
                        JournalLine.credit(StandardAccounts.MERCHANDISE, costOfGoodsSold)));
    }

    /** 売上の返品(戻り)。売上を取り消し、戻ってきた商品の原価を売上原価から商品へ戻す。 */
    public Journal recordSalesReturn(Account receivableAccount, Money salesAmount, Money costOfReturnedGoods,
                                     LocalDate date) {
        validate(receivableAccount, salesAmount, costOfReturnedGoods);
        return Journal.of(
                JournalId.newId(), date, "売上の返品(売上原価対立法)", JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.SALES, salesAmount),
                        JournalLine.credit(receivableAccount, salesAmount),
                        JournalLine.debit(StandardAccounts.MERCHANDISE, costOfReturnedGoods),
                        JournalLine.credit(StandardAccounts.COST_OF_GOODS_SOLD, costOfReturnedGoods)));
    }

    private static void validate(Account receivableAccount, Money salesAmount, Money cost) {
        if (receivableAccount == null) {
            throw new AccountingException("receivableAccount is required");
        }
        if (salesAmount == null || !salesAmount.isPositive()) {
            throw new AccountingException("salesAmount must be positive");
        }
        if (cost == null || !cost.isPositive()) {
            throw new AccountingException("cost must be positive");
        }
    }
}
