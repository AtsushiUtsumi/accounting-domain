package com.example.accounting.service;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;

import java.time.LocalDate;
import java.util.List;

/**
 * 赤黒処理(訂正)のうち、取消仕訳(赤)を導出する。借方・貸方を入れ替えた仕訳を作ることで、
 * 元の仕訳を打ち消す。正しい仕訳(黒)は、呼び出し側が通常の仕訳として別に作成する。
 */
public final class ReversalService {

    public Journal reverse(JournalId newId, LocalDate date, Journal original) {
        if (original == null) {
            throw new AccountingException("original is required");
        }
        List<JournalLine> reversedLines = original.lines().stream()
                .map(line -> new JournalLine(line.account(), line.side().opposite(), line.amount()))
                .toList();
        return Journal.reversalOf(
                newId, date, "取消: " + original.description(), JournalType.REVERSAL, reversedLines, original.id());
    }
}
