# accounting-domain 追加実装設計(フェーズ5以降)

フェーズ1〜4の完了時点(72テスト、commit `e58b7c7`)から、簿記3級の残りと2級商業簿記(CLAUDE.md の範囲内)を埋めるための設計。

## この文書の読み方(重要)

- CLAUDE.md の最重要前提は「**会計ルールを推測で実装しない**」。この文書に書いた**仕訳の形・計算式・科目の区分は、すべて「想定」**で、正解ではない。
- 各論点の「**要確認**」は、着手する前に、開発者がテキスト・問題集の解答例で確かめる事項。答えが出るまで、その論点は実装しない。
- テストの期待値は、この文書ではなく**問題集の解答例**から取る。
- 試験の範囲は作成者の知識にもとづく整理。最新の出題区分表で確認すること。

## 既存の設計方針(引き続き守る)

- **計算を伴う処理だけをサービスにする**。単純な2行仕訳(例: 給料を現金で支払う)には専用メソッドを作らず、利用側が `Journal.of(...)` で組む。
- サービスは保存せず、「生成すべき仕訳」を返す。何もしない場合は `Optional.empty()` を返す。
- 導出できる値は保存しない(例: 累計償却額は仕訳から導出する)。
- 評価勘定(貸倒引当金、減価償却累計額など)は `type = ASSET` で、正常な残高は貸方。`Ledger.balance()` は符号が逆になるので、残高として使うときは `negate()` する。
- 決算整理は実行順序に依存する。順序は `AdjustingEntryService` の Javadoc で固定する。論点を追加したら、Javadoc の順序も更新する。

## 全体のロードマップ

| フェーズ | 内容 | 試験範囲 | 優先度 |
|---|---|---|---|
| 5 | リポジトリのインターフェース、標準科目の拡充 | 3級 | 高 |
| 6 | 3級の残り(貸倒れ、月割償却、当座借越、貯蔵品) | 3級 | 高 |
| 7 | 2級の決算整理の中心(棚卸減耗・商品評価損、各種引当金) | 2級 | 高 |
| 8 | 売上原価対立法 | 2級 | 中 |
| 9 | 固定資産の諸論点 | 2級 | 中 |
| 10 | 有価証券の残りの区分 | 2級 | 中 |
| 11 | 手形・電子記録債権の諸論点、銀行勘定調整表 | 2級 | 中 |
| 12 | 純資産(任意積立金、その他資本剰余金、合併) | 2級 | 低 |
| 13 | 収益認識(役務収益、契約資産、返金負債) | 2級 | 低 |
| (別枠) | 実務向けの拡張 | 試験範囲外 | 必要になってから |

CLAUDE.md の方針どおり、**1フェーズごとにテストが通る状態で止まり、開発者に確認する**。

---

## フェーズ5: 土台の整備

### 5.1 リポジトリのインターフェース

CLAUDE.md には書かれているのに、まだ存在しないリポジトリのインターフェースを追加する。置き場所は、扱う集約と同じ `model` 配下のパッケージとする(当初は `repository` パッケージだったが、集約の隣に移した)。Spring Boot アプリの設計(`設計.md` §4)で未決だった「どこに置くか」は、**ライブラリに置く**(案A)で決着とする。

```
com.example.accounting.model
 ├─ journal.JournalRepository          save / saveAll / findById / findByPeriod(update・delete なし)
 ├─ period.AccountingPeriodRepository  findById / save
 ├─ account.AccountRepository          findByCode / findAll / save / existsByCode
 └─ asset.FixedAssetRepository         findById / findAll / save
```

- `saveAll` の Javadoc に契約を書く: 「全件保存するか、1件も保存しないか」。
- テスト用に、インメモリ実装を `src/test` に置く(main には入れない)。
- `findContaining(LocalDate)` などのアプリ都合のメソッドは、ここには入れない(必要ならアプリ側で拡張する)。

### 5.2 標準科目の拡充

計算ロジックは不要。`StandardAccounts` に追加するだけ。ただし**区分(`AccountCategory`)は利益計算に影響する**ので、全科目について区分を確認する。

コード体系は既存に合わせる: 1xx 資産、2xx 負債、3xx 純資産、4xx 収益、5xx 費用、9xx 特殊。

