package com.example.accounting.model.consolidation;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.IncomeStatement;
import com.example.accounting.model.statement.TrialBalance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1社分の個別財務諸表。連結の入力。その会社の当期の仕訳(開始仕訳・決算整理仕訳を含み、損益振替の前)から
 * 導出する(保存しない)。
 *
 * <p>損益振替の前なので、収益・費用の残高が残り、利益剰余金には当期純利益が含まれていない。
 */
public record IndividualStatements(CompanyId company, List<Journal> journals) {

    public IndividualStatements {
        if (company == null) {
            throw new AccountingException("company is required");
        }
        if (journals == null) {
            throw new AccountingException("journals is required");
        }
        journals = List.copyOf(journals);
        for (Journal journal : journals) {
            if (journal.type() == JournalType.CLOSING || journal.type() == JournalType.CONSOLIDATION) {
                throw new AccountingException(
                        "individual statements must be before closing and must not contain consolidation journals: "
                                + journal.type());
            }
        }
    }

    /** 科目ごとの残高(正常な向きなら正)。損益振替前の試算表の残高。 */
    public Map<Account, Money> balances() {
        return balancesOf(journals);
    }

    /**
     * 損益を繰越利益剰余金に含めた、貸借対照表の科目だけの残高。期末の貸借対照表の数字にあたる。
     * 支配獲得日の連結(子会社の損益計算書を連結しない)で使う。
     */
    public Map<Account, Money> balanceSheetBalances() {
        Map<Account, Money> result = new LinkedHashMap<>();
        for (Map.Entry<Account, Money> entry : balances().entrySet()) {
            if (!isIncomeStatementItem(entry.getKey().category())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        result.merge(StandardAccounts.RETAINED_EARNINGS, netIncome(), Money::plus);
        return result;
    }

    public Money netIncome() {
        return IncomeStatement.of(journals).netIncome();
    }

    /** 損益振替前の利益剰余金(利益準備金・任意積立金を含む)。当期純利益は含まない。 */
    public Money retainedEarnings() {
        return retainedEarningsOf(balances());
    }

    /** 期首の利益剰余金。開始仕訳から求める。 */
    public Money openingRetainedEarnings() {
        List<Journal> opening = journals.stream().filter(j -> j.type() == JournalType.OPENING).toList();
        return retainedEarningsOf(balancesOf(opening));
    }

    /**
     * 当期の剰余金の配当の額。期首から損益振替前までの利益剰余金の減少額で求める
     * (期中の利益剰余金の減少はすべて配当とみなす。利益準備金の積立は利益剰余金の中の振替なので含まれない)。
     */
    public Money dividends() {
        Money decrease = openingRetainedEarnings().minus(retainedEarnings());
        if (decrease.isNegative()) {
            throw new AccountingException(
                    "retained earnings increased during the period before closing: " + decrease.negate().amountInYen());
        }
        return decrease;
    }

    static boolean isIncomeStatementItem(AccountCategory category) {
        return switch (category) {
            case SALES, COST_OF_SALES, SG_AND_A, NON_OPERATING_INCOME, NON_OPERATING_EXPENSE,
                    EXTRAORDINARY_INCOME, EXTRAORDINARY_LOSS, INCOME_TAX_EXPENSE,
                    NON_CONTROLLING_INTERESTS_PROFIT -> true;
            default -> false;
        };
    }

    private static Map<Account, Money> balancesOf(List<Journal> journals) {
        Map<Account, Money> result = new LinkedHashMap<>();
        for (Map.Entry<Account, TrialBalance.Row> entry : TrialBalance.of(journals).rows().entrySet()) {
            result.put(entry.getKey(), entry.getValue().netBalance(entry.getKey().type()));
        }
        return result;
    }

    private static Money retainedEarningsOf(Map<Account, Money> balances) {
        Money total = Money.ZERO;
        for (Map.Entry<Account, Money> entry : balances.entrySet()) {
            if (entry.getKey().category() == AccountCategory.RETAINED_EARNINGS) {
                total = total.plus(entry.getValue());
            }
        }
        return total;
    }
}
