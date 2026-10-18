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
 * 受取手形(約束手形)の割引。手形金額のうち割引料を差し引いた金額を先に受け取り、
 * 手形金額全額を受取手形から取り除く。割引料は呼び出し側が額で与える(日割計算はしない)。
 *
 * <p>電子記録債権の譲渡(割引)も同じ形のため、{@link #discountElectronicReceivable} で扱う。
 * 手形の裏書・不渡・更新、電子記録債権の発生・決済は新しい計算が必要ないため、通常の仕訳で
 * {@code StandardAccounts.DISHONORED_NOTES} などの科目を直接使えばよい。
 *
 * <p>手形の振出・受取・期日決済は、新しい計算が必要ないため {@link Journal#of} と
 * {@link JournalLine#debit}/{@link JournalLine#credit} を {@code StandardAccounts.NOTES_RECEIVABLE} /
 * {@code StandardAccounts.NOTES_PAYABLE} に対して直接使えばよく、専用のメソッドは用意しない。
 */
public final class NotesReceivableService {

    /** 受取手形の割引。割引料は手形売却損とする。 */
    public Journal discount(Account proceedsAccount, Money faceValue, Money discountFee, LocalDate date) {
        return discount(StandardAccounts.NOTES_RECEIVABLE, StandardAccounts.LOSS_ON_NOTE_DISCOUNTING,
                proceedsAccount, faceValue, discountFee, date);
    }

    /** 電子記録債権の譲渡(割引)。額面と受取額の差は電子記録債権売却損とする。 */
    public Journal discountElectronicReceivable(Account proceedsAccount, Money faceValue, Money discountFee,
                                                LocalDate date) {
        return discount(StandardAccounts.ELECTRONICALLY_RECORDED_RECEIVABLES,
                StandardAccounts.LOSS_ON_SALE_OF_ELECTRONIC_RECEIVABLES,
                proceedsAccount, faceValue, discountFee, date);
    }

    /**
     * 債権を額面より低い額で現金化する(割引・譲渡)。共通の形: 受取額を借方、差額を {@code lossAccount}、
     * 債権の額面全額を貸方とする。
     */
    public Journal discount(Account receivableAccount, Account lossAccount, Account proceedsAccount,
                            Money faceValue, Money discountFee, LocalDate date) {
        if (receivableAccount == null || lossAccount == null) {
            throw new AccountingException("receivableAccount and lossAccount are required");
        }
        if (proceedsAccount == null) {
            throw new AccountingException("proceedsAccount is required");
        }
        if (faceValue == null || !faceValue.isPositive()) {
            throw new AccountingException("faceValue must be positive");
        }
        if (discountFee == null || discountFee.isNegative()) {
            throw new AccountingException("discountFee must not be negative");
        }
        if (discountFee.compareTo(faceValue) >= 0) {
            throw new AccountingException("discountFee must be less than faceValue");
        }

        Money proceeds = faceValue.minus(discountFee);

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(proceedsAccount, proceeds));
        if (discountFee.isPositive()) {
            lines.add(JournalLine.debit(lossAccount, discountFee));
        }
        lines.add(JournalLine.credit(receivableAccount, faceValue));

        return Journal.of(JournalId.newId(), date, receivableAccount.name() + "の割引", JournalType.NORMAL, lines);
    }
}
