package com.example.accounting.model.statement;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * フェーズ1の完了条件テスト。
 *
 * <p>「サンプル商店」4月の取引(日商簿記3級レベルの標準的な導入取引。開業出資・借入・
 * 掛け仕入・掛け売上・掛け代金の決済のみで構成し、仕訳の向きに解釈の余地がないもの):
 * <pre>
 * 4/1  現金600,000円を出資して開業した。      (借)現金600,000      / (貸)資本金600,000
 * 4/5  銀行から現金200,000円を借り入れた。    (借)現金200,000      / (貸)借入金200,000
 * 4/10 商品300,000円を掛けで仕入れた。        (借)仕入300,000      / (貸)買掛金300,000
 * 4/15 商品を280,000円で掛け売上した。        (借)売掛金280,000    / (貸)売上280,000
 * 4/20 買掛金150,000円を現金で支払った。      (借)買掛金150,000    / (貸)現金150,000
 * 4/25 売掛金180,000円を現金で回収した。      (借)現金180,000      / (貸)売掛金180,000
 * </pre>
 * 合計試算表の借方合計・貸方合計はいずれも1,710,000円になる。
 */
class TrialBalanceTest {

    @Test
    void trialBalance_debitTotalEqualsCreditTotal() {
        List<Journal> journals = List.of(
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 1), "出資による開業",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                                JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 5), "銀行からの借入",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(200_000)),
                                JournalLine.credit(StandardAccounts.LOANS_PAYABLE, Money.ofYen(200_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 10), "商品の掛け仕入",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(300_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(300_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 15), "商品の掛け売上",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(280_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(280_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 20), "買掛金の現金支払",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(150_000)),
                                JournalLine.credit(StandardAccounts.CASH, Money.ofYen(150_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 25), "売掛金の現金回収",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(180_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(180_000))
                        )
                )
        );

        TrialBalance trialBalance = TrialBalance.of(journals);

        assertThat(trialBalance.debitTotal()).isEqualTo(Money.ofYen(1_710_000));
        assertThat(trialBalance.creditTotal()).isEqualTo(Money.ofYen(1_710_000));
        assertThat(trialBalance.isBalanced()).isTrue();

        TrialBalance.Row cash = trialBalance.rows().get(StandardAccounts.CASH);
        assertThat(cash.debitTotal()).isEqualTo(Money.ofYen(980_000));
        assertThat(cash.creditTotal()).isEqualTo(Money.ofYen(150_000));

        TrialBalance.Row accountsReceivable = trialBalance.rows().get(StandardAccounts.ACCOUNTS_RECEIVABLE);
        assertThat(accountsReceivable.debitTotal()).isEqualTo(Money.ofYen(280_000));
        assertThat(accountsReceivable.creditTotal()).isEqualTo(Money.ofYen(180_000));

        TrialBalance.Row accountsPayable = trialBalance.rows().get(StandardAccounts.ACCOUNTS_PAYABLE);
        assertThat(accountsPayable.debitTotal()).isEqualTo(Money.ofYen(150_000));
        assertThat(accountsPayable.creditTotal()).isEqualTo(Money.ofYen(300_000));

        TrialBalance.Row loansPayable = trialBalance.rows().get(StandardAccounts.LOANS_PAYABLE);
        assertThat(loansPayable.creditTotal()).isEqualTo(Money.ofYen(200_000));

        TrialBalance.Row capitalStock = trialBalance.rows().get(StandardAccounts.CAPITAL_STOCK);
        assertThat(capitalStock.creditTotal()).isEqualTo(Money.ofYen(600_000));

        TrialBalance.Row sales = trialBalance.rows().get(StandardAccounts.SALES);
        assertThat(sales.creditTotal()).isEqualTo(Money.ofYen(280_000));

        TrialBalance.Row purchases = trialBalance.rows().get(StandardAccounts.PURCHASES);
        assertThat(purchases.debitTotal()).isEqualTo(Money.ofYen(300_000));
    }
}