| コード | 科目 | type | category(想定) |
|---|---|---|---|
| 115 | 当座預金 | ASSET | CURRENT_ASSET |
| 116 | 普通預金 | ASSET | CURRENT_ASSET |
| 117 | 定期預金 | ASSET | CURRENT_ASSET ※1年超の場合は要確認 |
| 118 | 小口現金 | ASSET | CURRENT_ASSET |
| 119 | 前払金 | ASSET | CURRENT_ASSET |
| 120 | 仮払金 | ASSET | CURRENT_ASSET |
| 121 | 立替金 | ASSET | CURRENT_ASSET |
| 122 | 貸付金 | ASSET | CURRENT_ASSET ※長期は要確認 |
| 123 | 電子記録債権 | ASSET | CURRENT_ASSET |
| 124 | クレジット売掛金 | ASSET | CURRENT_ASSET |
| 125 | 受取商品券 | ASSET | CURRENT_ASSET |
| 126 | 貯蔵品 | ASSET | CURRENT_ASSET |
| 127 | 建物 | ASSET | FIXED_ASSET |
| 128 | 建物減価償却累計額 | ASSET(評価勘定) | FIXED_ASSET |
| 129 | 車両運搬具 | ASSET | FIXED_ASSET |
| 130 | 車両運搬具減価償却累計額 | ASSET(評価勘定) | FIXED_ASSET |
| 131 | 土地 | ASSET | FIXED_ASSET |
| 132 | 差入保証金 | ASSET | FIXED_ASSET |
| 210 | 当座借越 | LIABILITY | CURRENT_LIABILITY |
| 211 | 未払金 | LIABILITY | CURRENT_LIABILITY |
| 212 | 前受金 | LIABILITY | CURRENT_LIABILITY |
| 213 | 仮受金 | LIABILITY | CURRENT_LIABILITY |
| 214 | 所得税預り金 | LIABILITY | CURRENT_LIABILITY |
| 215 | 社会保険料預り金 | LIABILITY | CURRENT_LIABILITY |
| 216 | 電子記録債務 | LIABILITY | CURRENT_LIABILITY |
| 407 | 受取利息 | REVENUE | NON_OPERATING_INCOME |
| 408 | 受取配当金 | REVENUE | NON_OPERATING_INCOME |
| 409 | 受取家賃 | REVENUE | NON_OPERATING_INCOME ※要確認 |
| 410 | 償却債権取立益 | REVENUE | NON_OPERATING_INCOME ※要確認 |
| 511 | 給料 | EXPENSE | SG_AND_A |
| 512 | 法定福利費 | EXPENSE | SG_AND_A |
| 513 | 支払家賃 | EXPENSE | SG_AND_A |
| 514 | 通信費 | EXPENSE | SG_AND_A |
| 515 | 水道光熱費 | EXPENSE | SG_AND_A |
| 516 | 消耗品費 | EXPENSE | SG_AND_A |
| 517 | 租税公課 | EXPENSE | SG_AND_A |
| 518 | 支払手数料 | EXPENSE | SG_AND_A |
| 519 | 発送費 | EXPENSE | SG_AND_A |
| 520 | 広告宣伝費 | EXPENSE | SG_AND_A |
| 521 | 旅費交通費 | EXPENSE | SG_AND_A |
| 522 | 貸倒損失 | EXPENSE | SG_AND_A ※要確認 |
| 523 | 支払利息 | EXPENSE | NON_OPERATING_EXPENSE |

**設計上の論点**

- 今の「備品」は、`FixedAsset` が資産ごとに専用の科目(本体と累計額)を持つ前提になっている(メモ: 「複数資産で共有すると導出結果が混ざる」)。建物・車両を足すと、同じ科目の資産が複数あるケースが現実的になる。
  - 案1: 利用側が「建物A」「建物B」のように資産ごとに科目を作る(今の方針のまま)。
  - 案2: `JournalLine` に資産 ID などの補助情報を持たせる(補助科目の考え方)。影響範囲が大きい。
  - **フェーズ5では案1のまま進め、案2は「別枠」の補助科目として扱う。**

**要確認**
1. 上の表の category(特に ※ の付いた科目)
2. 標準科目としてどこまで入れるか(費用科目は会社ごとに違うので、最小限にする案もある)
3. 受取家賃・償却債権取立益・貸倒損失の P/L 上の区分

**完了条件**: 追加した科目を使って問題集の3級の取引を数件仕訳し、試算表が一致する。

---

## フェーズ6: 3級の残り

### 6.1 貸倒れの処理

