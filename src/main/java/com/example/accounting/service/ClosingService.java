package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.account.AccountType;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.journal.PostedJournal;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.statement.TrialBalance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 決算の損益振替・開始仕訳を導出し、記帳する。保存は行わない(呼び出し側が {@link ClosingResult} を保存する)。
 *
 * <p>決算整理仕訳は、{@link #close} を呼ぶ前に記帳済みである前提。渡す {@code postedJournals} には
 * 当期に記帳した仕訳(開始仕訳・通常仕訳・決算整理仕訳)をすべて含めること。
 *
 * <p>損益振替仕訳は締める前の当期に、開始仕訳は次期に記帳した状態で返す。締めた後の期には記帳できないため、
 * この順序をこのクラスの中で固定している。
 */
public final class ClosingService {

    public ClosingResult close(AccountingPeriod period, List<PostedJournal> postedJournals) {
        if (period == null) {
            throw new AccountingException("period is required");
        }
        if (!period.isOpen()) {
            throw new AccountingException("period is not open: " + period.id().value());
        }
        for (PostedJournal posted : postedJournals) {
            if (!posted.periodId().equals(period.id())) {
                throw new AccountingException("journal " + posted.id().value() + " is not posted to this period");
            }
        }
        List<Journal> journals = PostedJournal.journalsOf(postedJournals);

        TrialBalance trialBalance = TrialBalance.of(journals);
        LocalDate closingDate = period.endDate();

        List<JournalLine> revenueCloseLines = new ArrayList<>();
        Money revenueTotal = Money.ZERO;
        List<JournalLine> expenseCloseLines = new ArrayList<>();
        Money expenseTotal = Money.ZERO;

        for (Map.Entry<Account, TrialBalance.Row> entry : trialBalance.rows().entrySet()) {
            Account account = entry.getKey();
            Money balance = entry.getValue().netBalance(account.type());
            if (balance.isZero()) {
                continue;
            }
            if (account.type() == AccountType.REVENUE) {
                revenueCloseLines.add(JournalLine.debit(account, balance));
                revenueTotal = revenueTotal.plus(balance);
            } else if (account.type() == AccountType.EXPENSE) {
                expenseCloseLines.add(JournalLine.credit(account, balance));
                expenseTotal = expenseTotal.plus(balance);
            }
        }

        List<Journal> closingEntries = new ArrayList<>();

        if (!revenueTotal.isZero()) {
            List<JournalLine> lines = new ArrayList<>(revenueCloseLines);
            lines.add(JournalLine.credit(StandardAccounts.PROFIT_AND_LOSS, revenueTotal));
            closingEntries.add(Journal.of(
                    JournalId.newId(), closingDate, "収益の損益振替", JournalType.CLOSING, lines));
        }

        if (!expenseTotal.isZero()) {
            List<JournalLine> lines = new ArrayList<>();
            lines.add(JournalLine.debit(StandardAccounts.PROFIT_AND_LOSS, expenseTotal));
            lines.addAll(expenseCloseLines);
            closingEntries.add(Journal.of(
                    JournalId.newId(), closingDate, "費用の損益振替", JournalType.CLOSING, lines));
        }

        Money netIncome = revenueTotal.minus(expenseTotal);
        if (!netIncome.isZero()) {
            if (netIncome.isPositive()) {
                closingEntries.add(Journal.of(
                        JournalId.newId(), closingDate, "当期純利益の繰越利益剰余金への振替", JournalType.CLOSING,
                        List.of(
                                JournalLine.debit(StandardAccounts.PROFIT_AND_LOSS, netIncome),
                                JournalLine.credit(StandardAccounts.RETAINED_EARNINGS, netIncome))));
            } else {
                Money loss = netIncome.negate();
                closingEntries.add(Journal.of(
                        JournalId.newId(), closingDate, "当期純損失の繰越利益剰余金への振替", JournalType.CLOSING,
                        List.of(
                                JournalLine.debit(StandardAccounts.RETAINED_EARNINGS, loss),
                                JournalLine.credit(StandardAccounts.PROFIT_AND_LOSS, loss))));
            }
        }

        List<Journal> journalsAfterClosing = new ArrayList<>(journals);
        journalsAfterClosing.addAll(closingEntries);
        TrialBalance trialBalanceAfterClosing = TrialBalance.of(journalsAfterClosing);

        AccountingPeriod closedPeriod = period.close();
        AccountingPeriod nextPeriod = period.nextPeriod();

        List<JournalLine> openingLines = new ArrayList<>();
        for (Map.Entry<Account, TrialBalance.Row> entry : trialBalanceAfterClosing.rows().entrySet()) {
            Account account = entry.getKey();
            AccountType type = account.type();
            if (type == AccountType.REVENUE || type == AccountType.EXPENSE) {
                continue;
            }
            if (account.category() == AccountCategory.PROFIT_AND_LOSS) {
                continue;
            }
            Money balance = entry.getValue().netBalance(type);
            if (balance.isZero()) {
                continue;
            }
            if (balance.isPositive()) {
                openingLines.add(new JournalLine(account, type.normalSide(), balance));
            } else {
                openingLines.add(new JournalLine(account, type.normalSide().opposite(), balance.negate()));
            }
        }

        List<Journal> openingEntries = openingLines.isEmpty()
                ? List.of()
                : List.of(Journal.of(
                        JournalId.newId(), nextPeriod.startDate(), "開始仕訳", JournalType.OPENING, openingLines));

        // 損益振替は締める前の当期(OPEN)に、開始仕訳は次期に記帳する。
        return new ClosingResult(
                PostedJournal.postAll(period, closingEntries),
                PostedJournal.postAll(nextPeriod, openingEntries),
                closedPeriod,
                nextPeriod);
    }
}
