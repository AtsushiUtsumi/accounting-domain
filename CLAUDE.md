# CLAUDE.md — accounting-domain

簿記3級〜2級レベル(商業簿記・単体会社)の会計ドメインライブラリ。Java / Gradle。
目的は **DDD の学習**。会計ルールはドメイン層に置き、DB やフレームワークには依存させない。

## このプロジェクトの前提(最重要)

- 開発者は **簿記の知識がない**。会計ルールの正しさは、開発者の判断では検証できない。
- したがって、**会計ルールを推測で実装しない**。ルールが曖昧なとき、仕訳の借方・貸方が確信できないときは、実装を止めて開発者に質問する。
- 正しさの根拠は、**簿記の問題集・テキストにある「取引 → 仕訳 → 試算表 → 財務諸表」の解答例**。これをテストの期待値にする。期待値を自分で作らない。
- 一度に作りすぎない。**フェーズごとに、テストが通る状態で止まる**。範囲外の機能を先回りして足さない。

## ビルド・技術条件

- ビルドツール: Gradle(Kotlin DSL)。`settings.gradle.kts` と `build.gradle.kts` は作成済み。
- Java: JDK 27(toolchain で指定。LTS にする場合は 25)。record、sealed、パターンマッチングを積極的に使う。
- **本体(main)の依存は JDK のみ**。Lombok、Jackson、JPA、SLF4J、Spring などを入れない。
- テスト専用に JUnit 5 と AssertJ を使う。バージョンは仮置きなので、着手時に最新へ更新する。
- Gradle ラッパーが未生成。JDK 27 に対応した Gradle バージョンを、公式の互換表で確認してから生成する。
- 実装前に確認する: `./gradlew build` が空の状態で通ること。

## アーキテクチャ方針

- **ドメイン層のみ**のライブラリ。アプリケーション層(ユースケース)、インフラ層(JPA 等)、プレゼンテーション層は作らない。
- アノテーション(`@Entity`、`@Component`、`@Transactional` など)を一切付けない。
- すべてイミュータブル。状態変更は新しいインスタンスを返す。
- 現在時刻は `LocalDate.now()` を直接呼ばない。日付は引数で受けるか `java.time.Clock` を受け取る。
- ID は `UUID` ベース。`newId()` で生成、`of(String)` で復元できる。DB の採番に依存しない。
- 例外は `AccountingException`(`RuntimeException`)。必要に応じて細分化する。
- **ドメインサービスはリポジトリを呼ばない**。副作用を持たず、「生成すべき仕訳」や「導出結果」を返すだけ。保存は利用側の責務。
- シリアライズは対応しない(JSON 化は利用側)。

### パッケージ構成

```
com.example.accounting
 ├─ model
 │   ├─ money      Money, RoundingPolicy
 │   ├─ account    Account, AccountCode, AccountType, AccountCategory, AccountTag, Side, StandardAccounts, AccountRepository
 │   ├─ journal    Journal, JournalId, JournalLine, JournalType, PostedJournal, JournalRepository
 │   ├─ period     AccountingPeriod, AccountingPeriodId, PeriodStatus, AccountingPeriodRepository
 │   ├─ asset      FixedAsset, FixedAssetId, DepreciationPolicy と各実装, FixedAssetRepository
 │   └─ statement  Ledger, TrialBalance, BalanceSheet, IncomeStatement, StatementOfChangesInEquity
 ├─ service        ClosingService, FinancialStatementFactory, AdjustingEntryService など
 └─ exception      AccountingException
```

## 設計の決定事項

### 帳簿の基本原則

- **追記のみ(append-only)**。仕訳の更新・削除は存在しない。誤りは**赤黒処理**(逆仕訳 + 正しい仕訳の新規作成)で訂正する。
- **三表(B/S・P/L・C/F)は保存しない**。仕訳から毎回導出する。導出できる値を保存しない。
- 繰越は「データの移行・書き換え」ではなく、**新しい仕訳を生成する**ことで実現する(開始仕訳方式)。

### 勘定科目

```java
public record Account(
    AccountCode code,
    String name,
    AccountType type,          // 資産・負債・純資産・収益・費用(固定 enum)
    AccountCategory category,  // 標準区分(固定 enum)。三表の合計と5段階利益の計算に使う
    Set<AccountTag> tags,      // 会社が自由に追加。表示のグルーピング・管理会計用。利益計算には影響させない
    boolean standard,          // 標準科目は変更・無効化できない
    boolean active             // 削除はせず無効化のみ
) {}
```

- 科目の同一性は `AccountCode` で判定する(名前は変更可)。
- `type` は作成後に変更できない。
- 使用済みの科目は削除不可。無効化した科目は新しい仕訳で使えない。
- `JournalLine` は `AccountCode` ではなく **`Account` を保持**する(財務諸表の導出を純粋計算にするため)。
- 会社ごとに科目を追加できる。`StandardAccounts` は「初期セット」の位置づけ。
- 標準科目として必ず存在させ、保護するもの(最低限): **損益**、**繰越利益剰余金**。フェーズが進むにつれて、手形・貸倒・減価償却・経過勘定・税などを足す。

### 仕訳

- `Journal`: id、日付、摘要、`JournalType`、複数の `JournalLine`、取消元 `reversalOf`(任意)。
- **不変条件: 借方合計 = 貸方合計**。コンストラクタ(またはファクトリ)で検証し、不正な仕訳は作れない。
- 複合仕訳(借方・貸方が複数行)を許す。
- `JournalType`: 通常、決算整理、損益振替、開始、再振替、取消。

### 会計期間