いまあるのは、期末の差額補充(`adjustAllowanceForDoubtfulAccounts`)だけ。期中に債権が回収不能になったときの処理がない。

```java
// service.BadDebtService(新規)
/** 売掛金などが回収不能になったときの仕訳を導出する。 */
public Journal writeOff(Account receivableAccount, Money amount,
                        boolean arisingInCurrentPeriod,
                        List<Journal> journalsSoFar, LocalDate date)

/** 前期以前に貸倒れ処理した債権を回収したときの仕訳。 */
public Journal recoverWrittenOff(Account proceedsAccount, Money amount, LocalDate date)
```

想定するロジック(要確認):
- 前期以前に発生した債権: まず貸倒引当金を取り崩し、足りない分を貸倒損失にする
- 当期に発生した債権: 全額を貸倒損失にする(引当金は使わない)
- 回収したとき: 償却債権取立益にする

**要確認**
1. 当期発生・前期発生で、処理を分ける理由と仕訳の形
2. 引当金の残高を超えた場合の扱い
3. `arisingInCurrentPeriod` を利用側が渡す形でよいか(債権の発生日をライブラリでは追跡していない)

### 6.2 期中に取得・売却した固定資産の月割償却

今の `DepreciationPolicy.depreciationFor(asset, accumulatedBefore)` は、1年分の償却額を返す。期中に取得・売却した資産の月割には対応していない。

```java
// 案: DepreciationPolicy の結果に月数を掛ける処理を FixedAsset 側に置く
public Optional<Journal> depreciationEntry(List<Journal> journalsSoFar, LocalDate date,
                                           AccountingPeriod period)   // 期首・期末から月数を求める
```

- 月数は、期首(または取得日)から期末(または売却日)まで。1か月未満の端数を切り上げるか切り捨てるかは**要確認**。
- `disposalEntry` の前に、売却日までの償却仕訳を作れるようにする。
- `DepreciationPolicy` インターフェースを変える(引数に月数を足す)か、`FixedAsset` 側で按分するか。**定率法でも同じ按分の考え方でよいか**で決まる(要確認)。

### 6.3 当座借越の期末振替

当座預金の残高が貸方(マイナス)のまま期末を迎えたとき、当座借越(負債)へ振り替える。

```java
// AdjustingEntryService に追加
public Optional<Journal> reclassifyBankOverdraft(Account checkingAccount,
                                                 List<Journal> journalsSoFar, LocalDate date)
```

- 当座預金の残高が負のときだけ仕訳を返す。
- 翌期首の再振替(`reverseAccruals`)の対象にするかは**要確認**。対象にするなら、`JournalType.ADJUSTING` で作る。

### 6.4 貯蔵品の振替

期末に未使用の収入印紙・切手を、租税公課・通信費から貯蔵品へ振り替える。**利用側が金額を与える単純な2行仕訳**なので、既存方針どおり専用メソッドは作らない。ただし、再振替(翌期首に戻す)が必要なら `reverseAccruals` の対象になるよう、`ADJUSTING` 型で記帳するルールを Javadoc に書く。

**要確認**: 貯蔵品は翌期首に再振替するか。

**フェーズ6の完了条件**: 問題集の3級の決算問題(決算整理事項 → 精算表 → B/S・P/L)を1問、解答例と一致させる。

---

## フェーズ7: 2級の決算整理の中心

### 7.1 棚卸減耗損・商品評価損

**2級の決算整理でほぼ毎回出る論点で、現状の最大の抜け。**

```java
// AdjustingEntryService に追加
/**
 * 期末商品の数量不足(棚卸減耗)と、正味売却価額の下落(商品評価損)を計上する。
 */
public List<Journal> recognizeInventoryLosses(InventoryCount count, LocalDate date)

/** 期末商品の実地棚卸の結果。 */
public record InventoryCount(
        long bookQuantity,           // 帳簿棚卸数量
        long actualQuantity,         // 実地棚卸数量
        Money unitCost,              // 原価(単価)
        Money netRealizableValue     // 正味売却価額(単価)
) {}
```

想定する計算(要確認):
- 棚卸減耗損 = (帳簿数量 − 実地数量) × 原価
- 商品評価損 = (原価 − 正味売却価額) × 実地数量(正味売却価額が原価以上なら0)

