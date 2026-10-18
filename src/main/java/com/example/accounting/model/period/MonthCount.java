package com.example.accounting.model.period;

import com.example.accounting.exception.AccountingException;

import java.time.LocalDate;

/**
 * 月割計算の月数。減価償却費・償却原価法などで、1か月未満の端数を1か月として数える。
 * 例: 10月15日〜3月31日は6か月(10月を1か月と数える)。
 */
public final class MonthCount {

    private MonthCount() {
    }

    /** {@code from} を含む月から {@code to} を含む月までの月数(両端の月を含む)。 */
    public static int inclusive(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new AccountingException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new AccountingException("to must not be before from");
        }
        return (to.getYear() * 12 + to.getMonthValue()) - (from.getYear() * 12 + from.getMonthValue()) + 1;
    }

    /**
     * 会計期間のうち、{@code from}〜{@code to} と重なる部分の月数。重ならなければ 0。
     * 期中取得・期中売却の月割に使う。
     */
    public static int withinPeriod(AccountingPeriod period, LocalDate from, LocalDate to) {
        LocalDate start = from.isAfter(period.startDate()) ? from : period.startDate();
        LocalDate end = to.isBefore(period.endDate()) ? to : period.endDate();
        if (end.isBefore(start)) {
            return 0;
        }
        return inclusive(start, end);
    }
}
