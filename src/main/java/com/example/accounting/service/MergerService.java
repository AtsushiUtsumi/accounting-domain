package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Side;
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
 * 吸収合併(事業の受け入れ)。受け入れた資産・負債を時価で計上し、対価(新株の発行など)との差額を
 * のれん(対価が多いとき)または負ののれん発生益(対価が少ないとき)とする。保存は行わない。
 */
public final class MergerService {

    /**
     * @param acquiredAssets     受け入れた資産(時価)。すべて借方の行
     * @param assumedLiabilities 引き受けた負債(時価)。すべて貸方の行
     * @param consideration      対価(資本金・資本準備金・その他資本剰余金・現金など)。すべて貸方の行
     */
    public Journal merge(List<JournalLine> acquiredAssets, List<JournalLine> assumedLiabilities,
                         List<JournalLine> consideration, LocalDate date) {
        requireSide(acquiredAssets, Side.DEBIT, "acquiredAssets");
        requireSide(assumedLiabilities, Side.CREDIT, "assumedLiabilities");
        requireSide(consideration, Side.CREDIT, "consideration");
        if (acquiredAssets.isEmpty() || consideration.isEmpty()) {
            throw new AccountingException("acquiredAssets and consideration are required");
        }

        Money netAssets = total(acquiredAssets).minus(total(assumedLiabilities));
        Money difference = total(consideration).minus(netAssets);

        List<JournalLine> lines = new ArrayList<>(acquiredAssets);
        if (difference.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.GOODWILL, difference));
        }
        lines.addAll(assumedLiabilities);
        lines.addAll(consideration);
        if (difference.isNegative()) {
            lines.add(JournalLine.credit(StandardAccounts.GAIN_ON_BARGAIN_PURCHASE, difference.negate()));
        }
        return Journal.of(JournalId.newId(), date, "吸収合併", JournalType.NORMAL, lines);
    }

    private static void requireSide(List<JournalLine> lines, Side side, String name) {
        if (lines == null) {
            throw new AccountingException(name + " is required");
        }
        for (JournalLine line : lines) {
            if (line.side() != side) {
                throw new AccountingException(name + " must contain only " + side + " lines");
            }
        }
    }

    private static Money total(List<JournalLine> lines) {
        Money total = Money.ZERO;
        for (JournalLine line : lines) {
            total = total.plus(line.amount());
        }
        return total;
    }
}
