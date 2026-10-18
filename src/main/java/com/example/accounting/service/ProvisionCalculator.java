package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
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

/** 引当金の計算の共通部品(差額補充・洗替・取崩し)。貸倒引当金とそれ以外の引当金で共有する。 */
final class ProvisionCalculator {

    private ProvisionCalculator() {
    }

    /**
     * 引当金の現在の残高(貸方残高を正とする)。負債の引当金も、資産の控除項目(貸倒引当金)も
     * 貸方残高が正常なので、貸方合計 − 借方合計で求める。
     */
    static Money currentBalance(Account provisionAccount, List<Journal> journalsSoFar) {
        Ledger ledger = Ledger.of(provisionAccount, journalsSoFar);
        return ledger.creditTotal().minus(ledger.debitTotal());
    }

    /** 差額補充法。必要額と現在の残高の差額を、繰入(不足)または戻入(過剰)する。差額がなければ空。 */
    static Optional<Journal> adjustTo(
            Money requiredBalance, Account provisionAccount, Account expenseAccount, Account reversalAccount,
            String label, List<Journal> journalsSoFar, LocalDate date) {
        requireNonNegative(requiredBalance);
        Money difference = requiredBalance.minus(currentBalance(provisionAccount, journalsSoFar));
        if (difference.isZero()) {
            return Optional.empty();
        }
        if (difference.isPositive()) {
            return Optional.of(Journal.of(
                    JournalId.newId(), date, label + "の繰入(差額補充法)", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(expenseAccount, difference),
                            JournalLine.credit(provisionAccount, difference))));
        }
        if (reversalAccount == null) {
            throw new AccountingException(label + " exceeds the required balance, but it has no reversal account");
        }
        Money excess = difference.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, label + "の取り崩し(差額補充法)", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(provisionAccount, excess),
                        JournalLine.credit(reversalAccount, excess))));
    }

    /** 洗替法。現在の残高をすべて戻入してから、必要額を改めて繰り入れる。 */
    static List<Journal> replace(
            Money requiredBalance, Account provisionAccount, Account expenseAccount, Account reversalAccount,
            String label, List<Journal> journalsSoFar, LocalDate date) {
        requireNonNegative(requiredBalance);
        Money current = currentBalance(provisionAccount, journalsSoFar);
        List<Journal> result = new ArrayList<>();
        if (current.isPositive()) {
            if (reversalAccount == null) {
                throw new AccountingException(label + " has no reversal account");
            }
            result.add(Journal.of(
                    JournalId.newId(), date, label + "の戻入(洗替法)", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(provisionAccount, current),
                            JournalLine.credit(reversalAccount, current))));
        }
        if (requiredBalance.isPositive()) {
            result.add(Journal.of(
                    JournalId.newId(), date, label + "の繰入(洗替法)", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(expenseAccount, requiredBalance),
                            JournalLine.credit(provisionAccount, requiredBalance))));
        }
        return result;
    }

    /**
     * 引当金の取崩し。支払額のうち引当金の残高までを引当金で充当し、足りない分を {@code shortfallAccount} に計上する。
     */
    static Journal use(
            Money amount, Account provisionAccount, Account shortfallAccount, Account creditAccount,
            String description, List<Journal> journalsSoFar, LocalDate date, JournalType type) {
        if (amount == null || !amount.isPositive()) {
            throw new AccountingException("amount must be positive");
        }
        if (creditAccount == null) {
            throw new AccountingException("credit account is required");
        }
        Money current = currentBalance(provisionAccount, journalsSoFar);
        Money covered = current.isPositive() ? (current.compareTo(amount) < 0 ? current : amount) : Money.ZERO;
        Money shortfall = amount.minus(covered);

        List<JournalLine> lines = new ArrayList<>();
        if (covered.isPositive()) {
            lines.add(JournalLine.debit(provisionAccount, covered));
        }
        if (shortfall.isPositive()) {
            lines.add(JournalLine.debit(shortfallAccount, shortfall));
        }
        lines.add(JournalLine.credit(creditAccount, amount));
        return Journal.of(JournalId.newId(), date, description, type, lines);
    }

    private static void requireNonNegative(Money requiredBalance) {
        if (requiredBalance == null || requiredBalance.isNegative()) {
            throw new AccountingException("requiredBalance must not be negative");
        }
    }
}
