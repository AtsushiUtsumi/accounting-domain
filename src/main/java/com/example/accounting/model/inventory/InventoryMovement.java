package com.example.accounting.model.inventory;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;

/**
 * 商品有高帳への1件の出入り(受入または払出)。{@code Journal} とは独立した、商品有高帳専用の入力。
 * ここから仕訳は生成しない(連携させるかどうかは利用側の責務)。
 */
public sealed interface InventoryMovement {

    LocalDate date();

    String description();

    int quantity();

    /** 受入(仕入・前期繰越など)。単価は呼び出し側が与える。 */
    record Receipt(LocalDate date, String description, int quantity, Money unitCost) implements InventoryMovement {
        public Receipt {
            if (date == null) {
                throw new AccountingException("date is required");
            }
            if (quantity <= 0) {
                throw new AccountingException("quantity must be positive: " + quantity);
            }
            if (unitCost == null || !unitCost.isPositive()) {
                throw new AccountingException("unitCost must be positive");
            }
        }
    }

    /** 払出(売上など)。単価は {@link InventoryValuationMethod}(先入先出法・移動平均法)が決める。 */
    record Issue(LocalDate date, String description, int quantity) implements InventoryMovement {
        public Issue {
            if (date == null) {
                throw new AccountingException("date is required");
            }
            if (quantity <= 0) {
                throw new AccountingException("quantity must be positive: " + quantity);
            }
        }
    }
}
