package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 債券(公社債)の売買に伴う端数利息。利払日の間に売買したとき、前回の利払日の翌日から売買日までの
 * 利息を、買い手が売り手に支払う。保存は行わない。
 */
public final class BondService {

    /**
     * 端数利息 = 額面金額 × 年利率 × 日数 ÷ 365(円未満切り捨て)。日数は前回の利払日の翌日から売買日まで
     * (売買日を含む)。
     *
     * @param annualRate 年利率(例: 0.0365)
     */
    public Money accruedInterest(Money faceValue, BigDecimal annualRate, LocalDate lastInterestDate,
                                 LocalDate tradeDate) {
        if (faceValue == null || !faceValue.isPositive()) {
            throw new AccountingException("faceValue must be positive");
        }
        if (annualRate == null || annualRate.signum() < 0) {
            throw new AccountingException("annualRate must not be negative");
        }
        if (lastInterestDate == null || tradeDate == null || tradeDate.isBefore(lastInterestDate)) {
            throw new AccountingException("tradeDate must not be before lastInterestDate");
        }
        long days = ChronoUnit.DAYS.between(lastInterestDate, tradeDate);
        BigDecimal raw = BigDecimal.valueOf(faceValue.amountInYen())
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.DOWN);
        return Money.ofYen(raw.longValueExact());
    }

    /**
     * 端数利息を付けた債券の購入。債券は購入価額で計上し、支払った端数利息は有価証券利息の借方
     * (受け取る利息の前払い分の相殺)とする。
     *
     * @param securitiesAccount 債券の勘定(売買目的有価証券・満期保有目的債券など)
     * @param purchasePrice     購入価額(端数利息を含まない)
     * @param accruedInterest   支払う端数利息
     * @param paymentAccount    支払う勘定
     */
    public Journal purchaseWithAccruedInterest(Account securitiesAccount, Money purchasePrice, Money accruedInterest,
                                               Account paymentAccount, LocalDate date) {
        if (securitiesAccount == null || paymentAccount == null) {
            throw new AccountingException("securitiesAccount and paymentAccount are required");
        }
        if (purchasePrice == null || !purchasePrice.isPositive()) {
            throw new AccountingException("purchasePrice must be positive");
        }
        if (accruedInterest == null || accruedInterest.isNegative()) {
            throw new AccountingException("accruedInterest must not be negative");
        }
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(securitiesAccount, purchasePrice));
        if (accruedInterest.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.INTEREST_ON_SECURITIES, accruedInterest));
        }
        lines.add(JournalLine.credit(paymentAccount, purchasePrice.plus(accruedInterest)));
        return Journal.of(JournalId.newId(), date, "債券の購入(端数利息を含む)", JournalType.NORMAL, lines);
    }
}
