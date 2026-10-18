package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.account.Side;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.money.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 8桁精算表。決算整理前残高(試算表)・修正記入・損益計算書・貸借対照表の4組の借方・貸方を、
 * 決算整理前の仕訳と決算整理仕訳から毎回導出する(保存しない)。損益振替(締め)はまだ行わない前提
 * なので、損益勘定(PROFIT_AND_LOSS)は対象にならない。
 */
public record Worksheet(Map<Account, Row> rows) {

    private static final TrialBalance.Row ZERO_ROW = new TrialBalance.Row(Money.ZERO, Money.ZERO);

    public record Row(
            Money trialBalanceDebit, Money trialBalanceCredit,
            Money adjustmentDebit, Money adjustmentCredit,
            Money incomeStatementDebit, Money incomeStatementCredit,
            Money balanceSheetDebit, Money balanceSheetCredit
    ) {
    }

    private enum Placement {
        BALANCE_SHEET, INCOME_STATEMENT, NONE
    }

    public Worksheet {
        rows = Map.copyOf(rows);
    }

    public static Worksheet of(List<Journal> journalsBeforeAdjustment, List<Journal> adjustingJournals) {
        TrialBalance preAdjustment = TrialBalance.of(journalsBeforeAdjustment);
        TrialBalance adjustments = TrialBalance.of(adjustingJournals);

        List<Journal> combined = new ArrayList<>(journalsBeforeAdjustment);
        combined.addAll(adjustingJournals);
        TrialBalance postAdjustment = TrialBalance.of(combined);

        Map<Account, Row> rows = new LinkedHashMap<>();
        for (Map.Entry<Account, TrialBalance.Row> entry : postAdjustment.rows().entrySet()) {
            Account account = entry.getKey();
            TrialBalance.Row tb = preAdjustment.rows().getOrDefault(account, ZERO_ROW);
            TrialBalance.Row adj = adjustments.rows().getOrDefault(account, ZERO_ROW);
            Money postBalance = entry.getValue().netBalance(account.type());

            Money plDebit = Money.ZERO;
            Money plCredit = Money.ZERO;
            Money bsDebit = Money.ZERO;
            Money bsCredit = Money.ZERO;

            Placement placement = placementOf(account.category());
            if (placement != Placement.NONE && !postBalance.isZero()) {
                boolean onNormalSide = postBalance.isPositive();
                Side side = onNormalSide ? account.type().normalSide() : account.type().normalSide().opposite();
                Money amount = onNormalSide ? postBalance : postBalance.negate();
                if (placement == Placement.INCOME_STATEMENT) {
                    if (side == Side.DEBIT) {
                        plDebit = amount;
                    } else {
                        plCredit = amount;
                    }
                } else {
                    if (side == Side.DEBIT) {
                        bsDebit = amount;
                    } else {
                        bsCredit = amount;
                    }
                }
            }

            rows.put(account, new Row(
                    tb.debitTotal(), tb.creditTotal(),
                    adj.debitTotal(), adj.creditTotal(),
                    plDebit, plCredit,
                    bsDebit, bsCredit));
        }
        return new Worksheet(rows);
    }

    private static Placement placementOf(AccountCategory category) {
        return switch (category) {
            case CURRENT_ASSET, FIXED_ASSET, CURRENT_LIABILITY, FIXED_LIABILITY, CAPITAL, RETAINED_EARNINGS,
                    VALUATION_AND_TRANSLATION_ADJUSTMENTS ->
                    Placement.BALANCE_SHEET;
            case SALES, COST_OF_SALES, SG_AND_A, NON_OPERATING_INCOME, NON_OPERATING_EXPENSE,
                    EXTRAORDINARY_INCOME, EXTRAORDINARY_LOSS, INCOME_TAX_EXPENSE -> Placement.INCOME_STATEMENT;
            case PROFIT_AND_LOSS, SUSPENSE -> Placement.NONE;
        };
    }

    public Money trialBalanceDebitTotal() {
        return sum(Row::trialBalanceDebit);
    }

    public Money trialBalanceCreditTotal() {
        return sum(Row::trialBalanceCredit);
    }

    public Money adjustmentDebitTotal() {
        return sum(Row::adjustmentDebit);
    }

    public Money adjustmentCreditTotal() {
        return sum(Row::adjustmentCredit);
    }

    public Money incomeStatementDebitTotal() {
        return sum(Row::incomeStatementDebit);
    }

    public Money incomeStatementCreditTotal() {
        return sum(Row::incomeStatementCredit);
    }

    public Money balanceSheetDebitTotal() {
        return sum(Row::balanceSheetDebit);
    }

    public Money balanceSheetCreditTotal() {
        return sum(Row::balanceSheetCredit);
    }

    /** 損益計算書欄の差額から求めた当期純利益(マイナスなら純損失)。 */
    public Money netIncome() {
        return incomeStatementCreditTotal().minus(incomeStatementDebitTotal());
    }

    /**
     * 精算表の自己検証: 損益計算書欄の差額(貸方-借方)と貸借対照表欄の差額(借方-貸方)が一致すること。
     * 一致していれば、どちらの差額も当期純利益(純損失ならマイナス)を表す。
     */
    public boolean isBalanced() {
        Money fromIncomeStatement = incomeStatementCreditTotal().minus(incomeStatementDebitTotal());
        Money fromBalanceSheet = balanceSheetDebitTotal().minus(balanceSheetCreditTotal());
        return fromIncomeStatement.equals(fromBalanceSheet);
    }

    private Money sum(java.util.function.Function<Row, Money> selector) {
        Money total = Money.ZERO;
        for (Row row : rows.values()) {
            total = total.plus(selector.apply(row));
        }
        return total;
    }
}
