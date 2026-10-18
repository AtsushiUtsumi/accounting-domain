package com.example.accounting.model.journal;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.period.AccountingPeriodId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 会計期間に記帳された仕訳。OPEN の会計期間の、期間内の日付でしか作れない({@link #post})。
 * 保存できるのはこの型だけなので({@link JournalRepository})、締めた期に仕訳が追加されることはない。
 *
 * <p>作成しただけの {@link Journal} は下書きにあたり、試算表などの計算(プレビュー)には自由に使える。
 * 帳簿に載せるときに {@link #post} を通す。
 *
 * <p>仕訳の種類によって、記帳できる日付がさらに決まっている: 開始仕訳・再振替仕訳は期首の日付、
 * 損益振替仕訳は期末の日付でなければならない。
 */
public final class PostedJournal {

    private final Journal journal;
    private final AccountingPeriodId periodId;
    private final long periodVersion;

    private PostedJournal(Journal journal, AccountingPeriodId periodId, long periodVersion) {
        if (journal == null) {
            throw new AccountingException("journal is required");
        }
        if (periodId == null) {
            throw new AccountingException("periodId is required");
        }
        this.journal = journal;
        this.periodId = periodId;
        this.periodVersion = periodVersion;
    }

    /** 記帳。会計期間が OPEN で、仕訳の日付が期間内(種類によっては期首・期末)であること。 */
    public static PostedJournal post(AccountingPeriod period, Journal journal) {
        if (period == null) {
            throw new AccountingException("period is required");
        }
        if (journal == null) {
            throw new AccountingException("journal is required");
        }
        if (!period.isOpen()) {
            throw new AccountingException("cannot post to a closed period: " + period.id().value());
        }
        LocalDate date = journal.date();
        if (!period.contains(date)) {
            throw new AccountingException(
                    "journal date %s is outside the period %s to %s"
                            .formatted(date, period.startDate(), period.endDate()));
        }
        switch (journal.type()) {
            case OPENING, REVERSING -> requireDate(journal, period.startDate(), "the first day of the period");
            case CLOSING -> requireDate(journal, period.endDate(), "the last day of the period");
            default -> {
                // 通常・決算整理・取消は期間内のどの日付でもよい
                // (期中売却の減価償却のように、決算整理でも期末以外の日付になることがある)。
            }
        }
        return new PostedJournal(journal, period.id(), period.version());
    }

    /** 複数の仕訳をまとめて記帳する。1件でも記帳できなければ例外(どれも記帳しない)。 */
    public static List<PostedJournal> postAll(AccountingPeriod period, List<Journal> journals) {
        List<PostedJournal> result = new ArrayList<>();
        for (Journal journal : journals) {
            result.add(post(period, journal));
        }
        return List.copyOf(result);
    }

    /**
     * 保存済みの仕訳の復元。リポジトリの実装だけが使うこと(新しい記帳には {@link #post} を使う)。
     * 締めた期の仕訳も読み出せるよう、会計期間の状態は検証しない。
     */
    public static PostedJournal restore(Journal journal, AccountingPeriodId periodId, long periodVersion) {
        return new PostedJournal(journal, periodId, periodVersion);
    }

    /** 計算(試算表・元帳など)に渡すために、記帳済みの仕訳の一覧を中身の仕訳の一覧へ戻す。 */
    public static List<Journal> journalsOf(List<PostedJournal> postedJournals) {
        return postedJournals.stream().map(PostedJournal::journal).toList();
    }

    public Journal journal() {
        return journal;
    }

    public JournalId id() {
        return journal.id();
    }

    public LocalDate date() {
        return journal.date();
    }

    /** 記帳先の会計期間。 */
    public AccountingPeriodId periodId() {
        return periodId;
    }

    /**
     * 記帳した時点の会計期間の version。保存時に会計期間の version と比べ、記帳の後に期間が締められて
     * いないかを確かめるのに使う({@link JournalRepository#saveAll} を参照)。
     */
    public long periodVersion() {
        return periodVersion;
    }

    private static void requireDate(Journal journal, LocalDate required, String description) {
        if (!journal.date().equals(required)) {
            throw new AccountingException(
                    "%s journal must be dated %s (%s), but was %s"
                            .formatted(journal.type(), description, required, journal.date()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostedJournal other)) {
            return false;
        }
        return journal.equals(other.journal) && periodId.equals(other.periodId) && periodVersion == other.periodVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(journal, periodId, periodVersion);
    }

    @Override
    public String toString() {
        return "PostedJournal[" + journal.id().value() + " in period " + periodId.value() + "]";
    }
}
