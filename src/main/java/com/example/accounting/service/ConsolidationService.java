package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.AccountCategory;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.consolidation.CompanyId;
import com.example.accounting.model.consolidation.ConsolidatedBalanceSheet;
import com.example.accounting.model.consolidation.ConsolidatedIncomeStatement;
import com.example.accounting.model.consolidation.ConsolidationWorksheet;
import com.example.accounting.model.consolidation.IndividualStatements;
import com.example.accounting.model.consolidation.IntercompanyBalance;
import com.example.accounting.model.consolidation.IntercompanyTransaction;
import com.example.accounting.model.consolidation.Subsidiary;
import com.example.accounting.model.consolidation.UnrealizedProfit;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.money.RoundingPolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 連結財務諸表の作成。個別財務諸表を合算し、連結修正仕訳を生成・反映して、連結精算表・連結損益計算書・
 * 連結貸借対照表を導出する。保存は行わない。
 *
 * <p>連結修正仕訳は次の順序で生成する:
 * <ol>
 *   <li>資本連結(子会社ごと): 投資と資本の相殺消去 → 前期以前ののれん償却・非支配株主持分の増減(開始仕訳)
 *       → 当期ののれん償却 → 子会社の当期純利益の非支配株主への按分 → 子会社の配当金の修正</li>
 *   <li>債権債務の相殺消去と貸倒引当金の修正</li>
 *   <li>取引高の相殺消去</li>
 *   <li>未実現利益の消去</li>
 * </ol>
 *
 * <p>前期以前の連結修正は、個別の帳簿に残っていないので、毎期「開始仕訳」として作り直す。
 * 開始仕訳では、前期以前の損益の修正を利益剰余金(繰越利益剰余金)の修正として行う。
 *
 * <p>子会社の支配獲得日は子会社の決算日とする。支配獲得日が当期末の子会社は、貸借対照表だけを連結する
 * (損益計算書は連結しない)。連結修正に伴う税効果は考慮しない。
 */
public final class ConsolidationService {

    private final RoundingPolicy rounding;

    /** 端数を円未満切り捨てで丸める。 */
    public ConsolidationService() {
        this(RoundingPolicy.roundDown());
    }

    public ConsolidationService(RoundingPolicy rounding) {
        if (rounding == null) {
            throw new AccountingException("rounding is required");
        }
        this.rounding = rounding;
    }

    public ConsolidationResult consolidate(ConsolidationInput input) {
        LocalDate end = input.fiscalYearEnd();
        LocalDate start = end.minusYears(1).plusDays(1);
        Map<CompanyId, Subsidiary> subsidiaries = subsidiariesById(input);
        Map<CompanyId, IndividualStatements> statements = statementsById(input, subsidiaries);

        Map<CompanyId, Map<Account, Money>> individualBalances = new LinkedHashMap<>();
        individualBalances.put(input.parent().company(), input.parent().balances());
        List<Journal> adjustments = new ArrayList<>();
        Context context = new Context(start, end, input.parent().company(), subsidiaries);

        for (Subsidiary subsidiary : input.subsidiaries()) {
            IndividualStatements subsidiaryStatements = statements.get(subsidiary.company());
            boolean atAcquisition = subsidiary.acquisitionDate().equals(end);
            individualBalances.put(subsidiary.company(), atAcquisition
                    ? subsidiaryStatements.balanceSheetBalances()
                    : subsidiaryStatements.balances());
            adjustments.addAll(capitalConsolidation(context, subsidiary, subsidiaryStatements, atAcquisition));
        }
        for (IntercompanyBalance balance : input.intercompanyBalances()) {
            adjustments.addAll(eliminateBalance(context, balance));
        }
        for (IntercompanyTransaction transaction : input.intercompanyTransactions()) {
            context.add(adjustments, "取引高の相殺消去",
                    transaction.revenue(), transaction.expense(), transaction.amount());
        }
        for (UnrealizedProfit profit : input.unrealizedProfits()) {
            adjustments.addAll(switch (profit) {
                case UnrealizedProfit.Inventory inventory -> eliminateInventoryProfit(context, inventory);
                case UnrealizedProfit.Land land -> eliminateLandProfit(context, land);
            });
        }

        ConsolidationWorksheet worksheet = ConsolidationWorksheet.of(individualBalances, adjustments);
        ConsolidatedIncomeStatement incomeStatement = ConsolidatedIncomeStatement.of(worksheet);
        ConsolidatedBalanceSheet balanceSheet = ConsolidatedBalanceSheet.of(worksheet, incomeStatement);
        return new ConsolidationResult(adjustments, worksheet, incomeStatement, balanceSheet);
    }

