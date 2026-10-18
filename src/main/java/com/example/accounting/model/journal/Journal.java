package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Side;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * 仕訳。追記のみ(append-only)で、更新・削除は存在しない。
 * 不変条件: 借方合計 = 貸方合計。
 */
public record Journal(
        JournalId id,
        LocalDate date,
        String description,
        JournalType type,
        List<JournalLine> lines,
        JournalId reversalOf
) {

    public Journal {
        if (id == null) {
            throw new AccountingException("id is required");
        }
        if (date == null) {
            throw new AccountingException("date is required");
        }
        if (description == null || description.isBlank()) {
            throw new AccountingException("description is required");
        }
        if (type == null) {
            throw new AccountingException("type is required");
        }
        if (lines == null || lines.size() < 2) {
            throw new AccountingException("journal must have at least two lines");
        }
        lines = List.copyOf(lines);

        Money debitTotal = sumOf(lines, Side.DEBIT);
        Money creditTotal = sumOf(lines, Side.CREDIT);
        if (!debitTotal.equals(creditTotal)) {
            throw new AccountingException(
                    "debit total (%d) must equal credit total (%d)"
                            .formatted(debitTotal.amountInYen(), creditTotal.amountInYen()));
        }
        if (debitTotal.isZero()) {
            throw new AccountingException("journal must have a non-zero amount");
        }
    }

    public static Journal of(
            JournalId id, LocalDate date, String description, JournalType type, List<JournalLine> lines) {
        return new Journal(id, date, description, type, lines, null);
    }

    public static Journal reversalOf(
            JournalId id, LocalDate date, String description, JournalType type,
            List<JournalLine> lines, JournalId original) {
        if (original == null) {
            throw new AccountingException("original journal id is required");
        }
        return new Journal(id, date, description, type, lines, original);
    }

    public Money debitTotal() {
        return sumOf(lines, Side.DEBIT);
    }

    public Money creditTotal() {
        return sumOf(lines, Side.CREDIT);
    }

    private static Money sumOf(List<JournalLine> lines, Side side) {
        Money total = Money.ZERO;
        for (JournalLine line : lines) {
            if (line.side() == side) {
                total = total.plus(line.amount());
            }
        }
        return total;
    }
}
