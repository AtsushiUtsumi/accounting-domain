package com.example.accounting.model.inventory;

import java.util.List;

/** 払出単価の決め方(先入先出法・移動平均法など)。差し替え可能にするためインターフェースにする。 */
public interface InventoryValuationMethod {

    /** 受入・払出の記録(時系列順)から、商品有高帳の全行を導出する。 */
    List<InventoryLedgerEntry> valuate(List<InventoryMovement> movements);
}
