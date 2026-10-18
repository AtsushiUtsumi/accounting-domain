package com.example.accounting.model.asset;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.period.MonthCount;
import com.example.accounting.model.statement.Ledger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 固定資産。累計償却額は保存せず、この資産の減価償却仕訳の履歴から毎回導出する。
 *
 * <p>そのため {@code assetAccount}・{@code accumulatedDepreciationAccount} は、この資産専用の勘定
 * (他の資産と共有しない)を指定すること。複数の資産で同じ勘定を共有すると、導出される累計償却額や
 * 売却仕訳の金額に他資産の分が混ざる。
 */
public record FixedAsset(
        FixedAssetId id,
        String name,
        Account assetAccount,
        Account accumulatedDepreciationAccount,
        Account depreciationExpenseAccount,
        Money acquisitionCost,
        LocalDate acquisitionDate,
        int usefulLifeYears,
        Money residualValue,
        DepreciationPolicy depreciationPolicy
) {

    public FixedAsset {
        if (id == null) {
            throw new AccountingException("id is required");
        }
        if (name == null || name.isBlank()) {
            throw new AccountingException("name is required");
        }
        if (assetAccount == null) {
            throw new AccountingException("assetAccount is required");
        }
        if (accumulatedDepreciationAccount == null) {
            throw new AccountingException("accumulatedDepreciationAccount is required");
        }
        if (depreciationExpenseAccount == null) {
            throw new AccountingException("depreciationExpenseAccount is required");
        }
        if (acquisitionCost == null || !acquisitionCost.isPositive()) {
            throw new AccountingException("acquisitionCost must be positive");
        }
        if (acquisitionDate == null) {
            throw new AccountingException("acquisitionDate is required");
        }
        if (usefulLifeYears <= 0) {
            throw new AccountingException("usefulLifeYears must be positive");
        }
        if (residualValue == null || residualValue.isNegative() || residualValue.compareTo(acquisitionCost) > 0) {
            throw new AccountingException("residualValue must be between zero and acquisitionCost");
        }
        if (depreciationPolicy == null) {
            throw new AccountingException("depreciationPolicy is required");
        }
    }

    public Money depreciableAmount() {
        return acquisitionCost.minus(residualValue);
    }

    public Money accumulatedDepreciation(List<Journal> journalsSoFar) {
        // accumulatedDepreciationAccount は contra-asset(貸方残高が正常)なので、
        // ASSET の正常側(借方)基準で出る Ledger.balance() の符号を反転して「償却累計額」の正の値に直す。
        return Ledger.of(accumulatedDepreciationAccount, journalsSoFar).balance().negate();
    }

    public Money bookValue(List<Journal> journalsSoFar) {
        return acquisitionCost.minus(accumulatedDepreciation(journalsSoFar));
    }

    /**
     * この期の減価償却仕訳(間接法)を1年分で導出する。償却可能限度額まで償却済みなら空を返す。
     * 期中に取得・売却した資産は {@link #depreciationEntry(List, AccountingPeriod, LocalDate)} を使うこと。
     */
    public Optional<Journal> depreciationEntry(List<Journal> journalsSoFar, LocalDate date) {
        Money accumulatedSoFar = accumulatedDepreciation(journalsSoFar);
        return entryFor(depreciationPolicy.depreciationFor(this, accumulatedSoFar), accumulatedSoFar, date);
    }

    /**
     * 月割の減価償却仕訳(間接法)。会計期間のうち、取得日(期首より後なら)から {@code until} までの月数分
     * (1か月未満は1か月)を、1年分の償却費 × 月数 ÷ 12(円未満切り捨て)で計上する。
     *
     * @param period 当期の会計期間
     * @param until  償却の終わりの日。通常は期末日、期中売却なら売却日
     */
    public Optional<Journal> depreciationEntry(List<Journal> journalsSoFar, AccountingPeriod period, LocalDate until) {
        if (period == null || until == null) {
            throw new AccountingException("period and until are required");
        }
        int months = MonthCount.withinPeriod(period, acquisitionDate, until);
        if (months == 0) {
            return Optional.empty();
        }
        Money accumulatedSoFar = accumulatedDepreciation(journalsSoFar);
        Money annual = depreciationPolicy.depreciationFor(this, accumulatedSoFar);
        Money prorated = Money.ofYen(Math.floorDiv(annual.amountInYen() * Math.min(months, 12), 12));
        return entryFor(prorated, accumulatedSoFar, until);
    }

    /** 生産高比例法など、利用量にもとづく減価償却仕訳(間接法)。月割はしない。 */
    public Optional<Journal> depreciationEntryByUsage(List<Journal> journalsSoFar, long usageThisPeriod, LocalDate date) {
        if (!(depreciationPolicy instanceof UsageBasedDepreciationPolicy usageBased)) {
            throw new AccountingException("depreciation policy of " + name + " is not usage-based");
        }
        Money accumulatedSoFar = accumulatedDepreciation(journalsSoFar);
        return entryFor(usageBased.depreciationForUsage(this, usageThisPeriod), accumulatedSoFar, date);
    }

    private Optional<Journal> entryFor(Money candidate, Money accumulatedSoFar, LocalDate date) {
        Money remaining = depreciableAmount().minus(accumulatedSoFar);
        if (!remaining.isPositive()) {
            return Optional.empty();
        }
        Money depreciation = candidate.compareTo(remaining) > 0 ? remaining : candidate;
        if (!depreciation.isPositive()) {
            return Optional.empty();
        }

        Journal journal = Journal.of(
                JournalId.newId(), date, name + "の減価償却(間接法)", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(depreciationExpenseAccount, depreciation),
                        JournalLine.credit(accumulatedDepreciationAccount, depreciation)
                )
        );
        return Optional.of(journal);
    }

    /**
     * 固定資産の売却(間接法)。売却時点までの減価償却(当期分を含む)は、このメソッドを呼ぶ前に
     * {@link #depreciationEntry} 等で別途記録しておくこと。帳簿価額と売却価額の差額を
     * 固定資産売却益・固定資産売却損のいずれかとして計上する(一致する場合はどちらも計上しない)。
     *
     * @param journalsSoFar   この資産に関するこれまでの仕訳(売却時点までの減価償却を含む)
     * @param date            売却日
     * @param proceedsAccount 売却代金を受け取る勘定(現金・未収入金など)
     * @param proceeds        売却価額
     */
    public Journal disposalEntry(List<Journal> journalsSoFar, LocalDate date, Account proceedsAccount, Money proceeds) {
        if (proceedsAccount == null) {
            throw new AccountingException("proceedsAccount is required");
        }
        if (proceeds == null || proceeds.isNegative()) {
            throw new AccountingException("proceeds must not be negative");
        }

        Money accumulated = accumulatedDepreciation(journalsSoFar);
        Money bookValueAtSale = acquisitionCost.minus(accumulated);
        Money gainOrLoss = proceeds.minus(bookValueAtSale);

        List<JournalLine> lines = new ArrayList<>();
        if (accumulated.isPositive()) {
            lines.add(JournalLine.debit(accumulatedDepreciationAccount, accumulated));
        }
        if (proceeds.isPositive()) {
            lines.add(JournalLine.debit(proceedsAccount, proceeds));
        }
        if (gainOrLoss.isNegative()) {
            lines.add(JournalLine.debit(StandardAccounts.LOSS_ON_DISPOSAL_OF_FIXED_ASSETS, gainOrLoss.negate()));
        }
        lines.add(JournalLine.credit(assetAccount, acquisitionCost));
        if (gainOrLoss.isPositive()) {
            lines.add(JournalLine.credit(StandardAccounts.GAIN_ON_DISPOSAL_OF_FIXED_ASSETS, gainOrLoss));
        }

        return Journal.of(JournalId.newId(), date, name + "の売却", JournalType.NORMAL, lines);
    }

    /**
     * 固定資産の除却(使用をやめて倉庫などに保管する)。帳簿価額のうち、見積処分価額を貯蔵品とし、
     * 残りを固定資産除却損とする。除却日までの減価償却は、このメソッドを呼ぶ前に記録しておくこと。
     *
     * @param estimatedDisposalValue 見積処分価額(売却できる見込みの額。なければゼロ)
     */
    public Journal retirementEntry(List<Journal> journalsSoFar, LocalDate date, Money estimatedDisposalValue) {
        if (estimatedDisposalValue == null || estimatedDisposalValue.isNegative()) {
            throw new AccountingException("estimatedDisposalValue must not be negative");
        }
        Money accumulated = accumulatedDepreciation(journalsSoFar);
        Money bookValueAtRetirement = acquisitionCost.minus(accumulated);
        if (estimatedDisposalValue.compareTo(bookValueAtRetirement) > 0) {
            throw new AccountingException("estimatedDisposalValue must not exceed the book value");
        }
        Money loss = bookValueAtRetirement.minus(estimatedDisposalValue);

        List<JournalLine> lines = new ArrayList<>();
        if (accumulated.isPositive()) {
            lines.add(JournalLine.debit(accumulatedDepreciationAccount, accumulated));
        }
        if (estimatedDisposalValue.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.SUPPLIES, estimatedDisposalValue));
        }
        if (loss.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.LOSS_ON_RETIREMENT_OF_FIXED_ASSETS, loss));
        }
        lines.add(JournalLine.credit(assetAccount, acquisitionCost));
        return Journal.of(JournalId.newId(), date, name + "の除却", JournalType.NORMAL, lines);
    }

    /**
     * 固定資産の廃棄(捨てる)。帳簿価額と廃棄にかかった費用の合計を固定資産廃棄損とする。
     * 廃棄日までの減価償却は、このメソッドを呼ぶ前に記録しておくこと。
     *
     * @param disposalCost   廃棄費用(なければゼロ)
     * @param paymentAccount 廃棄費用を支払う勘定。廃棄費用がゼロなら null でよい
     */
    public Journal abandonmentEntry(List<Journal> journalsSoFar, LocalDate date,
                                    Money disposalCost, Account paymentAccount) {
        if (disposalCost == null || disposalCost.isNegative()) {
            throw new AccountingException("disposalCost must not be negative");
        }
        if (disposalCost.isPositive() && paymentAccount == null) {
            throw new AccountingException("paymentAccount is required when disposalCost is positive");
        }
        Money accumulated = accumulatedDepreciation(journalsSoFar);
        Money loss = acquisitionCost.minus(accumulated).plus(disposalCost);

        List<JournalLine> lines = new ArrayList<>();
        if (accumulated.isPositive()) {
            lines.add(JournalLine.debit(accumulatedDepreciationAccount, accumulated));
        }
        if (loss.isPositive()) {
            lines.add(JournalLine.debit(StandardAccounts.LOSS_ON_ABANDONMENT_OF_FIXED_ASSETS, loss));
        }
        lines.add(JournalLine.credit(assetAccount, acquisitionCost));
        if (disposalCost.isPositive()) {
            lines.add(JournalLine.credit(paymentAccount, disposalCost));
        }
        return Journal.of(JournalId.newId(), date, name + "の廃棄", JournalType.NORMAL, lines);
    }

    /**
     * 圧縮記帳(直接減額方式)。国庫補助金の額だけ取得価額を減らし、固定資産圧縮損を計上する。
     * 以後の減価償却は、圧縮後の取得価額をもとに行う(返される {@link Compression#compressedAsset()} を使う)。
     * 減価償却を始める前(取得時)に行う前提。国庫補助金の受け取り自体は通常の仕訳で記録すること。
     *
     * @param reduction 圧縮額(通常は受け取った国庫補助金の額)
     */
    public Compression compress(Money reduction, LocalDate date) {
        if (reduction == null || !reduction.isPositive()) {
            throw new AccountingException("reduction must be positive");
        }
        Money compressedCost = acquisitionCost.minus(reduction);
        if (!compressedCost.isPositive() || compressedCost.compareTo(residualValue) < 0) {
            throw new AccountingException("reduction leaves the acquisition cost below the residual value");
        }
        FixedAsset compressed = new FixedAsset(
                id, name, assetAccount, accumulatedDepreciationAccount, depreciationExpenseAccount,
                compressedCost, acquisitionDate, usefulLifeYears, residualValue, depreciationPolicy);
        Journal journal = Journal.of(
                JournalId.newId(), date, name + "の圧縮記帳(直接減額方式)", JournalType.NORMAL,
                List.of(
                        JournalLine.debit(StandardAccounts.LOSS_ON_COMPRESSION_OF_FIXED_ASSETS, reduction),
                        JournalLine.credit(assetAccount, reduction)));
        return new Compression(compressed, journal);
    }

    /** 圧縮記帳の結果。圧縮後の固定資産と、圧縮損の仕訳。 */
    public record Compression(FixedAsset compressedAsset, Journal entry) {
    }
}