**要確認**
1. 上の計算式。特に、評価損に掛けるのが実地数量でよいか
2. 減耗損・評価損を売上原価に含めるか、販管費・営業外費用にするか(P/L の5段階利益に影響する)。「原価性の有無」で変わるなら、その指定を入力に含める
3. `recognizeCostOfSales`(期末商品の振替)との順序と、繰越商品に使う金額(帳簿価額か、減耗・評価後か)
4. 複数の商品がある場合の扱い(商品ごとに `InventoryCount` を渡す形でよいか)

新しい科目: 棚卸減耗損、商品評価損(区分は要確認)。

### 7.2 各種引当金

貸倒引当金と同じく「必要な残高を外から与え、差額を計上する」形が多いと想定し、**共通の部品**にする。

```java
// 内部クラス(package-private)
final class ProvisionCalculator {
    /** 必要額と現在の残高の差額を、繰入(または戻入)として計上する。 */
    Optional<Journal> adjustTo(Money requiredBalance, Account provisionAccount,
                               Account expenseAccount, Account reversalAccount,
                               List<Journal> journalsSoFar, LocalDate date)
}
```

既存の `adjustAllowanceForDoubtfulAccounts` も、内部ではこの部品を使うようにリファクタリングする(公開 API は変えない)。

| 引当金 | 費用科目 | 期中の取崩し | 要確認 |
|---|---|---|---|
| 賞与引当金 | 賞与引当金繰入 | 賞与支給時に取り崩し、超えた分は賞与 | 差額補充か、全額洗替か |
| 退職給付引当金 | 退職給付費用 | 退職金支給時に取り崩し | 繰入額を外から与えるか(残高ではなく費用額) |
| 修繕引当金 | 修繕引当金繰入 | 修繕時に取り崩し、超えた分は修繕費 | |
| 商品保証引当金 | 商品保証引当金繰入 | 保証の修理時に取り崩し | 戻入の科目 |

- 期中の取崩しは、「引当金を充当し、足りない分を費用にする」という形が共通していそう。6.1 の貸倒れと同じ形なら、取崩しも共通化する。
- **退職給付引当金は「残高」ではなく「当期の費用額」を与える問題が多い**と想定。その場合、`adjustTo` ではなく単純な仕訳になる(要確認)。

**フェーズ7の完了条件**: 問題集の2級の決算整理問題(棚卸減耗・評価損、引当金を含む)を1問、解答例と一致させる。

---

## フェーズ8: 売上原価対立法

今は三分法(仕入・繰越商品・売上)のみ。売上原価対立法では、販売のたびに「商品 → 売上原価」を振り替える。

- 新しい科目: 商品(資産)、売上原価(費用、COST_OF_SALES)
- 販売時の原価は、商品有高帳(`InventoryValuationMethod`)の払出金額から得られる。**商品有高帳と仕訳をつなぐ最初の機能**になる。

```java
// service.PerpetualInventoryService(新規、名前は仮)
/** 販売時の売上と売上原価の仕訳を導出する。原価は商品有高帳の払出金額。 */
public Journal recordSale(Account receivableAccount, Money salesAmount,
                          InventoryLedgerEntry issueEntry, LocalDate date)
```

- 決算時の `recognizeCostOfSales`(三分法用)は使わない。三分法と対立法のどちらを使うかを、会社単位で選べるようにする必要がある。
- 棚卸減耗・評価損(7.1)は、対立法でも同じ計算でよいか。**要確認**。

**要確認**
1. 1つの会社の中で、三分法と対立法を混在させないという前提でよいか
2. 返品のときの仕訳の形

---

## フェーズ9: 固定資産の諸論点

| 論点 | 想定する実装 | 要確認 |
|---|---|---|
| 除却・廃棄 | `FixedAsset.retirementEntry(...)`。売却と同じ形で、売却価額の代わりに見積処分価額(貯蔵品)を使う | 除却損・廃棄損の区分 |
| 建設仮勘定 | 単純な仕訳(前払・完成時の振替)。専用メソッドなし | |
| 改良(資本的支出)と修繕(収益的支出) | 利用側が判断して科目を選ぶ。ライブラリは判断しない | 改良後の償却額の再計算方法 |
| 圧縮記帳(直接減額方式) | 国庫補助金受贈益と固定資産圧縮損。圧縮後の取得価額で償却を続けるため、`FixedAsset` の取得価額の扱いを見直す | 圧縮後の償却の基礎 |
| 割賦購入 | 利息部分を前払利息にし、支払ごとに支払利息へ振り替える | 利息の配分方法(定額か) |
| 生産高比例法 | `DepreciationPolicy` の新しい実装。当期の利用量を入力に加える | 入力の形 |
| 無形固定資産(ソフトウェア、のれん) | 直接法(累計額勘定を使わない)の償却。`FixedAsset` の累計額導出と別の扱いになる | 償却年数・残存価額ゼロの扱い |