- `AccountingPeriod` が集約ルート。期首・期末・状態(OPEN / CLOSED)・`long version`(楽観ロック用。ライブラリは保持するだけで検証しない)。
- CLOSED の期には仕訳を追加できない。すでに CLOSED の期を `close` すると例外。
  - 実現方法: 帳簿に載せられるのは記帳済みの仕訳 `PostedJournal` だけで、`PostedJournal.post(period, journal)` が OPEN と日付を検証する。`JournalRepository` は `PostedJournal` しか保存できない。記帳後に期が締められた場合は、保存時の version 検証で弾く。

### 繰越・決算処理(ClosingService)

`ClosingService.close(period, journals)` は、保存せず `ClosingResult` を返す。

```java
public record ClosingResult(
    List<Journal> closingEntries,   // 損益振替・利益剰余金振替
    List<Journal> openingEntries,   // 次期の開始仕訳
    AccountingPeriod closedPeriod,
    AccountingPeriod nextPeriod
) {}
```

- 収益・費用は繰り越さない。損益勘定に集め、**当期純利益を繰越利益剰余金へ振り替える**(純損失なら逆向き)。
- 資産・負債・純資産の期末残高を、**次期の期首日付**の開始仕訳として生成する。
- 残高ゼロの科目は開始仕訳に含めない。残高の向きは `AccountType` から決める。
- 決算整理仕訳は、`close` の前に済んでいる前提(Javadoc に明記)。

**自己検証(テストに必ず入れる):**
- 締め後、収益・費用科目の残高がすべてゼロ
- 締め後の B/S で 資産 = 負債 + 純資産
- 前期の期末試算表の B/S 科目残高 = 次期の期首残高
- 当期純利益 = 繰越利益剰余金の増加額

### リポジトリ(インターフェースのみ。実装は利用側)

- インターフェースは、扱う集約と同じ `model` 配下のパッケージに置く(例: `JournalRepository` は `model.journal`)。
- `JournalRepository`: `save`、`saveAll`、`findById`、`findByPeriod`。**update / delete は作らない**。
- `AccountingPeriodRepository`: `findById`、`save`。
- `AccountRepository`: `findByCode`、`findAll`、`save`、`existsByCode`。
- `FixedAssetRepository`: 固定資産を扱うフェーズで追加。
- `saveAll` の Javadoc に契約を書く: **「全件保存するか、1件も保存しないか。途中まで保存された状態を残してはならない」**。実際のトランザクション制御は実装側の責務。

### 公開インターフェースの方針

- 差し替える正当な理由があるものだけインターフェースにする: リポジトリ、`DepreciationPolicy`、`CashFlowClassifier`。
- ドメインサービス(`ClosingService` など)は `final` の具象クラス。
- 内部の集計ロジックは package-private。

## 実装フェーズ

### フェーズ1(最初に作る。ここで止めて確認)

`Money`、`RoundingPolicy`、`Side`、`AccountType`、`AccountCategory`、`AccountCode`、`AccountTag`、`Account`、`StandardAccounts`(最小)、`JournalType`、`JournalLine`、`JournalId`、`Journal`、`AccountingPeriod`、`AccountingPeriodId`、`Ledger`、`TrialBalance`、`AccountingException`。

完了条件: 問題集の簡単な取引を数件仕訳し、**試算表の借方合計と貸方合計が一致する**テストが通る。

### フェーズ2

`BalanceSheet`、`IncomeStatement`(売上総利益 → 営業利益 → 経常利益 → 税引前当期純利益 → 当期純利益の5段階)、`FinancialStatementFactory`、`ClosingService`(損益振替・開始仕訳)、`ReversalService`(赤黒処理)。

### フェーズ3(決算整理)

再振替仕訳(経過勘定を戻す)、売上原価(三分法)、減価償却(定額法・定率法、間接法)、貸倒引当金(差額補充法)、法人税等、消費税の精算。`AdjustingEntryService` を公開の入口にし、個別処理は内部クラスにする。**決算整理には実行順序の依存がある**ため、順序をコードで固定する。

### フェーズ4以降(必要になってから)

手形(約束手形のみ)、現金過不足、有価証券、固定資産の売却、株主資本等変動計算書(2級の頻出論点)、精算表、C/F(間接法。前期末 B/S が必要)、商品有高帳。

## 範囲外(作らない)

工業簿記(原価計算)、連結会計、税効果会計、リース会計、外貨建取引、本支店会計、為替手形、簡易課税・インボイス、申告書の作成、Spring 向けモジュール(domain が固まってから別モジュールとして検討)。

## 実装前に開発者へ確認すること(未決事項)

1. **`Money` の通貨**: 円固定にするか、通貨付きにするか。(推奨: 円固定。為替差損益が不要になる)
2. **`AccountCategory` の具体的な一覧**: B/S・P/L の標準区分。損益勘定と繰越利益剰余金の置き場所に関わる。
3. **`FixedAsset` の累計償却額**: 保存するか、仕訳から導出するか。(フェーズ3で必要になった時点で決める)
4. **会計期間**: 1年固定か、変則決算を許すか。(暫定: 次期は1年)
5. **範囲**: 「単体会社の商業簿記まで」に絞る前提でよいか。

## 作業の進め方

- テストを先に書く(問題集の解答例を期待値にする)。
- 小さい単位でコミットできる状態を保つ。1ステップごとに `./gradlew test` を通す。
- 新しい公開クラスを足すときは、Javadoc に「何を表すか」を簿記の言葉で1行書く。
- 設計と食い違う実装が必要になったら、実装せずに理由を添えて質問する。