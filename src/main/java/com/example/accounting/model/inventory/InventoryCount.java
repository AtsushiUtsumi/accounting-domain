package com.example.accounting.model.inventory;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;

/**
 * 期末商品の棚卸の結果(1品目分)。帳簿棚卸数量・実地棚卸数量・原価・正味売却価額の組。
 * 棚卸減耗損と商品評価損の計算に使う。
 *
 * @param bookQuantity        帳簿棚卸数量
 * @param actualQuantity      実地棚卸数量
 * @param unitCost            原価(単価)
 * @param netRealizableValue 正味売却価額(単価)
 */
public record InventoryCount(int bookQuantity, int actualQuantity, Money unitCost, Money netRealizableValue) {

    public InventoryCount {
        if (bookQuantity < 0 || actualQuantity < 0) {
            throw new AccountingException("quantities must not be negative");
        }
        if (actualQuantity > bookQuantity) {
            throw new AccountingException("actualQuantity exceeding bookQuantity is out of scope");
        }
        if (unitCost == null || unitCost.isNegative()) {
            throw new AccountingException("unitCost must not be negative");
        }
        if (netRealizableValue == null || netRealizableValue.isNegative()) {
            throw new AccountingException("netRealizableValue must not be negative");
        }
    }

    /** 帳簿棚卸高(帳簿数量 × 原価)。三分法の期末商品棚卸高に使う。 */
    public Money bookAmount() {
        return Money.ofYen(unitCost.amountInYen() * bookQuantity);
    }

    /** 棚卸減耗損 =(帳簿数量 − 実地数量)× 原価。 */
    public Money shrinkageLoss() {
        return Money.ofYen(unitCost.amountInYen() * (bookQuantity - actualQuantity));
    }

    /** 商品評価損 =(原価 − 正味売却価額)× 実地数量。正味売却価額が原価以上ならゼロ。 */
    public Money valuationLoss() {
        long perUnit = unitCost.amountInYen() - netRealizableValue.amountInYen();
        return perUnit > 0 ? Money.ofYen(perUnit * actualQuantity) : Money.ZERO;
    }
}
