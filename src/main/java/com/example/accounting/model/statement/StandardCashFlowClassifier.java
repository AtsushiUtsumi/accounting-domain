package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;

/**
 * 標準科目に対する既定の分類。現金・損益勘定・現金過不足・備品減価償却累計額(減価償却費の
 * 加算で別途処理する)・繰越利益剰余金(当期純利益の起点と二重計算になる)は対象外。
 * 固定資産・売買目的有価証券は投資活動、借入金・資本金は財務活動、それ以外の流動資産・
 * 流動負債は営業活動とする。
 */
public final class StandardCashFlowClassifier implements CashFlowClassifier {

    @Override
    public CashFlowActivity classify(Account account) {
        if (account.equals(StandardAccounts.CASH)
                || account.equals(StandardAccounts.RETAINED_EARNINGS)
                || account.equals(StandardAccounts.ACCUMULATED_DEPRECIATION_EQUIPMENT)
                || account.equals(StandardAccounts.PROFIT_AND_LOSS)
                || account.equals(StandardAccounts.CASH_OVER_SHORT)) {
            return CashFlowActivity.EXCLUDED;
        }
        if (account.equals(StandardAccounts.EQUIPMENT) || account.equals(StandardAccounts.TRADING_SECURITIES)) {
            return CashFlowActivity.INVESTING;
        }
        if (account.equals(StandardAccounts.LOANS_PAYABLE) || account.equals(StandardAccounts.CAPITAL_STOCK)) {
            return CashFlowActivity.FINANCING;
        }
        return switch (account.category()) {
            case CURRENT_ASSET, CURRENT_LIABILITY -> CashFlowActivity.OPERATING;
            default -> CashFlowActivity.EXCLUDED;
        };
    }
}
