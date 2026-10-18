package com.example.accounting.model.statement;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 株主資本等変動計算書。仕訳一覧から毎回導出する(保存しない)。
 *
 * <p>当期首残高は、この期の開始仕訳({@link JournalType#OPENING})から導出する。それ以外の
 * 純資産(資本・利益剰余金・評価・換算差額等)の変動を事由({@link EquityChangeReason})別に集計し、
 * 当期末残高を導く。列は純資産の科目ごと(その他資本剰余金・任意積立金・その他有価証券評価差額金を含む)。
 */
public record StatementOfChangesInEquity(
        Map<Account, Money> openingBalances,
        Map<EquityChangeReason, Map<Account, Money>> changesByReason,
        Map<Account, Money> closingBalances
) {

    public StatementOfChangesInEquity {
        openingBalances = Map.copyOf(openingBalances);
        closingBalances = Map.copyOf(closingBalances);
        Map<EquityChangeReason, Map<Account, Money>> copy = new EnumMap<>(EquityChangeReason.class);
        changesByReason.forEach((reason, byAccount) -> copy.put(reason, Map.copyOf(byAccount)));
        changesByReason = Map.copyOf(copy);
    }

    public static StatementOfChangesInEquity of(List<Journal> journalsThisPeriod) {
        List<Journal> openingJournals = journalsThisPeriod.stream()
                .filter(journal -> journal.type() == JournalType.OPENING)
                .toList();
        List<Journal> changeJournals = journalsThisPeriod.stream()
                .filter(journal -> journal.type() != JournalType.OPENING)
                .toList();

        Map<Account, Money> openingBalances = equityBalancesOf(openingJournals);

        Map<EquityChangeReason, Map<Account, Money>> changesByReason = new EnumMap<>(EquityChangeReason.class);
        for (Journal journal : changeJournals) {
            Map<Account, Money> delta = equityDeltaOf(journal);
            if (delta.isEmpty()) {
                continue;
            }
            EquityChangeReason reason = classify(journal);
            Map<Account, Money> bucket = changesByReason.computeIfAbsent(reason, r -> new LinkedHashMap<>());
            delta.forEach((account, amount) -> bucket.merge(account, amount, Money::plus));
        }

        Map<Account, Money> closingBalances = equityBalancesOf(journalsThisPeriod);

        return new StatementOfChangesInEquity(openingBalances, changesByReason, closingBalances);
    }

    private static EquityChangeReason classify(Journal journal) {
        boolean touchesRetainedEarnings = touches(journal, AccountCategory.RETAINED_EARNINGS);
        boolean touchesCapital = touches(journal, AccountCategory.CAPITAL);
        boolean touchesValuation = touches(journal, AccountCategory.VALUATION_AND_TRANSLATION_ADJUSTMENTS);
        boolean touchesDividendsPayable = journal.lines().stream()
                .anyMatch(line -> line.account().equals(StandardAccounts.DIVIDENDS_PAYABLE));
        boolean onlyEquity = journal.lines().stream().allMatch(line -> isEquity(line.account()));

        if (journal.type() == JournalType.CLOSING && touchesRetainedEarnings) {
            return EquityChangeReason.NET_INCOME;
        }
        if (touchesValuation) {
            return EquityChangeReason.VALUATION_CHANGE;
        }
        if (touchesDividendsPayable) {
            return EquityChangeReason.DIVIDENDS;
        }
        if (onlyEquity) {
            return EquityChangeReason.TRANSFER_WITHIN_EQUITY;
        }
        if (touchesCapital) {
            return EquityChangeReason.ISSUANCE_OF_SHARES;
        }
        if (touchesRetainedEarnings) {
            return EquityChangeReason.DIVIDENDS;
        }
        return EquityChangeReason.OTHER;
    }

    private static boolean touches(Journal journal, AccountCategory category) {
        return journal.lines().stream().anyMatch(line -> line.account().category() == category);
    }

    private static Map<Account, Money> equityDeltaOf(Journal journal) {
        TrialBalance trialBalance = TrialBalance.of(List.of(journal));
        Map<Account, Money> delta = new LinkedHashMap<>();
        for (Map.Entry<Account, TrialBalance.Row> entry : trialBalance.rows().entrySet()) {
            Account account = entry.getKey();
            if (isEquity(account)) {
                delta.put(account, entry.getValue().netBalance(account.type()));
            }
        }
        return delta;
    }

    private static Map<Account, Money> equityBalancesOf(List<Journal> journals) {
        TrialBalance trialBalance = TrialBalance.of(journals);
        Map<Account, Money> balances = new LinkedHashMap<>();
        for (Map.Entry<Account, TrialBalance.Row> entry : trialBalance.rows().entrySet()) {
            Account account = entry.getKey();
            if (isEquity(account)) {
                balances.put(account, entry.getValue().netBalance(account.type()));
            }
        }
        return balances;
    }

    private static boolean isEquity(Account account) {
        return account.category() == AccountCategory.CAPITAL
                || account.category() == AccountCategory.RETAINED_EARNINGS
                || account.category() == AccountCategory.VALUATION_AND_TRANSLATION_ADJUSTMENTS;
    }

    /** 指定した科目の当期変動額合計(事由をまたいだ合計)。 */
    public Money totalChangeFor(Account account) {
        Money total = Money.ZERO;
        for (Map<Account, Money> bucket : changesByReason.values()) {
            total = total.plus(bucket.getOrDefault(account, Money.ZERO));
        }
        return total;
    }

    public Money totalEquityAtBeginning() {
        return sum(openingBalances.values());
    }

    public Money totalEquityAtEnd() {
        return sum(closingBalances.values());
    }

    /** 自己検証: 科目ごとに「当期首残高+当期変動額合計=当期末残高」が成り立つこと。 */
    public boolean reconciles() {
        for (Map.Entry<Account, Money> entry : closingBalances.entrySet()) {
            Account account = entry.getKey();
            Money opening = openingBalances.getOrDefault(account, Money.ZERO);
            if (!opening.plus(totalChangeFor(account)).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Money sum(Iterable<Money> amounts) {
        Money total = Money.ZERO;
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }
}