- 生産高比例法は、今の `DepreciationPolicy.depreciationFor(asset, accumulatedBefore)` に当期の利用量が渡らない。インターフェースを変えるかどうかを、6.2(月割)と合わせて決める。
- 直接法の償却は、`FixedAsset` の「専用の累計額勘定から累計償却額を導出する」設計と合わない。別のクラス(`IntangibleAsset`)にするか、`FixedAsset` に方式を持たせるかを決める。

---

## フェーズ10: 有価証券の残りの区分

| 区分 | 期末評価(想定) | 要確認 |
|---|---|---|
| 満期保有目的債券 | 償却原価法(定額法)。取得価額と額面の差を、期間で按分して有価証券利息に | 月割の扱い |
| 子会社株式・関連会社株式 | 取得原価のまま(評価しない) | |
| その他有価証券 | 時価評価し、差額を純資産(その他有価証券評価差額金)に直接計上(全部純資産直入法)。翌期首に戻す(洗替) | 洗替の仕訳の形 |
| 端数利息 | 債券の売買時に、前回の利払日からの経過利息を受け払いする | 日割の計算方法 |

- 新しい AccountCategory が必要になる可能性: **その他有価証券評価差額金**は、資本金・利益剰余金のどちらでもない純資産(評価・換算差額等)。今の6区分に当てはまらない。区分の追加はユーザーに確認する(`SUSPENSE` を追加したときと同じ手順)。
- 株主資本等変動計算書(`StatementOfChangesInEquity`)の列にも影響する。
- 既存の `TradingSecuritiesService.sell()` は「保有全額を一括売却」の前提。一部売却(平均単価)への対応も、このフェーズで検討する。

---

## フェーズ11: 債権・債務の諸論点、銀行勘定調整表

### 手形・電子記録債権

| 論点 | 想定する実装 |
|---|---|
| 手形の裏書 | 単純な仕訳(受取手形 → 買掛金など)。専用メソッドなし |
| 不渡手形 | 不渡手形(資産)への振替。償還請求の費用を含める。単純な仕訳 |
| 手形の更新 | 旧手形の取消と新手形 + 利息。利息を利用側が与えるなら単純な仕訳 |
| 電子記録債権の譲渡・割引 | 手形の割引(`NotesReceivableService.discount`)と同じ形。共通化を検討する |
| 営業外手形 | 営業外受取手形・営業外支払手形(固定資産の売買など)。科目の追加のみ |

計算を伴うのは「割引」だけなので、ほとんどは科目の追加と、問題集の取引で試算表を確かめるテストだけになる見込み。

### 銀行勘定調整表

銀行の残高証明書と、帳簿の当座預金残高の差異を調整する。

```java
// model.statement.BankReconciliation(新規)
public static BankReconciliation of(Money bankStatementBalance,
                                    Account checkingAccount,
                                    List<Journal> journalsSoFar,
                                    List<ReconcilingItem> items)

public sealed interface ReconcilingItem {
    record OutstandingCheck(Money amount) implements ReconcilingItem {}       // 未取付小切手
    record DepositInTransit(Money amount) implements ReconcilingItem {}       // 時間外預入
    record UnrecordedDeposit(Money amount, Account counter) implements ReconcilingItem {}  // 連絡未通知
    record UnissuedCheck(Money amount, Account counter) implements ReconcilingItem {}       // 未渡小切手
    record RecordingError(Money difference, Account counter) implements ReconcilingItem {}  // 誤記入
}
```

- 帳簿側の修正が必要な項目(連絡未通知・未渡小切手・誤記入)は、修正仕訳を生成する。銀行側の項目(未取付小切手・時間外預入)は、仕訳を作らない。
- 自己検証: 調整後の銀行残高 = 調整後の帳簿残高。
- **要確認**: 各項目が「銀行側」「帳簿側」のどちらを調整するか。未渡小切手の相手科目(買掛金か未払金か)の扱い。

---

## フェーズ12: 純資産