    // ---- 資本連結 ----

    private List<Journal> capitalConsolidation(
            Context context, Subsidiary subsidiary, IndividualStatements statements, boolean atAcquisition) {
        int elapsedYears = elapsedYears(subsidiary, context.start, atAcquisition);
        String prefix = atAcquisition ? "" : "開始仕訳: ";
        List<Journal> journals = new ArrayList<>();

        journals.add(investmentElimination(context, subsidiary, atAcquisition, prefix));
        if (atAcquisition) {
            return journals;
        }

        Money annualAmortization = subsidiary.annualGoodwillAmortization(rounding);
        Money goodwill = subsidiary.goodwill(rounding);
        Money priorAmortization = Money.ZERO;
        if (elapsedYears > 0) {
            priorAmortization = min(Money.ofYen(annualAmortization.amountInYen() * elapsedYears), goodwill);
            context.add(journals, prefix + "前期以前ののれん償却",
                    StandardAccounts.RETAINED_EARNINGS, StandardAccounts.GOODWILL, priorAmortization);

            Money retainedEarningsIncrease = statements.openingRetainedEarnings()
                    .minus(retainedEarningsAtAcquisition(subsidiary));
            context.add(journals, prefix + "前期以前の子会社の利益剰余金の増減の非支配株主持分への振替",
                    StandardAccounts.RETAINED_EARNINGS, StandardAccounts.NON_CONTROLLING_INTERESTS,
                    nonControllingShare(subsidiary, retainedEarningsIncrease));
        }

        if (goodwill.isPositive()) {
            context.add(journals, "のれんの償却", StandardAccounts.GOODWILL_AMORTIZATION, StandardAccounts.GOODWILL,
                    min(annualAmortization, goodwill.minus(priorAmortization)));
        }
        context.add(journals, "子会社の当期純利益の非支配株主持分への振替",
                StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS,
                StandardAccounts.NON_CONTROLLING_INTERESTS,
                nonControllingShare(subsidiary, statements.netIncome()));

        Money dividends = statements.dividends();
        if (dividends.isPositive()) {
            Money nonControllingDividends = nonControllingShare(subsidiary, dividends);
            List<JournalLine> lines = new ArrayList<>();
            addLine(lines, JournalLine::debit, StandardAccounts.DIVIDEND_INCOME, dividends.minus(nonControllingDividends));
            addLine(lines, JournalLine::debit, StandardAccounts.NON_CONTROLLING_INTERESTS, nonControllingDividends);
            addLine(lines, JournalLine::credit, StandardAccounts.RETAINED_EARNINGS, dividends);
            journals.add(context.journal("子会社の配当金の修正", lines));
        }
        return journals;
    }

    /** 投資(子会社株式)と子会社の資本を相殺消去し、差額をのれん、親会社以外の持分を非支配株主持分にする。 */
    private Journal investmentElimination(Context context, Subsidiary subsidiary, boolean atAcquisition, String prefix) {
        List<JournalLine> lines = new ArrayList<>();
        for (Map.Entry<Account, Money> equity : subsidiary.equityAtAcquisition().entrySet()) {
            addSigned(lines, equity.getKey(), equity.getValue());
        }
        Money goodwill = subsidiary.goodwill(rounding);
        if (goodwill.isPositive()) {
            addLine(lines, JournalLine::debit, StandardAccounts.GOODWILL, goodwill);
        }
        addLine(lines, JournalLine::credit, StandardAccounts.SUBSIDIARY_STOCKS, subsidiary.investmentCost());
        addLine(lines, JournalLine::credit, StandardAccounts.NON_CONTROLLING_INTERESTS,
                subsidiary.nonControllingInterestAtAcquisition(rounding));
        if (goodwill.isNegative()) {
            // 負ののれんは支配獲得時の利益。翌期以降は利益剰余金(当期首)の修正になる。
            Account gain = atAcquisition ? StandardAccounts.GAIN_ON_BARGAIN_PURCHASE : StandardAccounts.RETAINED_EARNINGS;
            addLine(lines, JournalLine::credit, gain, goodwill.negate());
        }
        return context.journal(prefix + "投資と資本の相殺消去", lines);
    }

