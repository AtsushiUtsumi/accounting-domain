package com.example.accounting.service;

import com.example.accounting.model.journal.PostedJournal;
import com.example.accounting.model.period.AccountingPeriod;

import java.util.List;

/**
 * {@link ClosingService#close} の結果。仕訳は記帳済み({@link PostedJournal})で、保存は呼び出し側の責務。
 *
 * <p>保存は1つのトランザクションで、次の順に行うこと: {@code closingEntries} →
 * {@code closedPeriod} → {@code nextPeriod} → {@code openingEntries}。損益振替仕訳は締める前の当期の
 * version で記帳されているため、当期を CLOSED で保存する前に保存しないと、
 * {@link com.example.accounting.model.journal.JournalRepository#saveAll} の version 検証で失敗する。
 *
 * @param closingEntries 損益振替仕訳(収益・費用の損益勘定への振替、当期純利益・純損失の繰越利益剰余金への振替)。
 *                       締める前の当期に、期末日付で記帳済み
 * @param openingEntries 次期の開始仕訳(資産・負債・純資産の期末残高を次期期首日付で引き継ぐ)。次期に記帳済み
 * @param closedPeriod   CLOSED 状態になった当期
 * @param nextPeriod     新しく生成された次期(OPEN 状態)
 */
public record ClosingResult(
        List<PostedJournal> closingEntries,
        List<PostedJournal> openingEntries,
        AccountingPeriod closedPeriod,
        AccountingPeriod nextPeriod
) {

    public ClosingResult {
        closingEntries = List.copyOf(closingEntries);
        openingEntries = List.copyOf(openingEntries);
    }
}
