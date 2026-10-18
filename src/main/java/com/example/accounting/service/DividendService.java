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
 * 剰余金の配当。配当額に応じて準備金を積み立てる(会社法の上限計算を行う)。繰越利益剰余金を原資とする
 * 配当では利益準備金、その他資本剰余金を原資とする配当では資本準備金に積み立てる。
 *
 * <p>積立額 = min(配当額 × 1/10, 資本金 × 1/4 - (資本準備金 + 利益準備金の現在残高))。
 * 右辺が負(すでに上限に達している)ときは積み立てない。1円未満は切り捨てる。
 *
 * <p>新株の発行は計算を伴わない単純な仕訳のため、専用のメソッドは用意しない
 * ({@link Journal#of} と {@code StandardAccounts.CAPITAL_STOCK} / {@code CAPITAL_SURPLUS}
 * に対して直接 {@link JournalLine#credit} すればよい)。
 */
public final class DividendService {

    public Journal declareDividends(
            Money dividendAmount,
            Money capitalStockBalance,
            Money capitalSurplusBalance,
            Money statutoryReserveBalance,
            LocalDate date) {
        validate(dividendAmount, capitalStockBalance, capitalSurplusBalance, statutoryReserveBalance);
        return dividendJournal(StandardAccounts.RETAINED_EARNINGS, StandardAccounts.STATUTORY_RESERVE,
                dividendAmount, capitalStockBalance, capitalSurplusBalance, statutoryReserveBalance, date);
    }

    /**
     * その他資本剰余金を原資とする配当。準備金の積立額の計算(上限を含む)は利益剰余金からの配当と同じで、
     * 積立先が資本準備金になる。
     */
    public Journal declareDividendsFromCapitalSurplus(
            Money dividendAmount,
            Money capitalStockBalance,
            Money capitalSurplusBalance,
            Money statutoryReserveBalance,
            LocalDate date) {
        validate(dividendAmount, capitalStockBalance, capitalSurplusBalance, statutoryReserveBalance);
        return dividendJournal(StandardAccounts.OTHER_CAPITAL_SURPLUS, StandardAccounts.CAPITAL_SURPLUS,
                dividendAmount, capitalStockBalance, capitalSurplusBalance, statutoryReserveBalance, date);
    }

    private Journal dividendJournal(
            Account sourceAccount, Account reserveAccount, Money dividendAmount, Money capitalStockBalance,
            Money capitalSurplusBalance, Money statutoryReserveBalance, LocalDate date) {
        Money appropriation = statutoryReserveAppropriation(
                dividendAmount, capitalStockBalance, capitalSurplusBalance, statutoryReserveBalance);

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(sourceAccount, dividendAmount.plus(appropriation)));
        lines.add(JournalLine.credit(StandardAccounts.DIVIDENDS_PAYABLE, dividendAmount));
        if (appropriation.isPositive()) {
            lines.add(JournalLine.credit(reserveAccount, appropriation));
        }

        return Journal.of(JournalId.newId(), date, "剰余金の配当", JournalType.NORMAL, lines);
    }

    private static void validate(Money dividendAmount, Money capitalStockBalance,
                                 Money capitalSurplusBalance, Money statutoryReserveBalance) {
        if (dividendAmount == null || !dividendAmount.isPositive()) {
            throw new AccountingException("dividendAmount must be positive");
        }
        if (capitalStockBalance == null || capitalStockBalance.isNegative()) {
            throw new AccountingException("capitalStockBalance must not be negative");
        }
        if (capitalSurplusBalance == null || capitalSurplusBalance.isNegative()) {
            throw new AccountingException("capitalSurplusBalance must not be negative");
        }
        if (statutoryReserveBalance == null || statutoryReserveBalance.isNegative()) {
            throw new AccountingException("statutoryReserveBalance must not be negative");
        }
    }

    private Money statutoryReserveAppropriation(
            Money dividendAmount, Money capitalStockBalance, Money capitalSurplusBalance, Money statutoryReserveBalance) {
        Money oneTenthOfDividend = Money.ofYen(dividendAmount.amountInYen() / 10);
        Money reserveCeiling = Money.ofYen(capitalStockBalance.amountInYen() / 4);
        Money alreadyReserved = capitalSurplusBalance.plus(statutoryReserveBalance);
        Money remainingRoom = reserveCeiling.minus(alreadyReserved);
        if (remainingRoom.isNegative()) {
            return Money.ZERO;
        }
        return oneTenthOfDividend.compareTo(remainingRoom) <= 0 ? oneTenthOfDividend : remainingRoom;
    }
}
