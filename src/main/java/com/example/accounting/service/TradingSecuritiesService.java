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
import java.util.Optional;

/**
 * 売買目的有価証券(切放方式)。期末の時価評価と売却の仕訳を導出する。保存は行わない。
 *
 * <p>切放方式のため、期末の評価替え(評価益・評価損)は {@code StandardAccounts.TRADING_SECURITIES}
 * の帳簿残高を直接書き換える(間接法ではない)。翌期に取得原価へ戻す仕訳は行わない。
 *
 * <p>購入は新しい計算を伴わないため、通常の {@link Journal#of} / {@link JournalLine#debit} で
 * {@code StandardAccounts.TRADING_SECURITIES} に対して直接記録すればよく、専用のメソッドは用意しない。
 *
 * <p>{@link #sell} は保有分を一括で売却する。一部売却は {@link #sellPortion}(平均単価で按分)を使う。
 */
public final class TradingSecuritiesService {

    /** 期末の時価評価。帳簿残高との差額を評価益・評価損として計上する(切放方式)。差額がなければ空を返す。 */
    public Optional<Journal> valuateAtPeriodEnd(Money fairValue, List<Journal> journalsSoFar, LocalDate date) {
        if (fairValue == null || fairValue.isNegative()) {
            throw new AccountingException("fairValue must not be negative");
        }

        Money bookValue = Ledger.of(StandardAccounts.TRADING_SECURITIES, journalsSoFar).balance();
        Money difference = fairValue.minus(bookValue);
        if (difference.isZero()) {
            return Optional.empty();
        }
        if (difference.isPositive()) {
            return Optional.of(Journal.of(
                    JournalId.newId(), date, "売買目的有価証券の評価替え(評価益)", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(StandardAccounts.TRADING_SECURITIES, difference),
                            JournalLine.credit(StandardAccounts.UNREALIZED_GAIN_ON_SECURITIES, difference))));
        }
        Money loss = difference.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, "売買目的有価証券の評価替え(評価損)", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.UNREALIZED_LOSS_ON_SECURITIES, loss),
                        JournalLine.credit(StandardAccounts.TRADING_SECURITIES, loss))));
    }

    /** 保有する売買目的有価証券を全額売却する。帳簿残高と売却価額の差額を売却益・売却損として計上する。 */
    public Journal sell(Account proceedsAccount, Money proceeds, List<Journal> journalsSoFar, LocalDate date) {
        Money bookValue = heldBookValue(journalsSoFar);
        return sale(proceedsAccount, proceeds, bookValue, Money.ZERO, date);
    }

    /**
     * 保有する売買目的有価証券の一部を売却する。売却分の帳簿価額は、現在の帳簿残高を保有数量で按分した額
     * (平均単価 × 売却数量、円未満切り捨て)とする。全数量を売却するときは帳簿残高の全額。
     */
    public Journal sellPortion(Account proceedsAccount, Money proceeds, int quantitySold, int quantityHeld,
                               List<Journal> journalsSoFar, LocalDate date) {
        return sellPortion(proceedsAccount, proceeds, Money.ZERO, quantitySold, quantityHeld, journalsSoFar, date);
    }

    /**
     * 売買目的で保有する債券の一部を、端数利息を付けて売却する。受け取る額は売却価額 + 端数利息で、
     * 端数利息は有価証券利息とする({@link BondService#accruedInterest} で計算できる)。
     */
    public Journal sellPortion(Account proceedsAccount, Money proceeds, Money accruedInterest,
                               int quantitySold, int quantityHeld, List<Journal> journalsSoFar, LocalDate date) {
        if (quantityHeld <= 0 || quantitySold <= 0 || quantitySold > quantityHeld) {
            throw new AccountingException("quantitySold must be between 1 and quantityHeld");
        }
        Money bookValue = heldBookValue(journalsSoFar);
        Money portion = quantitySold == quantityHeld
                ? bookValue
                : Money.ofYen(Math.floorDiv(bookValue.amountInYen() * quantitySold, quantityHeld));
        return sale(proceedsAccount, proceeds, portion, accruedInterest, date);
    }

    private Money heldBookValue(List<Journal> journalsSoFar) {
        Money bookValue = Ledger.of(StandardAccounts.TRADING_SECURITIES, journalsSoFar).balance();
        if (!bookValue.isPositive()) {
            throw new AccountingException("no trading securities are currently held");
        }
        return bookValue;
    }

    private Journal sale(Account proceedsAccount, Money proceeds, Money bookValue, Money accruedInterest,
                         LocalDate date) {
        if (proceedsAccount == null) {
            throw new AccountingException("proceedsAccount is required");
        }
        if (proceeds == null || proceeds.isNegative()) {
            throw new AccountingException("proceeds must not be negative");
        }
        if (accruedInterest == null || accruedInterest.isNegative()) {
            throw new AccountingException("accruedInterest must not be negative");
        }
        Money gainOrLoss = proceeds.minus(bookValue);

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(proceedsAccount, proceeds.plus(accruedInterest)));
        if (gainOrLoss.isNegative()) {
            lines.add(JournalLine.debit(StandardAccounts.LOSS_ON_SALE_OF_SECURITIES, gainOrLoss.negate()));
        }
        lines.add(JournalLine.credit(StandardAccounts.TRADING_SECURITIES, bookValue));
        if (gainOrLoss.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.GAIN_ON_SALE_OF_SECURITIES, gainOrLoss));
        }
        if (accruedInterest.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.INTEREST_ON_SECURITIES, accruedInterest));
        }

        return Journal.of(JournalId.newId(), date, "売買目的有価証券の売却", JournalType.NORMAL, lines);
    }
}
