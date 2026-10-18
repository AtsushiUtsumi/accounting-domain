package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.Ledger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 収益認識に関する仕訳(返金負債・契約資産・役務収益)を導出する。保存は行わない。
 *
 * <p>役務の費用を仕掛品に振り替える仕訳、契約負債(前受け)の計上は計算を伴わないため、通常の仕訳で
 * {@code StandardAccounts.WORK_IN_PROCESS}・{@code CONTRACT_LIABILITIES} を直接使えばよい。
 */
public final class RevenueRecognitionService {

    /**
     * 返金が見込まれる販売(売上割戻しなど)。対価の全額を債権とし、返金見込額を返金負債、
     * 残りを売上とする。
     */
    public Journal recordSaleWithExpectedRefund(Account receivableAccount, Money totalConsideration,
                                                Money expectedRefund, LocalDate date) {
        if (receivableAccount == null) {
            throw new AccountingException("receivableAccount is required");
        }
        if (totalConsideration == null || !totalConsideration.isPositive()) {
            throw new AccountingException("totalConsideration must be positive");
        }
        if (expectedRefund == null || expectedRefund.isNegative() || expectedRefund.compareTo(totalConsideration) >= 0) {
            throw new AccountingException("expectedRefund must be between zero and totalConsideration");
        }
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(receivableAccount, totalConsideration));
        lines.add(JournalLine.credit(StandardAccounts.SALES, totalConsideration.minus(expectedRefund)));
        if (expectedRefund.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.REFUND_LIABILITIES, expectedRefund));
        }
        return Journal.of(JournalId.newId(), date, "返金が見込まれる販売", JournalType.NORMAL, lines);
    }

    /**
     * 一部の履行義務を果たしたが、まだ対価を請求できないとき。履行した分の売上を契約資産とともに計上する。
     */
    public Journal recognizeContractAsset(Money revenue, LocalDate date) {
        if (revenue == null || !revenue.isPositive()) {
            throw new AccountingException("revenue must be positive");
        }
        return Journal.of(
                JournalId.newId(), date, "履行義務の充足(契約資産)", JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.CONTRACT_ASSETS, revenue),
                        JournalLine.credit(StandardAccounts.SALES, revenue)));
    }

    /**
     * すべての履行義務を果たし、対価を請求できるようになったとき。契約資産の残高(これまでの履行分)を
     * 債権に振り替え、残りの履行分を売上とする。
     *
     * @param remainingRevenue 今回の履行で認識する売上
     * @param journalsSoFar    契約資産の残高を求めるための、これまでの仕訳(この契約の分だけを渡すこと)
     */
    public Journal billCompletedContract(Account receivableAccount, Money remainingRevenue,
                                         List<Journal> journalsSoFar, LocalDate date) {
        if (receivableAccount == null) {
            throw new AccountingException("receivableAccount is required");
        }
        if (remainingRevenue == null || remainingRevenue.isNegative()) {
            throw new AccountingException("remainingRevenue must not be negative");
        }
        Money contractAsset = Ledger.of(StandardAccounts.CONTRACT_ASSETS, journalsSoFar).balance();
        if (contractAsset.isNegative()) {
            throw new AccountingException("contract asset balance must not be negative");
        }
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(receivableAccount, contractAsset.plus(remainingRevenue)));
        if (contractAsset.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.CONTRACT_ASSETS, contractAsset));
        }
        if (remainingRevenue.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.SALES, remainingRevenue));
        }
        return Journal.of(JournalId.newId(), date, "契約の完了と対価の請求", JournalType.NORMAL, lines);
    }

    /**
     * 役務収益の計上。役務収益を計上し、その役務のために仕掛品へ集めた費用を役務原価へ振り替える。
     *
     * @param costOfServices 仕掛品から役務原価へ振り替える額(この役務の分)
     */
    public Journal recognizeServiceRevenue(Account receivableAccount, Money revenue, Money costOfServices,
                                           LocalDate date) {
        if (receivableAccount == null) {
            throw new AccountingException("receivableAccount is required");
        }
        if (revenue == null || !revenue.isPositive()) {
            throw new AccountingException("revenue must be positive");
        }
        if (costOfServices == null || costOfServices.isNegative()) {
            throw new AccountingException("costOfServices must not be negative");
        }
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(receivableAccount, revenue));
        lines.add(JournalLine.credit(StandardAccounts.SERVICE_REVENUE, revenue));
        if (costOfServices.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.COST_OF_SERVICES, costOfServices));
            lines.add(JournalLine.credit(StandardAccounts.WORK_IN_PROCESS, costOfServices));
        }
        return Journal.of(JournalId.newId(), date, "役務収益の計上", JournalType.NORMAL, lines);
    }
}
