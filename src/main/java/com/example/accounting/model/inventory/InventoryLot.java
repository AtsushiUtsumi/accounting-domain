package com.example.accounting.model.inventory;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;

/** 商品有高帳の1ロット(数量と単価の組)。受入・払出・残高のいずれの列にも使う。 */
public record InventoryLot(int quantity, Money unitCost) {

    public InventoryLot {
        if (quantity < 0) {
            throw new AccountingException("quantity must not be negative: " + quantity);
        }
        if (unitCost == null) {
            throw new AccountingException("unitCost is required");
        }
    }

    public Money amount() {
        return Money.ofYen(unitCost.amountInYen() * quantity);
    }
}
