package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 固定資産の割賦購入。現金購入価額で資産を計上し、支払総額との差(利息相当額)を前払利息とする。
 * 支払のつど、利息相当額を支払回数で均等に(定額で)支払利息へ振り替える。保存は行わない。
 */
public final class InstallmentPurchaseService {

    /**
     * 割賦購入の条件。
     *
     * @param assetAccount         取得する資産の勘定
     * @param cashPrice            現金購入価額
     * @param totalPayment         支払総額(手形・未払金の合計)
     * @param numberOfInstallments 支払回数
     * @param payableAccount       支払義務の勘定(営業外支払手形・未払金)
     */
    public record InstallmentPlan(
            Account assetAccount, Money cashPrice, Money totalPayment, int numberOfInstallments,
            Account payableAccount) {

        public InstallmentPlan {
            if (assetAccount == null || payableAccount == null) {
                throw new AccountingException("assetAccount and payableAccount are required");
            }
            if (cashPrice == null || !cashPrice.isPositive()) {
                throw new AccountingException("cashPrice must be positive");
            }
            if (totalPayment == null || totalPayment.compareTo(cashPrice) < 0) {
                throw new AccountingException("totalPayment must not be less than cashPrice");
            }
            if (numberOfInstallments <= 0) {
                throw new AccountingException("numberOfInstallments must be positive");
            }
        }

        /** 利息相当額 = 支払総額 − 現金購入価額。 */
        public Money totalInterest() {
            return totalPayment.minus(cashPrice);
        }

        /** 第 n 回の支払額。割り切れない端数は最終回に含める。 */
        public Money installmentAmount(int installmentNumber) {
            return share(totalPayment, installmentNumber);
        }

        /** 第 n 回の支払に対応する利息(定額法)。割り切れない端数は最終回に含める。 */
        public Money interestFor(int installmentNumber) {
            return share(totalInterest(), installmentNumber);
        }

        private Money share(Money total, int installmentNumber) {
            if (installmentNumber < 1 || installmentNumber > numberOfInstallments) {
                throw new AccountingException("installmentNumber out of range: " + installmentNumber);
            }
            long each = total.amountInYen() / numberOfInstallments;
            if (installmentNumber < numberOfInstallments) {
                return Money.ofYen(each);
            }
            return Money.ofYen(total.amountInYen() - each * (numberOfInstallments - 1));
        }
    }

    /** 購入時。資産を現金購入価額で計上し、利息相当額を前払利息とする。 */
    public Journal purchase(InstallmentPlan plan, LocalDate date) {
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(plan.assetAccount(), plan.cashPrice()));
        if (plan.totalInterest().isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.PREPAID_INTEREST, plan.totalInterest()));
        }
        lines.add(JournalLine.credit(plan.payableAccount(), plan.totalPayment()));
        return Journal.of(JournalId.newId(), date, "固定資産の割賦購入", JournalType.NORMAL, lines);
    }

    /** 第 n 回の支払。支払義務を減らし、その回の利息を前払利息から支払利息へ振り替える。 */
    public Journal payInstallment(InstallmentPlan plan, int installmentNumber, Account paymentAccount, LocalDate date) {
        if (paymentAccount == null) {
            throw new AccountingException("paymentAccount is required");
        }
        Money payment = plan.installmentAmount(installmentNumber);
        Money interest = plan.interestFor(installmentNumber);

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(plan.payableAccount(), payment));
        lines.add(JournalLine.credit(paymentAccount, payment));
        if (interest.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.INTEREST_EXPENSE, interest));
            lines.add(JournalLine.credit(StandardAccounts.PREPAID_INTEREST, interest));
        }
        return Journal.of(
                JournalId.newId(), date, "割賦代金の支払(第" + installmentNumber + "回)", JournalType.NORMAL, lines);
    }
}
