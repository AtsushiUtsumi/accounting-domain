package com.example.accounting.model.consolidation;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.money.RoundingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子会社。支配獲得時の事実(支配獲得日、親会社の持分比率、子会社株式の取得原価、支配獲得日の子会社の資本)と、
 * のれんの償却年数を持つ。投資と資本の相殺消去の元になる。
 *
 * <p>支配獲得日は子会社の決算日(期末)とする。期中の支配獲得、追加取得・一部売却などの持分の変動は扱わない。
 * 子会社の資産・負債の時価と帳簿価額は等しい(評価差額はない)とする。
 *
 * @param equityAtAcquisition 支配獲得日の子会社の資本(資本金・資本剰余金・利益剰余金の各科目と金額)
 */
public record Subsidiary(
        CompanyId company,
        LocalDate acquisitionDate,
        BigDecimal parentOwnership,
        Money investmentCost,
        Map<Account, Money> equityAtAcquisition,
        int goodwillAmortizationYears
) {

    public Subsidiary {
        if (company == null) {
            throw new AccountingException("company is required");
        }
        if (acquisitionDate == null) {
            throw new AccountingException("acquisitionDate is required");
        }
        if (parentOwnership == null
                || parentOwnership.signum() <= 0 || parentOwnership.compareTo(BigDecimal.ONE) > 0) {
            throw new AccountingException("parentOwnership must be greater than 0 and at most 1: " + parentOwnership);
        }
        if (investmentCost == null || !investmentCost.isPositive()) {
            throw new AccountingException("investmentCost must be positive");
        }
        if (equityAtAcquisition == null || equityAtAcquisition.isEmpty()) {
            throw new AccountingException("equityAtAcquisition is required");
        }
        for (Account account : equityAtAcquisition.keySet()) {
            if (account.type() != AccountType.EQUITY) {
                throw new AccountingException("equityAtAcquisition must contain only equity accounts: " + account.name());
            }
        }
        if (goodwillAmortizationYears <= 0) {
            throw new AccountingException("goodwillAmortizationYears must be positive");
        }
        equityAtAcquisition = Map.copyOf(new LinkedHashMap<>(equityAtAcquisition));
    }

    /** 非支配株主の持分比率(1 − 親会社の持分比率)。 */
    public BigDecimal nonControllingOwnership() {
        return BigDecimal.ONE.subtract(parentOwnership);
    }

    /** 支配獲得日の子会社の資本(純資産)の合計。 */
    public Money netAssetsAtAcquisition() {
        Money total = Money.ZERO;
        for (Money amount : equityAtAcquisition.values()) {
            total = total.plus(amount);
        }
        return total;
    }

    /** 支配獲得日の非支配株主持分 = 子会社の資本 × 非支配株主の持分比率。 */
    public Money nonControllingInterestAtAcquisition(RoundingPolicy rounding) {
        return netAssetsAtAcquisition().times(nonControllingOwnership(), rounding);
    }

    /**
     * のれん = 子会社株式の取得原価 − 子会社の資本のうち親会社の持分。負なら負ののれん。
     * 親会社の持分は「資本 − 非支配株主持分」で求め、端数の食い違いが出ないようにする。
     */
    public Money goodwill(RoundingPolicy rounding) {
        Money parentShare = netAssetsAtAcquisition().minus(nonControllingInterestAtAcquisition(rounding));
        return investmentCost.minus(parentShare);
    }

    /** のれんの1年分の償却額(定額法)。負ののれんは償却しないのでゼロ。 */
    public Money annualGoodwillAmortization(RoundingPolicy rounding) {
        Money goodwill = goodwill(rounding);
        if (!goodwill.isPositive()) {
            return Money.ZERO;
        }
        BigDecimal raw = BigDecimal.valueOf(goodwill.amountInYen())
                .divide(BigDecimal.valueOf(goodwillAmortizationYears), 10, RoundingMode.HALF_UP);
        return rounding.apply(raw);
    }
}
