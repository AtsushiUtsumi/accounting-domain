package com.example.accounting.model.consolidation;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.statement.TrialBalance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 連結精算表。各社の個別財務諸表の残高を合算し、連結修正仕訳(修正・消去)を加えて連結の残高を出す。
 * 毎回導出する(保存しない)。金額はすべて科目の正常な向きを正とした残高。
 *
 * <p>個別財務諸表は損益振替の前の残高なので、利益剰余金の行には当期純利益が含まれない
 * (連結貸借対照表で親会社株主に帰属する当期純利益を足す。{@link ConsolidatedBalanceSheet} を参照)。
 */
public record ConsolidationWorksheet(Map<Account, Row> rows) {

    private static final TrialBalance.Row ZERO_ROW = new TrialBalance.Row(Money.ZERO, Money.ZERO);

    /**
     * @param individual       会社ごとの個別財務諸表の残高
     * @param adjustmentDebit  修正・消去の借方
     * @param adjustmentCredit 修正・消去の貸方
     * @param consolidated     連結財務諸表の残高
     */
    public record Row(
            Map<CompanyId, Money> individual,
            Money adjustmentDebit,
            Money adjustmentCredit,
            Money consolidated
    ) {

        public Row {
            individual = Map.copyOf(individual);
        }

        /** 個別財務諸表の合計(合算)。 */
        public Money individualTotal() {
            Money total = Money.ZERO;
            for (Money amount : individual.values()) {
                total = total.plus(amount);
            }
            return total;
        }
    }

    public ConsolidationWorksheet {
        rows = Map.copyOf(rows);
    }

    /**
     * @param individualBalances 会社ごとの個別財務諸表の残高(正常な向きを正)
     * @param adjustments        連結修正仕訳(種類は {@link JournalType#CONSOLIDATION})
     */
    public static ConsolidationWorksheet of(
            Map<CompanyId, Map<Account, Money>> individualBalances, List<Journal> adjustments) {
        for (Journal journal : adjustments) {
            if (journal.type() != JournalType.CONSOLIDATION) {
                throw new AccountingException("adjustments must be consolidation journals: " + journal.type());
            }
        }
        TrialBalance adjustmentTotals = TrialBalance.of(adjustments);

        Map<Account, Map<CompanyId, Money>> byAccount = new LinkedHashMap<>();
        for (Map.Entry<CompanyId, Map<Account, Money>> company : individualBalances.entrySet()) {
            for (Map.Entry<Account, Money> balance : company.getValue().entrySet()) {
                byAccount.computeIfAbsent(balance.getKey(), k -> new LinkedHashMap<>())
                        .put(company.getKey(), balance.getValue());
            }
        }
        for (Account account : adjustmentTotals.rows().keySet()) {
            byAccount.computeIfAbsent(account, k -> new LinkedHashMap<>());
        }

        Map<Account, Row> rows = new LinkedHashMap<>();
        for (Map.Entry<Account, Map<CompanyId, Money>> entry : byAccount.entrySet()) {
            Account account = entry.getKey();
            TrialBalance.Row adjustment = adjustmentTotals.rows().getOrDefault(account, ZERO_ROW);
            Money individualTotal = Money.ZERO;
            for (Money amount : entry.getValue().values()) {
                individualTotal = individualTotal.plus(amount);
            }
            Money consolidated = individualTotal.plus(adjustment.netBalance(account.type()));
            rows.put(account, new Row(entry.getValue(), adjustment.debitTotal(), adjustment.creditTotal(), consolidated));
        }
        return new ConsolidationWorksheet(rows);
    }

    /** 連結財務諸表の残高(行がなければゼロ)。 */
    public Money consolidated(Account account) {
        Row row = rows.get(account);
        return row == null ? Money.ZERO : row.consolidated();
    }

    public Money adjustmentDebitTotal() {
        Money total = Money.ZERO;
        for (Row row : rows.values()) {
            total = total.plus(row.adjustmentDebit());
        }
        return total;
    }

    public Money adjustmentCreditTotal() {
        Money total = Money.ZERO;
        for (Row row : rows.values()) {
            total = total.plus(row.adjustmentCredit());
        }
        return total;
    }

    /** 修正・消去欄の借方合計と貸方合計が一致すること。 */
    public boolean isAdjustmentBalanced() {
        return adjustmentDebitTotal().equals(adjustmentCreditTotal());
    }
}
