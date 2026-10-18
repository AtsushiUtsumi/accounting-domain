package com.example.accounting.model.inventory;

import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * 商品有高帳の1行。受入・払出・残高をそれぞれロット(数量・単価)の列として持つ。
 * 先入先出法では、単価の異なる在庫が混在するとき、払出・残高が複数ロットに分かれることがある。
 */
public record InventoryLedgerEntry(
        LocalDate date,
        String description,
        List<InventoryLot> receipts,
        List<InventoryLot> issues,
        List<InventoryLot> balance
) {

    public int balanceQuantity() {
        return balance.stream().mapToInt(InventoryLot::quantity).sum();
    }

    public Money balanceAmount() {
        return balance.stream().map(InventoryLot::amount).reduce(Money.ZERO, Money::plus);
    }

    public Money receiptAmount() {
        return receipts.stream().map(InventoryLot::amount).reduce(Money.ZERO, Money::plus);
    }

    public Money issueAmount() {
        return issues.stream().map(InventoryLot::amount).reduce(Money.ZERO, Money::plus);
    }
}
