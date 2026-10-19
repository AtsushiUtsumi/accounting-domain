package com.example.accounting.model.consolidation;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;

/**
 * 未実現利益。連結会社間で売買した資産が、連結の外にまだ売られずに残っているときの、売った側の利益。
 * 売った側({@link #seller()})が子会社ならアップストリームとして、消去額を非支配株主に按分する。
 */
public sealed interface UnrealizedProfit {

    CompanyId seller();

    /**
     * 棚卸資産に含まれる未実現利益。
     *
     * @param inventory 買った側の棚卸資産の科目(三分法なら繰越商品、売上原価対立法なら商品)
     * @param ending    期末の棚卸資産に含まれる未実現利益
     * @param beginning 期首の棚卸資産に含まれる未実現利益(前期末の未実現利益。当期に実現したとみなす)
     */
    record Inventory(CompanyId seller, Account inventory, Money ending, Money beginning) implements UnrealizedProfit {

        public Inventory {
            if (seller == null || inventory == null) {
                throw new AccountingException("seller and inventory are required");
            }
            if (ending == null || ending.isNegative() || beginning == null || beginning.isNegative()) {
                throw new AccountingException("unrealized profit must not be negative");
            }
        }
    }

    /**
     * 土地の売却で生じた未実現利益(売却益)。買った側が持ち続けている間は毎期消去する。
     *
     * @param saleDate 売却日。当期中なら売却益を消去し、前期以前なら利益剰余金(当期首)を修正する
     */
    record Land(CompanyId seller, Money amount, LocalDate saleDate) implements UnrealizedProfit {

        public Land {
            if (seller == null || saleDate == null) {
                throw new AccountingException("seller and saleDate are required");
            }
            if (amount == null || !amount.isPositive()) {
                throw new AccountingException("amount must be positive");
            }
        }
    }
}
