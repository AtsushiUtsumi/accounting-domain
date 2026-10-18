package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.asset.FixedAsset;
import com.example.accounting.model.asset.IntangibleAsset;
import com.example.accounting.model.inventory.InventoryCount;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.securities.HeldToMaturityBond;
import com.example.accounting.model.statement.Ledger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 決算整理仕訳を導出する公開の入口。保存は行わない。個々の処理は副作用を持たず、
 * 生成すべき仕訳(または何もしないことを示す空の結果)を返すだけ。
 *
 * <p>決算整理には実行順序の依存があるため、このクラスのメソッドは次の順序で呼ぶこと:
 * <ol>
 *   <li>{@link #reverseAccruals}(期首)</li>
 *   <li>{@link #reclassifyBankOverdraft}</li>
 *   <li>{@link #recognizeCostOfSales}(三分法のみ) → {@link #recognizeInventoryLosses}</li>
 *   <li>{@link #depreciate}(有形固定資産ごと) → {@link #amortize}(無形固定資産ごと)</li>
 *   <li>有価証券の期末評価: {@link TradingSecuritiesService#valuateAtPeriodEnd} →
 *       {@link #amortizeHeldToMaturityBond} → {@link #valuateAvailableForSaleSecurities}</li>
 *   <li>{@link #adjustAllowanceForDoubtfulAccounts} → その他の引当金({@link #adjustProvision} など)</li>
 *   <li>経過勘定・貯蔵品(利用側が {@link JournalType#ADJUSTING} の仕訳で記帳する)</li>
 *   <li>{@link #accrueIncomeTax} → {@link #settleConsumptionTax}</li>
 * </ol>
 * 各メソッドの {@code journalsSoFar} には、その時点までに確定した仕訳(当期の通常仕訳 + それより前の
 * 決算整理仕訳)をすべて含めること。法人税等は税引前当期純利益に依存するため、損益に影響する整理の後に置く。
 */
public final class AdjustingEntryService {

    /**
     * 翌期首に再振替する決算整理仕訳の目印となる科目。経過勘定に加え、当座借越・貯蔵品・
     * その他有価証券評価差額金(洗替方式)を含む。
     */
    private static final Set<Account> REVERSIBLE_ACCOUNTS = Set.of(
            StandardAccounts.PREPAID_EXPENSE,
            StandardAccounts.UNEARNED_REVENUE,
            StandardAccounts.ACCRUED_EXPENSE,
            StandardAccounts.ACCRUED_REVENUE,
            StandardAccounts.BANK_OVERDRAFT,
            StandardAccounts.SUPPLIES,
            StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES
    );

    /**
     * 前期の決算整理仕訳のうち、翌期首に戻すもの(経過勘定・当座借越・貯蔵品・その他有価証券の評価替え)を、
     * 借方・貸方を入れ替えて戻す。{@link JournalType#ADJUSTING} 以外の仕訳は対象にしない。
     */
    public List<Journal> reverseAccruals(List<Journal> previousPeriodAdjustingJournals, LocalDate date) {
        List<Journal> result = new ArrayList<>();
        for (Journal journal : previousPeriodAdjustingJournals) {
            if (journal.type() != JournalType.ADJUSTING) {
                continue;
            }
            boolean reversible = journal.lines().stream()
                    .anyMatch(line -> REVERSIBLE_ACCOUNTS.contains(line.account()));
            if (!reversible) {
                continue;
            }
            List<JournalLine> reversedLines = journal.lines().stream()
                    .map(line -> new JournalLine(line.account(), line.side().opposite(), line.amount()))
                    .toList();
            result.add(Journal.of(
                    JournalId.newId(), date, "再振替: " + journal.description(), JournalType.REVERSING, reversedLines));
        }
        return result;
    }

    /**
     * 当座借越の振替。当座預金の残高が貸方(マイナス)のときだけ、その額を当座借越(負債)へ振り替える。
     * 翌期首に {@link #reverseAccruals} で再振替される。
     *
     * @param checkingAccount 当座預金の勘定(通常は {@link StandardAccounts#CHECKING_ACCOUNT})
     */
    public Optional<Journal> reclassifyBankOverdraft(
            Account checkingAccount, List<Journal> journalsSoFar, LocalDate date) {
        if (checkingAccount == null) {
            throw new AccountingException("checkingAccount is required");
        }
        Money balance = Ledger.of(checkingAccount, journalsSoFar).balance();
        if (!balance.isNegative()) {
            return Optional.empty();
        }
        Money overdraft = balance.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, "当座借越への振替", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(checkingAccount, overdraft),
                        JournalLine.credit(StandardAccounts.BANK_OVERDRAFT, overdraft))));
    }

    /** 三分法による売上原価の算定。期首・期末の商品棚卸高(期末は帳簿棚卸高)を仕入に加減する。 */
    public List<Journal> recognizeCostOfSales(Money beginningInventory, Money endingInventory, LocalDate date) {
        List<Journal> result = new ArrayList<>();
        if (beginningInventory.isPositive()) {
            result.add(Journal.of(
                    JournalId.newId(), date, "期首商品棚卸高を仕入に算入", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(StandardAccounts.PURCHASES, beginningInventory),
                            JournalLine.credit(StandardAccounts.MERCHANDISE_INVENTORY, beginningInventory))));
        }
        if (endingInventory.isPositive()) {
            result.add(Journal.of(
                    JournalId.newId(), date, "期末商品棚卸高を仕入から除く", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(StandardAccounts.MERCHANDISE_INVENTORY, endingInventory),
                            JournalLine.credit(StandardAccounts.PURCHASES, endingInventory))));
        }
        return result;
    }

    /**
     * 棚卸減耗損と商品評価損(三分法)。{@link #recognizeCostOfSales} で期末商品を帳簿棚卸高のまま繰越商品に
     * 振り替えた後に呼び、繰越商品から減耗分・評価損分を減らす。棚卸減耗損は販売費及び一般管理費、
     * 商品評価損は売上原価の内訳として扱う(標準科目の区分)。
     */
    public List<Journal> recognizeInventoryLosses(InventoryCount count, LocalDate date) {
        return recognizeInventoryLosses(
                count, StandardAccounts.MERCHANDISE_INVENTORY,
                StandardAccounts.INVENTORY_SHRINKAGE_LOSS, StandardAccounts.INVENTORY_VALUATION_LOSS, date);
    }

    /**
     * 棚卸減耗損と商品評価損。科目を指定する版。売上原価対立法なら {@code inventoryAccount} に
     * {@link StandardAccounts#MERCHANDISE} を、棚卸減耗損を売上原価に含めるなら、区分を売上原価にした
     * 独自の科目を {@code shrinkageLossAccount} に渡す。
     */
    public List<Journal> recognizeInventoryLosses(
            InventoryCount count, Account inventoryAccount,
            Account shrinkageLossAccount, Account valuationLossAccount, LocalDate date) {
        if (count == null || inventoryAccount == null || shrinkageLossAccount == null || valuationLossAccount == null) {
            throw new AccountingException("count and accounts are required");
        }
        List<Journal> result = new ArrayList<>();
        Money shrinkage = count.shrinkageLoss();
        if (shrinkage.isPositive()) {
            result.add(Journal.of(
                    JournalId.newId(), date, "棚卸減耗損の計上", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(shrinkageLossAccount, shrinkage),
                            JournalLine.credit(inventoryAccount, shrinkage))));
        }
        Money valuation = count.valuationLoss();
        if (valuation.isPositive()) {
            result.add(Journal.of(
                    JournalId.newId(), date, "商品評価損の計上", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(valuationLossAccount, valuation),
                            JournalLine.credit(inventoryAccount, valuation))));
        }
        return result;
    }

    /** 固定資産1件分の減価償却仕訳(間接法、1年分)。実体は {@link FixedAsset#depreciationEntry} に委譲する。 */
    public Optional<Journal> depreciate(FixedAsset asset, List<Journal> journalsSoFar, LocalDate date) {
        return asset.depreciationEntry(journalsSoFar, date);
    }

    /** 固定資産1件分の減価償却仕訳(間接法、月割)。期中に取得した資産に使う。 */
    public Optional<Journal> depreciate(FixedAsset asset, List<Journal> journalsSoFar, AccountingPeriod period) {
        return asset.depreciationEntry(journalsSoFar, period, period.endDate());
    }

    /** 無形固定資産1件分の償却仕訳(直接法、月割)。実体は {@link IntangibleAsset#amortizationEntry} に委譲する。 */
    public Optional<Journal> amortize(IntangibleAsset asset, List<Journal> journalsSoFar, AccountingPeriod period) {
        return asset.amortizationEntry(journalsSoFar, period);
    }

    /**
     * 満期保有目的債券の償却原価法(定額法)。金利調整差額のうち当期に配分される額を、債券の帳簿価額に
     * 加減し、相手を有価証券利息とする。調整額がなければ空。
     */
    public Optional<Journal> amortizeHeldToMaturityBond(
            HeldToMaturityBond bond, List<Journal> journalsSoFar, LocalDate date) {
        Money adjustment = bond.amortizationUpTo(date, journalsSoFar);
        if (adjustment.isZero()) {
            return Optional.empty();
        }
        if (adjustment.isPositive()) {
            return Optional.of(Journal.of(
                    JournalId.newId(), date, "満期保有目的債券の償却原価法(増額)", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(bond.bondAccount(), adjustment),
                            JournalLine.credit(StandardAccounts.INTEREST_ON_SECURITIES, adjustment))));
        }
        Money decrease = adjustment.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, "満期保有目的債券の償却原価法(減額)", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.INTEREST_ON_SECURITIES, decrease),
                        JournalLine.credit(bond.bondAccount(), decrease))));
    }

    /**
     * その他有価証券の期末評価(全部純資産直入法・洗替方式)。帳簿価額(取得原価)と時価の差額を、
     * その他有価証券評価差額金(純資産)として計上する。翌期首に {@link #reverseAccruals} で取得原価に戻る。
     *
     * @param securitiesAccount その他有価証券の勘定(銘柄ごとに評価するなら銘柄ごとの勘定)
     */
    public Optional<Journal> valuateAvailableForSaleSecurities(
            Account securitiesAccount, Money fairValue, List<Journal> journalsSoFar, LocalDate date) {
        if (securitiesAccount == null) {
            throw new AccountingException("securitiesAccount is required");
        }
        if (fairValue == null || fairValue.isNegative()) {
            throw new AccountingException("fairValue must not be negative");
        }
        Money bookValue = Ledger.of(securitiesAccount, journalsSoFar).balance();
        Money difference = fairValue.minus(bookValue);
        if (difference.isZero()) {
            return Optional.empty();
        }
        Account valuation = StandardAccounts.VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES;
        if (difference.isPositive()) {
            return Optional.of(Journal.of(
                    JournalId.newId(), date, "その他有価証券の評価替え(評価差益)", JournalType.ADJUSTING,
                    List.of(
                            JournalLine.debit(securitiesAccount, difference),
                            JournalLine.credit(valuation, difference))));
        }
        Money loss = difference.negate();
        return Optional.of(Journal.of(
                JournalId.newId(), date, "その他有価証券の評価替え(評価差損)", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(valuation, loss),
                        JournalLine.credit(securitiesAccount, loss))));
    }

    /** 貸倒引当金の差額補充法。必要額と現在残高の差額を繰入(不足)または戻入(過剰)する。 */
    public Optional<Journal> adjustAllowanceForDoubtfulAccounts(
            Money requiredBalance, List<Journal> journalsSoFar, LocalDate date) {
        return ProvisionCalculator.adjustTo(
                requiredBalance,
                StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS,
                StandardAccounts.BAD_DEBT_EXPENSE,
                StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS_REVERSAL,
                "貸倒引当金", journalsSoFar, date);
    }

    /** 引当金(賞与・修繕・商品保証など)の差額補充法。必要額と現在残高の差額を繰入または戻入する。 */
    public Optional<Journal> adjustProvision(
            ProvisionType type, Money requiredBalance, List<Journal> journalsSoFar, LocalDate date) {
        return ProvisionCalculator.adjustTo(
                requiredBalance, type.provisionAccount(), type.provisionExpenseAccount(), type.reversalAccount(),
                type.provisionAccount().name(), journalsSoFar, date);
    }

    /** 引当金の洗替法。現在の残高をすべて戻入し、必要額を改めて繰り入れる。 */
    public List<Journal> replaceProvision(
            ProvisionType type, Money requiredBalance, List<Journal> journalsSoFar, LocalDate date) {
        return ProvisionCalculator.replace(
                requiredBalance, type.provisionAccount(), type.provisionExpenseAccount(), type.reversalAccount(),
                type.provisionAccount().name(), journalsSoFar, date);
    }

    /** 退職給付引当金の繰入。当期の退職給付費用の額を与える(残高ではない)。 */
    public Journal accrueRetirementBenefits(Money expenseForThisPeriod, LocalDate date) {
        if (expenseForThisPeriod == null || !expenseForThisPeriod.isPositive()) {
            throw new AccountingException("expenseForThisPeriod must be positive");
        }
        return Journal.of(
                JournalId.newId(), date, "退職給付引当金の繰入", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(StandardAccounts.RETIREMENT_BENEFIT_EXPENSE, expenseForThisPeriod),
                        JournalLine.credit(StandardAccounts.PROVISION_FOR_RETIREMENT_BENEFITS, expenseForThisPeriod)));
    }

    /** 法人税等の計上。確定額と仮払法人税等(中間納付)の差額を未払法人税等とする。 */
    public Journal accrueIncomeTax(Money determinedTaxAmount, List<Journal> journalsSoFar, LocalDate date) {
        Money prepaid = Ledger.of(StandardAccounts.PREPAID_CORPORATE_TAX, journalsSoFar).balance();
        Money payable = determinedTaxAmount.minus(prepaid);
        if (payable.isNegative()) {
            throw new AccountingException(
                    "prepaid corporate tax exceeds the determined tax amount (refund case is out of scope): "
                            + payable.amountInYen());
        }

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(StandardAccounts.CORPORATE_TAX_EXPENSE, determinedTaxAmount));
        if (prepaid.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.PREPAID_CORPORATE_TAX, prepaid));
        }
        if (payable.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.ACCRUED_CORPORATE_TAX, payable));
        }
        return Journal.of(JournalId.newId(), date, "法人税等の計上", JournalType.ADJUSTING, lines);
    }

    /** 消費税(税抜方式)の精算。仮受消費税と仮払消費税の差額を未払(または未収)消費税とする。 */
    public Optional<Journal> settleConsumptionTax(List<Journal> journalsSoFar, LocalDate date) {
        Money received = Ledger.of(StandardAccounts.CONSUMPTION_TAX_RECEIVED, journalsSoFar).balance();
        Money paid = Ledger.of(StandardAccounts.CONSUMPTION_TAX_PAID, journalsSoFar).balance();
        if (received.isZero() && paid.isZero()) {
            return Optional.empty();
        }

        Money net = received.minus(paid);
        List<JournalLine> lines = new ArrayList<>();
        if (received.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.CONSUMPTION_TAX_RECEIVED, received));
        }
        if (paid.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.CONSUMPTION_TAX_PAID, paid));
        }
        if (net.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.CONSUMPTION_TAX_PAYABLE, net));
        } else if (net.isNegative()) {
            lines.add(JournalLine.debit(StandardAccounts.CONSUMPTION_TAX_REFUNDABLE, net.negate()));
        }
        return Optional.of(Journal.of(JournalId.newId(), date, "消費税の精算", JournalType.ADJUSTING, lines));
    }
}