| 論点 | 想定する実装 | 要確認 |
|---|---|---|
| 任意積立金(別途積立金など) | 繰越利益剰余金からの振替。単純な仕訳。科目を追加 | 区分(RETAINED_EARNINGS でよいか) |
| その他資本剰余金からの配当 | `DividendService` を拡張し、配当の原資(利益剰余金/資本剰余金)を選べるようにする。準備金の積立先が資本準備金になる | 積立上限の計算は同じか |
| 合併・事業譲受 | 受け入れた資産・負債と対価の差額をのれん(または負ののれん発生益)にする | のれんの償却(フェーズ9の無形固定資産と共通) |

- 株主資本等変動計算書の列(今は資本金・資本準備金・利益準備金・繰越利益剰余金の4列)に、その他資本剰余金と任意積立金を追加する必要がある。
- 変動事由(`EquityChangeReason`)に「積立金の積立」「合併」などを足す。

---

## フェーズ13: 収益認識

| 論点 | 想定する実装 | 要確認 |
|---|---|---|
| 役務収益・役務原価 | サービス業の売上と原価。仕掛品(役務原価の繰延)を含む | 仕掛品から役務原価への振替のタイミング |
| 契約資産 | 複数の履行義務のうち、一部だけ履行したときの未請求の対価 | 仕訳の形 |
| 返金負債 | 売上割戻しなど、返金が見込まれる部分 | 変動対価の見積もり方法 |

- 2級の中でも新しい論点で、問題集によって扱いの深さが違う。**着手前に、どの問題集を基準にするかを決める。**

---

## 別枠: 実務向けの拡張(試験範囲外。必要になってから)

| 機能 | 影響 |
|---|---|
| 補助科目(取引先別の売掛金・買掛金、資産別の固定資産) | `JournalLine` に補助情報を足す。`Ledger` を補助単位でも導出する。影響が最も大きい |
| 部門 | 補助科目と同じ仕組みで扱えるか検討 |
| 月次決算 | `AccountingPeriod` に月の区切りを持たせる(今は1年固定)。「月次の締め」と「年次の締め」を分ける |
| 消費税の税込方式 | `settleConsumptionTax` と別の精算方法。簡易課税・インボイスは引き続き範囲外 |
| 源泉徴収の計算 | 税額表を使う計算は範囲外。預り金の科目だけ用意する(フェーズ5) |
| 固定資産台帳 | `FixedAsset` の一覧と、仕訳から導出した帳簿価額を並べた表 |

## 範囲外(引き続き作らない)

CLAUDE.md の「範囲外」節のとおり: 工業簿記、連結会計、税効果会計、リース会計、外貨建取引、本支店会計、為替手形、簡易課税・インボイス、申告書の作成。**これらは2級の試験範囲には入っている**ので、「2級の商業簿記を全部」が目標になったら、範囲の見直しが必要になる。

## 決算整理の実行順序(更新案)

フェーズ6〜10で論点が増えたあとの順序の案。**法人税等は税引前当期純利益に依存するので最後のほうに置く**、という既存の考え方を保つ。順序全体は**要確認**。

```
reverseAccruals(期首)
 → reclassifyBankOverdraft            (6.3)
 → recognizeCostOfSales               (既存。三分法のみ)
 → recognizeInventoryLosses           (7.1)
 → depreciate(資産ごと、月割)         (既存 + 6.2)
 → 有価証券の期末評価                  (既存 + 10)
 → adjustAllowanceForDoubtfulAccounts (既存)
 → その他の引当金                      (7.2)
 → 経過勘定・貯蔵品(利用側が記帳)
 → accrueIncomeTax                    (既存)
 → settleConsumptionTax               (既存)
```

消費税の精算と法人税等の順序が、今の Javadoc(法人税等 → 消費税)と同じでよいかも、あわせて確認する。

## 開発者への確認事項(着手前にまとめて確認)

フェーズ5〜7(優先度が高い部分)の確認事項:

1. 標準科目の表(5.2)の category。特に ※ の付いた科目
2. 貸倒れ: 当期発生・前期発生の処理の違い、引当金を超えた場合(6.1)
3. 月割償却: 端数月の扱い、定率法でも同じ按分か(6.2)
4. 当座借越・貯蔵品を、翌期首に再振替するか(6.3, 6.4)
5. 棚卸減耗損・商品評価損の計算式と、P/L 上の区分(7.1)
6. 各引当金が差額補充か洗替か、退職給付は費用額を与えるか(7.2)
7. 決算整理の実行順序(上の更新案)
8. 基準にする問題集・テキスト(期待値の出どころ)
