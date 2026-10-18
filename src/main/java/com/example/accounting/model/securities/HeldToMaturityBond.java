package com.example.accounting.model.securities;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.MonthCount;
import com.example.accounting.model.statement.Ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 満期保有目的債券。取得価額と額面金額の差(金利調整差額)を、取得から満期までの期間に
 * 月割で配分する(償却原価法・定額法)。帳簿価額は保存せず、債券の勘定の残高から導出する。
 *
 * <p>そのため {@code bondAccount} は、この債券専用の勘定(他の債券と共有しない)を指定すること。
 *
 * @param bondAccount     この債券専用の勘定(満期保有目的債券)
 * @param faceValue       額面金額
 * @param acquisitionCost 取得価額
 * @param acquisitionDate 取得日
 * @param maturityDate    満期日
 */
public record HeldToMaturityBond(
        Account bondAccount,
        Money faceValue,
        Money acquisitionCost,
        LocalDate acquisitionDate,
        LocalDate maturityDate
) {

    public HeldToMaturityBond {
        if (bondAccount == null) {
            throw new AccountingException("bondAccount is required");
        }
        if (faceValue == null || !faceValue.isPositive()) {
            throw new AccountingException("faceValue must be positive");
        }
        if (acquisitionCost == null || !acquisitionCost.isPositive()) {
            throw new AccountingException("acquisitionCost must be positive");
        }
        if (acquisitionDate == null || maturityDate == null || !maturityDate.isAfter(acquisitionDate)) {
            throw new AccountingException("maturityDate must be after acquisitionDate");
        }
    }

    /** 帳簿価額。債券の勘定の残高(取得の仕訳を含むこと)。 */
    public Money bookValue(List<Journal> journalsSoFar) {
        return Ledger.of(bondAccount, journalsSoFar).balance();
    }

    /**
     * {@code asOf} 時点での帳簿価額の調整額(正なら増額、負なら減額)。取得から {@code asOf} までの月数に
     * 応じた金利調整差額の累計から、すでに調整済みの額を引いた残り。満期に達していれば、帳簿価額が額面金額に
     * 一致するように調整する。
     */
    public Money amortizationUpTo(LocalDate asOf, List<Journal> journalsSoFar) {
        Money totalDifference = faceValue.minus(acquisitionCost);
        Money alreadyAmortized = bookValue(journalsSoFar).minus(acquisitionCost);
        if (asOf.isBefore(acquisitionDate)) {
            return Money.ZERO;
        }
        LocalDate until = asOf.isBefore(maturityDate) ? asOf : maturityDate;
        int elapsedMonths = MonthCount.inclusive(acquisitionDate, until);
        int totalMonths = MonthCount.inclusive(acquisitionDate, maturityDate.minusDays(1));

        Money target;
        if (elapsedMonths >= totalMonths) {
            target = totalDifference;
        } else {
            BigDecimal raw = BigDecimal.valueOf(totalDifference.amountInYen())
                    .multiply(BigDecimal.valueOf(elapsedMonths))
                    .divide(BigDecimal.valueOf(totalMonths), 0, RoundingMode.DOWN);
            target = Money.ofYen(raw.longValueExact());
        }
        return target.minus(alreadyAmortized);
    }
}
