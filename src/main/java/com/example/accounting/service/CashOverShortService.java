package com.example.accounting.service;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.Ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 現金過不足。手許現金と現金勘定の帳簿残高の食い違いを発見したときの仕訳と、決算までに
 * 原因が判明しなかった分を雑損・雑収入へ振り替える仕訳を導出する。保存は行わない。
 *
 * <p>原因判明時にCASH_OVER_SHORTから正しい勘定へ振り替える仕訳は、新しい計算を伴わないため
 * 通常の {@link Journal#of} / {@link JournalLine#debit}・{@link JournalLine#credit} で組めばよく、
 * 専用のメソッドは用意しない。
 */
public final class CashOverShortService {

    /** 手許現金の実際有高と現金勘定の帳簿残高を比較し、食い違いを現金過不足に記録する。一致していれば空を返す。 */
    public Optional<Journal> recordDiscrepancy(Money actualCashOnHand, List<Journal> journalsSoFar, LocalDate date) {
        Money bookBalance = Ledger.of(StandardAccounts.CASH, journalsSoFar).balance();
        Money difference = actualCashOnHand.minus(bookBalance);
        if (difference.isZero()) {
            return Optional.empty();
        }
        if (difference.isPositive()) {
            return Optional.of(Journal.of(
                    JournalId.newId(), date, "現金過剰の発見", JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.CASH, difference),
                            JournalLine.credit(StandardAccounts.CASH_OVER_SHORT, difference))));
        }
        Money shortage = difference.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, "現金不足の発見", JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.CASH_OVER_SHORT, shortage),
                        JournalLine.credit(StandardAccounts.CASH, shortage))));
    }

    /** 決算時、原因不明のまま残っている現金過不足を雑損(不足)または雑収入(過剰)へ振り替える。 */
    public Optional<Journal> settleAtPeriodEnd(List<Journal> journalsSoFar, LocalDate date) {
        Money remaining = Ledger.of(StandardAccounts.CASH_OVER_SHORT, journalsSoFar).balance();
        if (remaining.isZero()) {
            return Optional.empty();
        }
        if (remaining.isPositive()) {
            return Optional.of(Journal.of(
                    JournalId.newId(), date, "原因不明の現金過不足(不足)を雑損へ振替", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(StandardAccounts.MISCELLANEOUS_LOSS, remaining),
                            JournalLine.credit(StandardAccounts.CASH_OVER_SHORT, remaining))));
        }
        Money overage = remaining.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, "原因不明の現金過不足(過剰)を雑収入へ振替", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.CASH_OVER_SHORT, overage),
                        JournalLine.credit(StandardAccounts.MISCELLANEOUS_INCOME, overage))));
    }
}