    /** 支配獲得日から当期首までの年数。支配獲得日は決算日(当期末または前期以前の期末)でなければならない。 */
    private static int elapsedYears(Subsidiary subsidiary, LocalDate start, boolean atAcquisition) {
        if (atAcquisition) {
            return 0;
        }
        LocalDate firstConsolidatedDay = subsidiary.acquisitionDate().plusDays(1);
        int years = 0;
        while (firstConsolidatedDay.plusYears(years).isBefore(start)) {
            years++;
        }
        if (!firstConsolidatedDay.plusYears(years).equals(start)) {
            throw new AccountingException(
                    "acquisition date must be a fiscal year end on or before the consolidation date: "
                            + subsidiary.acquisitionDate());
        }
        return years;
    }

    private static Money retainedEarningsAtAcquisition(Subsidiary subsidiary) {
        Money total = Money.ZERO;
        for (Map.Entry<Account, Money> equity : subsidiary.equityAtAcquisition().entrySet()) {
            if (equity.getKey().category() == AccountCategory.RETAINED_EARNINGS) {
                total = total.plus(equity.getValue());
            }
        }
        return total;
    }

    // ---- 債権債務・取引高 ----

    private List<Journal> eliminateBalance(Context context, IntercompanyBalance balance) {
        List<Journal> journals = new ArrayList<>();
        context.add(journals, "債権債務の相殺消去", balance.payable(), balance.receivable(), balance.amount());

        Subsidiary upstream = context.upstream(balance.creditor());
        Account allowance = StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS;
        Money beginning = balance.allowanceAtBeginning();
        context.add(journals, "開始仕訳: 貸倒引当金の修正", allowance, StandardAccounts.RETAINED_EARNINGS, beginning);
        Money change = balance.allowance().minus(beginning);
        context.add(journals, "貸倒引当金の修正", allowance, StandardAccounts.BAD_DEBT_EXPENSE, change);
        if (upstream != null) {
            context.add(journals, "開始仕訳: 貸倒引当金の修正の非支配株主持分への按分",
                    StandardAccounts.RETAINED_EARNINGS, StandardAccounts.NON_CONTROLLING_INTERESTS,
                    nonControllingShare(upstream, beginning));
            context.add(journals, "貸倒引当金の修正の非支配株主持分への按分",
                    StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS,
                    StandardAccounts.NON_CONTROLLING_INTERESTS, nonControllingShare(upstream, change));
        }
        return journals;
    }

    // ---- 未実現利益 ----

    private List<Journal> eliminateInventoryProfit(Context context, UnrealizedProfit.Inventory profit) {
        List<Journal> journals = new ArrayList<>();
        Subsidiary upstream = context.upstream(profit.seller());
        Account costOfSales = StandardAccounts.COST_OF_GOODS_SOLD;

        context.add(journals, "開始仕訳: 期首棚卸資産の未実現利益の消去",
                StandardAccounts.RETAINED_EARNINGS, profit.inventory(), profit.beginning());
        context.add(journals, "期首棚卸資産の未実現利益の実現", profit.inventory(), costOfSales, profit.beginning());
        context.add(journals, "期末棚卸資産の未実現利益の消去", costOfSales, profit.inventory(), profit.ending());
        if (upstream != null) {
            Money beginningShare = nonControllingShare(upstream, profit.beginning());
            Money endingShare = nonControllingShare(upstream, profit.ending());
            context.add(journals, "開始仕訳: 期首棚卸資産の未実現利益の非支配株主持分への按分",
                    StandardAccounts.NON_CONTROLLING_INTERESTS, StandardAccounts.RETAINED_EARNINGS, beginningShare);
            context.add(journals, "期首棚卸資産の未実現利益の実現の非支配株主持分への按分",
                    StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS,
                    StandardAccounts.NON_CONTROLLING_INTERESTS, beginningShare);
            context.add(journals, "期末棚卸資産の未実現利益の非支配株主持分への按分",
                    StandardAccounts.NON_CONTROLLING_INTERESTS,
                    StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS, endingShare);
        }
        return journals;
    }

    private List<Journal> eliminateLandProfit(Context context, UnrealizedProfit.Land profit) {
        if (profit.saleDate().isAfter(context.end)) {
            throw new AccountingException("land sale date is after the consolidation date: " + profit.saleDate());
        }
        boolean soldThisPeriod = !profit.saleDate().isBefore(context.start);
        List<Journal> journals = new ArrayList<>();
        Subsidiary upstream = context.upstream(profit.seller());
        Money share = upstream == null ? Money.ZERO : nonControllingShare(upstream, profit.amount());

        if (soldThisPeriod) {
            context.add(journals, "土地の未実現利益の消去",
                    StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS, StandardAccounts.LAND, profit.amount());
            context.add(journals, "土地の未実現利益の非支配株主持分への按分",
                    StandardAccounts.NON_CONTROLLING_INTERESTS,
                    StandardAccounts.PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS, share);
        } else {
            context.add(journals, "開始仕訳: 土地の未実現利益の消去",
                    StandardAccounts.RETAINED_EARNINGS, StandardAccounts.LAND, profit.amount());
            context.add(journals, "開始仕訳: 土地の未実現利益の非支配株主持分への按分",
                    StandardAccounts.NON_CONTROLLING_INTERESTS, StandardAccounts.RETAINED_EARNINGS, share);
        }
        return journals;
    }

