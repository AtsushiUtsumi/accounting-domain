package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.Side;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.money.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 間接法キャッシュ・フロー計算書(最小構成)。仕訳一覧から毎回導出する(保存しない)。
 *
 * <p>営業活動は当期純利益に減価償却費を加算し、{@link CashFlowClassifier} で OPERATING と
 * 判定された科目の前期末からの増減を加減して求める。投資・財務活動は、当期の仕訳のうち現金が
 * 絡む行を直接集計する(相手科目の帳簿価額ではなく現金そのものの行の金額を使うため、
 * 固定資産や有価証券の売却損益が混ざっていても正しい受取額・支払額になる)。
 *
 * <p>売却損益の営業活動からの除外、受取利息・配当金、法人税等の支払額、配当の支払いは対象外
 * (最小構成)。
 */
public record CashFlowStatement(
        Money netIncome,
        Money depreciationAddBack,
        Map<Account, Money> operatingWorkingCapitalChanges,
        Money investingActivitiesCashFlow,
        Money financingActivitiesCashFlow,
        Money cashAtBeginningOfPeriod,
        Money cashAtEndOfPeriod
) {

    public CashFlowStatement {
        operatingWorkingCapitalChanges = Map.copyOf(operatingWorkingCapitalChanges);
    }

    public static CashFlowStatement of(
            List<Journal> priorPeriodJournals, List<Journal> currentPeriodJournals, CashFlowClassifier classifier) {

        List<Journal> allJournals = new ArrayList<>(priorPeriodJournals);
        allJournals.addAll(currentPeriodJournals);

        Money netIncome = IncomeStatement.of(currentPeriodJournals).netIncome();
        Money depreciation = Ledger.of(StandardAccounts.DEPRECIATION_EXPENSE, currentPeriodJournals).balance();

        Map<Account, Money> workingCapitalChanges =
                operatingWorkingCapitalChanges(priorPeriodJournals, allJournals, classifier);

        Money investingTotal = Money.ZERO;
        Money financingTotal = Money.ZERO;
        for (Journal journal : currentPeriodJournals) {
            Money cashDelta = cashDeltaOf(journal);
            if (cashDelta.isZero()) {
                continue;
            }
            CashFlowActivity activity = counterpartActivityOf(journal, classifier);
            if (activity == CashFlowActivity.INVESTING) {
                investingTotal = investingTotal.plus(cashDelta);
            } else if (activity == CashFlowActivity.FINANCING) {
                financingTotal = financingTotal.plus(cashDelta);
            }
        }

        Money cashAtBeginning = Ledger.of(StandardAccounts.CASH, priorPeriodJournals).balance();
        Money cashAtEnd = Ledger.of(StandardAccounts.CASH, allJournals).balance();

        return new CashFlowStatement(
                netIncome, depreciation, workingCapitalChanges, investingTotal, financingTotal,
                cashAtBeginning, cashAtEnd);
    }

    private static Map<Account, Money> operatingWorkingCapitalChanges(
            List<Journal> priorPeriodJournals, List<Journal> allJournals, CashFlowClassifier classifier) {

        TrialBalance priorTB = TrialBalance.of(priorPeriodJournals);
        TrialBalance currentTB = TrialBalance.of(allJournals);

        Set<Account> accounts = new LinkedHashSet<>();
        accounts.addAll(priorTB.rows().keySet());
        accounts.addAll(currentTB.rows().keySet());

        Map<Account, Money> changes = new LinkedHashMap<>();
        for (Account account : accounts) {
            if (classifier.classify(account) != CashFlowActivity.OPERATING) {
                continue;
            }
            Money priorBalance = balanceOf(priorTB, account);
            Money currentBalance = balanceOf(currentTB, account);
            Money delta = currentBalance.minus(priorBalance);
            if (delta.isZero()) {
                continue;
            }
            Money cashImpact = account.type().normalSide() == Side.DEBIT ? delta.negate() : delta;
            changes.put(account, cashImpact);
        }
        return changes;
    }

    private static Money balanceOf(TrialBalance trialBalance, Account account) {
        TrialBalance.Row row = trialBalance.rows().get(account);
        return row == null ? Money.ZERO : row.netBalance(account.type());
    }

    private static Money cashDeltaOf(Journal journal) {
        Money cashDelta = Money.ZERO;
        for (JournalLine line : journal.lines()) {
            if (line.account().equals(StandardAccounts.CASH)) {
                cashDelta = cashDelta.plus(line.side() == Side.DEBIT ? line.amount() : line.amount().negate());
            }
        }
        return cashDelta;
    }

    /** 現金以外の行のうち、投資・財務活動に分類される科目があれば、その活動区分を返す(最初に見つかったもの)。 */
    private static CashFlowActivity counterpartActivityOf(Journal journal, CashFlowClassifier classifier) {
        for (JournalLine line : journal.lines()) {
            if (line.account().equals(StandardAccounts.CASH)) {
                continue;
            }
            CashFlowActivity activity = classifier.classify(line.account());
            if (activity == CashFlowActivity.INVESTING || activity == CashFlowActivity.FINANCING) {
                return activity;
            }
        }
        return CashFlowActivity.EXCLUDED;
    }

    public Money operatingActivitiesCashFlow() {
        Money total = netIncome.plus(depreciationAddBack);
        for (Money change : operatingWorkingCapitalChanges.values()) {
            total = total.plus(change);
        }
        return total;
    }

    public Money netChangeInCash() {
        return operatingActivitiesCashFlow().plus(investingActivitiesCashFlow).plus(financingActivitiesCashFlow);
    }

    /** 自己検証: 期首現金 + 計算上の現金増減 = 期末現金(実際のCASH元帳残高)になること。 */
    public boolean reconcilesToEndingCash() {
        return cashAtBeginningOfPeriod.plus(netChangeInCash()).equals(cashAtEndOfPeriod);
    }
}
