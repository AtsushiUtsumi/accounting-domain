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
import java.util.List;

/**
 * 貸倒れ(売掛金などが回収できなくなったとき)の仕訳を導出する。保存は行わない。
 *
 * <p>前期以前に発生した債権は、前期末に設定した貸倒引当金で賄う(足りない分は貸倒損失)。
 * 当期に発生した債権は、引当金の設定対象になっていないため全額を貸倒損失とする。
 * 債権の発生日はこのライブラリでは追跡していないため、どちらに当たるかは呼び出し側が指定する。
 */
public final class BadDebtService {

    /**
     * 債権の貸倒れ。
     *
     * @param receivableAccount        貸倒れた債権の勘定(売掛金・受取手形など)
     * @param amount                   貸倒額
     * @param arisingInCurrentPeriod   当期に発生した債権なら true、前期以前なら false
     * @param journalsSoFar            貸倒引当金の残高を求めるための、これまでの仕訳
     */
    public Journal writeOff(Account receivableAccount, Money amount, boolean arisingInCurrentPeriod,
                            List<Journal> journalsSoFar, LocalDate date) {
        if (receivableAccount == null) {
            throw new AccountingException("receivableAccount is required");
        }
        if (arisingInCurrentPeriod) {
            if (amount == null || !amount.isPositive()) {
                throw new AccountingException("amount must be positive");
            }
            return Journal.of(
                    JournalId.newId(), date, receivableAccount.name() + "の貸倒れ(当期発生)", JournalType.NORMAL,
                    List.of(
                            JournalLine.debit(StandardAccounts.BAD_DEBT_LOSS, amount),
                            JournalLine.credit(receivableAccount, amount)));
        }
        return ProvisionCalculator.use(
                amount, StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, StandardAccounts.BAD_DEBT_LOSS,
                receivableAccount, receivableAccount.name() + "の貸倒れ(前期以前発生)",
                journalsSoFar, date, JournalType.NORMAL);
    }

    /** 前期以前に貸倒れ処理した債権を回収したとき。回収額を償却債権取立益とする。 */
    public Journal recoverWrittenOff(Account proceedsAccount, Money amount, LocalDate date) {
        if (proceedsAccount == null) {
            throw new AccountingException("proceedsAccount is required");
        }
        if (amount == null || !amount.isPositive()) {
            throw new AccountingException("amount must be positive");
        }
        return Journal.of(
                JournalId.newId(), date, "償却済み債権の回収", JournalType.NORMAL,
                List.of(
                        JournalLine.debit(proceedsAccount, amount),
                        JournalLine.credit(StandardAccounts.RECOVERY_OF_BAD_DEBTS, amount)));
    }
}