    // ---- 補助 ----

    /** 金額 × 非支配株主の持分比率。負の金額(純損失など)も同じ比率で按分する。 */
    private Money nonControllingShare(Subsidiary subsidiary, Money amount) {
        Money share = amount.abs().times(subsidiary.nonControllingOwnership(), rounding);
        return amount.isNegative() ? share.negate() : share;
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** 資本の科目を借方に(負の金額なら貸方に)加える。 */
    private static void addSigned(List<JournalLine> lines, Account account, Money amount) {
        if (amount.isPositive()) {
            lines.add(JournalLine.debit(account, amount));
        } else if (amount.isNegative()) {
            lines.add(JournalLine.credit(account, amount.negate()));
        }
    }

    private static void addLine(List<JournalLine> lines,
                                BiFunction<Account, Money, JournalLine> factory,
                                Account account, Money amount) {
        if (amount.isPositive()) {
            lines.add(factory.apply(account, amount));
        }
    }

    private static Map<CompanyId, Subsidiary> subsidiariesById(ConsolidationInput input) {
        Map<CompanyId, Subsidiary> result = new LinkedHashMap<>();
        for (Subsidiary subsidiary : input.subsidiaries()) {
            if (subsidiary.company().equals(input.parent().company())) {
                throw new AccountingException("parent cannot be its own subsidiary");
            }
            if (result.put(subsidiary.company(), subsidiary) != null) {
                throw new AccountingException("duplicate subsidiary: " + subsidiary.company().value());
            }
            if (subsidiary.acquisitionDate().isAfter(input.fiscalYearEnd())) {
                throw new AccountingException("subsidiary acquired after the consolidation date: "
                        + subsidiary.acquisitionDate());
            }
        }
        return result;
    }

    private static Map<CompanyId, IndividualStatements> statementsById(
            ConsolidationInput input, Map<CompanyId, Subsidiary> subsidiaries) {
        Map<CompanyId, IndividualStatements> result = new LinkedHashMap<>();
        for (IndividualStatements statements : input.subsidiaryStatements()) {
            if (!subsidiaries.containsKey(statements.company())) {
                throw new AccountingException("statements of unknown subsidiary: " + statements.company().value());
            }
            if (result.put(statements.company(), statements) != null) {
                throw new AccountingException("duplicate statements: " + statements.company().value());
            }
        }
        if (result.size() != subsidiaries.size()) {
            throw new AccountingException("statements are required for every subsidiary");
        }
        return result;
    }

    /** 連結期間と連結会社。連結修正仕訳を作る補助。 */
    private record Context(LocalDate start, LocalDate end, CompanyId parent, Map<CompanyId, Subsidiary> subsidiaries) {

        /**
         * 売った側(債権者)が子会社ならその子会社(アップストリーム)、親会社なら null(ダウンストリーム)。
         * 連結会社でなければ例外。
         */
        private Subsidiary upstream(CompanyId seller) {
            Subsidiary subsidiary = subsidiaries.get(seller);
            if (subsidiary == null && !parent.equals(seller)) {
                throw new AccountingException("not a consolidated company: " + seller.value());
            }
            return subsidiary;
        }

        /**
         * 「借方 / 貸方」の2行の連結修正仕訳を加える。金額がゼロなら何もしない。負なら借方と貸方を入れ替える。
         */
        private void add(List<Journal> journals, String description, Account debit, Account credit, Money amount) {
            if (amount.isZero()) {
                return;
            }
            Money absolute = amount.abs();
            Account d = amount.isPositive() ? debit : credit;
            Account c = amount.isPositive() ? credit : debit;
            journals.add(journal(description, List.of(JournalLine.debit(d, absolute), JournalLine.credit(c, absolute))));
        }

        private Journal journal(String description, List<JournalLine> lines) {
            return Journal.of(JournalId.newId(), end, description, JournalType.CONSOLIDATION, lines);
        }
    }
}
