package com.example.accounting.model.statement;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 銀行勘定調整表(両者区分調整法)。銀行の残高証明書の残高と、帳簿の当座預金残高の差異を、
 * 銀行側の調整と帳簿側の調整に分けて一致させる。保存はせず、毎回導出する。
 *
 * <p>帳簿側の調整項目(連絡未通知・未渡小切手・誤記入)だけが帳簿の修正仕訳になる
 * ({@link #correctingEntries})。銀行側の項目(時間外預入・未取付小切手・未取立小切手)は仕訳しない。
 */
public record BankReconciliation(
        Account checkingAccount,
        Money bankStatementBalance,
        Money bookBalance,
        List<ReconcilingItem> items
) {

    public BankReconciliation {
        if (checkingAccount == null || bankStatementBalance == null || bookBalance == null) {
            throw new AccountingException("checkingAccount and balances are required");
        }
        items = List.copyOf(items);
    }

    /** 調整項目。銀行側か帳簿側か、どちら向きに調整するかは種類ごとに決まっている。 */
    public sealed interface ReconcilingItem {
        Money amount();
    }

    /** 時間外預入。帳簿には預入を記録したが、銀行は翌日扱い。銀行残高に加算する。 */
    public record DepositInTransit(Money amount) implements ReconcilingItem {
    }

    /** 未取付小切手。振り出した小切手が、まだ銀行に呈示されていない。銀行残高から減算する。 */
    public record OutstandingCheck(Money amount) implements ReconcilingItem {
    }

    /** 未取立小切手。銀行に取立てを依頼したが、まだ入金されていない。銀行残高に加算する。 */
    public record UncollectedCheck(Money amount) implements ReconcilingItem {
    }

    /** 連絡未通知(入金)。銀行には入金があったが、帳簿に記録していない。帳簿残高に加算する。 */
    public record UnrecordedDeposit(Money amount, Account counterAccount) implements ReconcilingItem {
    }

    /** 連絡未通知(引落し)。銀行から引き落とされたが、帳簿に記録していない。帳簿残高から減算する。 */
    public record UnrecordedWithdrawal(Money amount, Account counterAccount) implements ReconcilingItem {
    }

    /**
     * 未渡小切手。小切手を作成して帳簿で支払を記録したが、相手に渡していない。帳簿残高に加算する。
     * 相手科目は、支払の内容に応じて買掛金(商品代金)または未払金(それ以外)を指定する。
     */
    public record UnissuedCheck(Money amount, Account counterAccount) implements ReconcilingItem {
    }

    /** 誤記入。{@code amount} は帳簿残高の訂正額(正なら帳簿残高を増やし、負なら減らす)。 */
    public record RecordingError(Money amount, Account counterAccount) implements ReconcilingItem {
    }

    /** 当座預金の帳簿残高を仕訳から求めて、銀行勘定調整表を作る。 */
    public static BankReconciliation of(Account checkingAccount, Money bankStatementBalance,
                                        List<Journal> journalsSoFar, List<ReconcilingItem> items) {
        Money bookBalance = Ledger.of(checkingAccount, journalsSoFar).balance();
        return new BankReconciliation(checkingAccount, bankStatementBalance, bookBalance, items);
    }

    /** 銀行側の調整後残高。 */
    public Money adjustedBankBalance() {
        Money balance = bankStatementBalance;
        for (ReconcilingItem item : items) {
            balance = switch (item) {
                case DepositInTransit d -> balance.plus(d.amount());
                case UncollectedCheck u -> balance.plus(u.amount());
                case OutstandingCheck o -> balance.minus(o.amount());
                default -> balance;
            };
        }
        return balance;
    }

    /** 帳簿側の調整後残高。 */
    public Money adjustedBookBalance() {
        Money balance = bookBalance;
        for (ReconcilingItem item : items) {
            balance = balance.plus(bookAdjustmentOf(item));
        }
        return balance;
    }

    /** 自己検証: 銀行側の調整後残高 = 帳簿側の調整後残高。 */
    public boolean reconciles() {
        return adjustedBankBalance().equals(adjustedBookBalance());
    }

    /** 帳簿側の調整項目に対する修正仕訳({@link JournalType#ADJUSTING})。 */
    public List<Journal> correctingEntries(LocalDate date) {
        List<Journal> result = new ArrayList<>();
        for (ReconcilingItem item : items) {
            Money adjustment = bookAdjustmentOf(item);
            if (adjustment.isZero()) {
                continue;
            }
            Account counter = switch (item) {
                case UnrecordedDeposit d -> d.counterAccount();
                case UnrecordedWithdrawal w -> w.counterAccount();
                case UnissuedCheck u -> u.counterAccount();
                case RecordingError e -> e.counterAccount();
                default -> throw new IllegalStateException("bank-side item has no book adjustment");
            };
            if (counter == null) {
                throw new AccountingException("counterAccount is required for book-side items");
            }
            List<JournalLine> lines = adjustment.isPositive()
                    ? List.of(JournalLine.debit(checkingAccount, adjustment), JournalLine.credit(counter, adjustment))
                    : List.of(JournalLine.debit(counter, adjustment.negate()),
                            JournalLine.credit(checkingAccount, adjustment.negate()));
            result.add(Journal.of(JournalId.newId(), date, "銀行勘定調整: " + describe(item), JournalType.ADJUSTING,
                    lines));
        }
        return result;
    }

    private static Money bookAdjustmentOf(ReconcilingItem item) {
        return switch (item) {
            case UnrecordedDeposit d -> d.amount();
            case UnrecordedWithdrawal w -> w.amount().negate();
            case UnissuedCheck u -> u.amount();
            case RecordingError e -> e.amount();
            case DepositInTransit d -> Money.ZERO;
            case OutstandingCheck o -> Money.ZERO;
            case UncollectedCheck u -> Money.ZERO;
        };
    }

    private static String describe(ReconcilingItem item) {
        return switch (item) {
            case UnrecordedDeposit d -> "連絡未通知(入金)";
            case UnrecordedWithdrawal w -> "連絡未通知(引落し)";
            case UnissuedCheck u -> "未渡小切手";
            case RecordingError e -> "誤記入";
            case DepositInTransit d -> "時間外預入";
            case OutstandingCheck o -> "未取付小切手";
            case UncollectedCheck u -> "未取立小切手";
        };
    }
}
