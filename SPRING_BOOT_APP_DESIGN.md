# accounting-app 設計書(Spring Boot)

`accounting-domain` ライブラリを使い、仕訳の記帳から決算・財務諸表の照会までを提供する Spring Boot アプリケーションの設計。

- 位置づけ: ドメイン層(会計ルール)は `accounting-domain` が持つ。このアプリは **アプリケーション層・インフラ層・プレゼンテーション層** だけを担う。
- 原則: **会計ルールをアプリ側に書かない**。借方・貸方・金額の計算は、すべてライブラリのドメインサービスに任せる。アプリの役割は「取得する → ドメインに計算させる → 保存する」と、トランザクション・入出力・認可。

---

## 1. 技術スタック

| 項目 | 選定 | 理由 |
|---|---|---|
| 言語 | Java 25(ドメインと同じ toolchain) | |
| フレームワーク | Spring Boot 4.x(着手時に最新の安定版を確認する) | |
| ビルド | Gradle(Kotlin DSL) | ドメインと揃える |
| 永続化 | **Spring JDBC(`JdbcClient`)** | ドメインモデルはイミュータブルな record で、アノテーションも付けられない。JPA エンティティを別に作って詰め替えるより、SQL ↔ record を手で変換するほうが単純で、学習目的にも見通しが良い |
| マイグレーション | Flyway | |
| DB | PostgreSQL(開発・テストは Testcontainers) | |
| API | Spring Web MVC(REST / JSON) | |
| テスト | JUnit / AssertJ / Spring Boot Test / Testcontainers | |

JPA を採らない理由をもう少し: `Journal` は `JournalLine` が `Account` を直接持ち、生成時に「借方合計 = 貸方合計」を検証する。JPA の遅延ロードや dirty checking、引数なしコンストラクタは、この「不正な状態を作らせない」設計と相性が悪い。

## 2. プロジェクト構成

ドメインのリポジトリはそのまま残し、アプリは別の Gradle プロジェクトにする。

```
accounting-domain/            ← 既存
accounting-app/              ← 新規
 ├─ settings.gradle.kts      includeBuild("../accounting-domain")   ※または publishToMavenLocal
 └─ build.gradle.kts         implementation("com.example:accounting-domain")
```

- ドメインが Spring に依存しないことを、**ビルドの構成そのもので保証する**(CLAUDE.md の「Spring 向けモジュールは別モジュールとして検討」に対応)。
- マルチモジュール(`accounting-domain/domain`, `accounting-domain/app`)にしてもよい。その場合も domain → app の一方向の依存を守る。

## 3. レイヤーとパッケージ

```
com.example.accountingapp
 ├─ presentation          REST コントローラ、リクエスト/レスポンス DTO、例外ハンドラ
 │   ├─ journal
 │   ├─ closing
 │   ├─ statement
 │   └─ master
 ├─ application           ユースケース(@Service, @Transactional)とコマンド/結果の型
 │   ├─ journal           PostJournalUseCase, CorrectJournalUseCase
 │   ├─ transaction       DiscountNoteUseCase, SellSecuritiesUseCase, DeclareDividendsUseCase,
 │   │                     RecordCashDiscrepancyUseCase
 │   ├─ closing           RunAdjustingEntriesUseCase, ClosePeriodUseCase, ReverseAccrualsUseCase
 │   ├─ statement         StatementQueryService(読み取り専用)
 │   ├─ master            AccountManagementUseCase, PeriodSetupUseCase, FixedAssetUseCase
 ├─ infrastructure
 │   ├─ persistence       Jdbc*Repository(ライブラリのリポジトリのインターフェースの実装)、行 ↔ ドメインの変換
 │   └─ config            DomainServiceConfig(ドメインサービスの Bean 登録)、ClockConfig
 └─ AccountingApplication
```

依存の向き: `presentation → application → domain`、`infrastructure → domain`(リポジトリのインターフェースはドメイン側にある)。application は infrastructure を知らない。

### ドメインサービスの Bean 登録

ライブラリのクラスにアノテーションは付けられないため、`@Configuration` で登録する。ドメインサービスは副作用がないので、すべてシングルトンでよい。

```java
@Configuration
class DomainServiceConfig {
    @Bean ClosingService closingService() { return new ClosingService(); }
    @Bean AdjustingEntryService adjustingEntryService() { return new AdjustingEntryService(); }
    @Bean ReversalService reversalService() { return new ReversalService(); }
    @Bean FinancialStatementFactory financialStatementFactory() { return new FinancialStatementFactory(); }
    // NotesReceivableService, TradingSecuritiesService, DividendService, CashOverShortService も同様
}

@Configuration
class ClockConfig {
    @Bean Clock clock() { return Clock.system(ZoneId.of("Asia/Tokyo")); }  // テストでは Clock.fixed に差し替える
}
```

(コンストラクタの引数は実装に合わせる。)

## 4. ポート(リポジトリインターフェース)

> **決定済み**: リポジトリのインターフェースはライブラリ側にあり、扱う集約と同じパッケージに置いている(`model.journal.JournalRepository`、`model.period.AccountingPeriodRepository`、`model.account.AccountRepository`、`model.asset.FixedAssetRepository`)。アプリはこれを実装する。
> 以下のうち `findContaining` / `findPrevious` / `findAllInUse` はアプリ都合のメソッドなので、ライブラリのインターフェースにはない(アプリ側で拡張する)。

```java
interface JournalRepository {
    void save(PostedJournal journal);
    /**
     * 全件保存するか、1件も保存しないか。途中まで保存された状態を残してはならない。
     * 保存時の会計期間の version が PostedJournal.periodVersion() と違う仕訳があれば失敗する。
     */
    void saveAll(List<PostedJournal> journals);
    Optional<PostedJournal> findById(JournalId id);
    List<PostedJournal> findByPeriod(AccountingPeriod period);   // その期に記帳された仕訳
    // update / delete は作らない(追記のみ)
}

interface AccountingPeriodRepository {
    Optional<AccountingPeriod> findById(AccountingPeriodId id);
    Optional<AccountingPeriod> findContaining(LocalDate date);   // アプリ都合で追加
    Optional<AccountingPeriod> findPrevious(AccountingPeriod period);  // C/F・再振替で使う
    void save(AccountingPeriod period);   // version を使って楽観ロック
}

interface AccountRepository {
    Optional<Account> findByCode(AccountCode code);
    List<Account> findAll();
    void save(Account account);
    boolean existsByCode(AccountCode code);
}

interface FixedAssetRepository {
    Optional<FixedAsset> findById(FixedAssetId id);
    List<FixedAsset> findAllInUse(LocalDate asOf);
    void save(FixedAsset asset);
}
```

`Journal` は会計期間の ID を持たない。そのため、どの期の仕訳かは**日付**で決まる。`findByPeriod` は `date BETWEEN start_date AND end_date` で検索する。

## 5. 永続化モデル

```sql
create table account (
    code        varchar(16) primary key,
    name        varchar(100) not null,
    type        varchar(16)  not null,     -- AccountType
    category    varchar(32)  not null,     -- AccountCategory
    standard    boolean      not null,
    active      boolean      not null
);
create table account_tag (
    account_code varchar(16) references account(code),
    tag          varchar(50),
    primary key (account_code, tag)
);

create table accounting_period (
    id          uuid primary key,
    start_date  date not null,
    end_date    date not null,
    status      varchar(8) not null,       -- OPEN / CLOSED
    version     bigint not null
);

create table journal (
    id           uuid primary key,
    date         date not null,
    description  varchar(200) not null,
    type         varchar(16) not null,     -- JournalType
    reversal_of  uuid references journal(id),
    created_at   timestamptz not null,     -- 監査用(ドメインには戻さない)
    created_by   varchar(100) not null     -- 監査用
);
create index journal_date_idx on journal(date);

create table journal_line (
    journal_id   uuid references journal(id),
    line_no      int,
    account_code varchar(16) not null references account(code),
    side         varchar(8)  not null,     -- DEBIT / CREDIT
    amount       bigint      not null,     -- 円(Money は円固定)
    primary key (journal_id, line_no)
);

create table fixed_asset ( ... );          -- FixedAsset の各フィールド。累計償却額は保存しない(仕訳から導出)
```

方針:
- **journal / journal_line は INSERT だけ**。アプリの DB ユーザーから `UPDATE` / `DELETE` 権限を外すと、追記のみの原則を DB 側でも守れる。
- **B/S・P/L・試算表のテーブルは作らない**(導出できる値は保存しない)。
- 復元するとき: `journal_line.account_code` から `Account` を取ってきて、`JournalLine` を組み立て、`Journal.of(...)` / `Journal.reversalOf(...)` を通す。こうすると、DB から読んだデータもドメインの不変条件(貸借一致)で検証される。
- 仕訳をまとめて読むときは、科目を1回で全件読み込み、`Map<AccountCode, Account>` にしてから組み立てる(N+1 問題を避けるため)。

## 6. ユースケース

共通の形:

```
@Transactional
1. 入力 DTO を検証する(形式チェックのみ。会計ルールはチェックしない)
2. リポジトリから期・科目・仕訳を取得する
3. ドメインのファクトリ/サービスを呼んで、結果(仕訳など)を受け取る
4. リポジトリで保存する
5. 結果(ID など)を返す
```

### 6.1 仕訳を記帳する(PostJournalUseCase)

入力: 日付、摘要、行のリスト(科目コード、借方/貸方、金額)

1. `periodRepository.findContaining(date)` で期を探す。見つからない、または CLOSED なら例外。
2. 科目コードを `Account` に変換する。存在しない、または無効化された科目なら例外。
3. `Journal.of(JournalId.newId(), date, description, JournalType.NORMAL, lines)` を呼ぶ。貸借の不一致はここで例外になる。
4. `PostedJournal.post(period, journal)` で記帳する。CLOSED の期・期間外の日付はここで例外になる。
5. `journalRepository.save(posted)`。

**同時実行**: 記帳と締めが同時に走っても、締めた期に仕訳は入らない。`PostedJournal` は記帳した時点の期の `version` を持ち、`saveAll` の実装が保存時の期の `version` と比べて弾くため(締めると version が1つ進む)。JDBC 実装では、同じトランザクションの中で `accounting_period.version` を読み、一致しなければロールバックする。

### 6.2 仕訳を訂正する — 赤黒(CorrectJournalUseCase)

入力: 元の仕訳 ID、訂正日、正しい仕訳の内容(省略すれば取消のみ)

1. 元の仕訳を取得する。
2. `reversalService.reverse(JournalId.newId(), date, original)` で逆仕訳を作る。
3. 正しい仕訳を 6.1 と同じ手順で作る。
4. 逆仕訳と正しい仕訳を、訂正日を含む OPEN の期に `PostedJournal.postAll` で記帳し、`journalRepository.saveAll` で保存する。元の仕訳が締めた期のものでも、訂正は当期に記帳される。

同じ仕訳を2回取り消せないようにするかどうかは**未決**(ライブラリ側のルールにするか、アプリ側のチェックにするか)。

### 6.3 個別取引(transaction パッケージ)

手形の割引・有価証券の売却・配当・現金過不足は、どれも同じ形になる:

```
期を取得 → journalRepository.findByPeriod(period) → ドメインサービス(..., journalsSoFar, date) → save
```

金額などの事実(割引料、売却代金、実際の手許現金)はユーザーが入力する。アプリは計算しない。

### 6.4 決算整理(RunAdjustingEntriesUseCase)

入力(すべて外部から与える事実): 期首商品棚卸高、期末商品棚卸高、貸倒引当金の設定額、確定した法人税等の額

呼び出す順序は、ライブラリの Javadoc で決まっている:

```
reverseAccruals(※期首に実施済み) → recognizeCostOfSales → depreciate(資産ごと)
  → adjustAllowanceForDoubtfulAccounts → accrueIncomeTax → settleConsumptionTax
```

```java
List<Journal> soFar = new ArrayList<>(journalRepository.findByPeriod(period));
List<Journal> generated = new ArrayList<>();
// 各ステップの結果を soFar と generated の両方に追加してから、次のステップへ進む
...
journalRepository.saveAll(PostedJournal.postAll(period, generated));   // 最後にまとめて記帳・保存する
```

- **途中で保存しない**。どこかで失敗したら全部やり直す。
- 二重実行を防ぐ: 当期に `JournalType.ADJUSTING` の仕訳が1件でもあれば拒否する(やり直すときは、整理仕訳を赤黒で取り消してから)。
- 「プレビュー」用のエンドポイントでは、保存せずに `generated` を返す。ユーザーが確認してから確定させる流れにする。

### 6.5 決算を締める(ClosePeriodUseCase)

1. 期を取得し、排他ロックする。
2. `posted = journalRepository.findByPeriod(period)`(開始仕訳・通常仕訳・決算整理仕訳がすべて入る)。
3. `ClosingResult result = closingService.close(period, posted)`。損益振替は当期に、開始仕訳は次期に記帳済みで返る。
4. **1トランザクションで、この順に**保存する(`ClosingResult` の Javadoc の順序):
   - `journalRepository.saveAll(result.closingEntries())` — 締める前の version で記帳されているので先に保存する
   - `periodRepository.save(result.closedPeriod())` — version による楽観ロック
   - `periodRepository.save(result.nextPeriod())`
   - `journalRepository.saveAll(result.openingEntries())`
5. 結果として、次期の ID と当期純利益を返す。

すでに CLOSED の期を締めようとすると、ドメインが例外を投げる。同時に2回実行された場合は、楽観ロックの失敗で片方が弾かれる。

### 6.6 再振替(ReverseAccrualsUseCase)

次期の期首に実行する。前期の `ADJUSTING` 仕訳を取得し、`reverseAccruals(prevAdjusting, nextPeriod.startDate())` の結果を次期に記帳して `saveAll` する(再振替は期首の日付でしか記帳できない)。決算を締めたあとに続けて自動実行するか、別の操作にするかは**未決**。

### 6.7 照会(StatementQueryService, `@Transactional(readOnly = true)`)

| 照会 | 必要な仕訳 | 呼び出すもの |
|---|---|---|
| 試算表 | 当期 | `FinancialStatementFactory.trialBalance` |
| B/S・P/L | 当期 | `balanceSheet` / `incomeStatement` |
| 精算表 | 当期 | `Worksheet` |
| C/F | 当期 + 前期(前期末の B/S が必要) | `CashFlowStatement`、`StandardCashFlowClassifier` |
| 株主資本等変動計算書 | 当期 | `StatementOfChangesInEquity` |
| 総勘定元帳 | 当期 | `Ledger.of(account, journals)` |
| 商品有高帳 | 入出庫の明細(別テーブルが必要) | `FifoInventoryValuationMethod` など |

締め前の期を照会する場合は、`CLOSING` 仕訳を除いた「締め前の状態」を見せたい場面がある(試算表など)。どちらを見せるかを画面ごとに決める。

### 6.8 マスタ

- **科目**: 追加、名前の変更(`rename`)、タグの変更(`withTags`)、無効化(`deactivate`)。標準科目の保護はドメインが担う。使用済みの科目かどうかは `journal_line` を検索して判定し、削除 API は作らない。
- **初期化**: 会社(テナント)を作るときに、`StandardAccounts.ALL` を投入し、`AccountingPeriod.startingAt(期首日)` で最初の期を作る。
- **固定資産**: 登録と償却方法の選択(`StraightLine` / `DecliningBalance`)。売却は `disposalEntry` で仕訳を作って保存する。

## 7. REST API(案)

| メソッド | パス | ユースケース |
|---|---|---|
| POST | `/journals` | 記帳 |
| POST | `/journals/{id}/corrections` | 赤黒訂正 |
| GET | `/periods/{id}/journals` | 仕訳帳 |
| POST | `/notes-receivable/discounts` 他 | 個別取引 |
| POST | `/periods/{id}/adjustments/preview` | 決算整理のプレビュー |
| POST | `/periods/{id}/adjustments` | 決算整理の確定 |
| POST | `/periods/{id}/close` | 締め |
| GET | `/periods/{id}/trial-balance` | 試算表 |
| GET | `/periods/{id}/balance-sheet` | B/S |
| GET | `/periods/{id}/income-statement` | P/L |
| GET | `/periods/{id}/cash-flow` | C/F |
| GET | `/periods/{id}/worksheet` | 精算表 |
| GET | `/accounts/{code}/ledger?periodId=` | 元帳 |
| GET/POST/PATCH | `/accounts` | 科目マスタ(DELETE はない) |

- 金額は JSON の整数(円)でやり取りする。`Money` を JSON にどう変換するかは presentation 層で決める(ライブラリはシリアライズに対応しない)。
- レスポンス DTO は、ドメインの record から presentation 層で詰め替える。ドメインの型をそのまま JSON にしない。

## 8. 例外の扱い

| 発生元 | HTTP | 備考 |
|---|---|---|
| `AccountingException`(貸借の不一致、CLOSED の期への記帳、など) | 422 Unprocessable Entity | メッセージを ProblemDetail の `detail` に入れる |
| リソースが見つからない(アプリ側) | 404 | |
| 楽観ロックの失敗 | 409 Conflict | |
| 入力の形式エラー(Bean Validation) | 400 | |

`@RestControllerAdvice` で `ProblemDetail`(RFC 9457)に変換する。ライブラリの例外が細分化されたら、種類ごとにコードを振り分ける。

## 9. 横断的な関心事

- **日付**: 「今日」が必要なのは、入力が省略されたときの初期値だけ。`Clock` Bean から取り、ドメインには `LocalDate` で渡す。
- **認可**: 記帳者、承認者(決算整理・締め)、閲覧者のロールを分ける(Spring Security)。
- **監査**: `journal.created_at / created_by` に記録する。ドメインの `Journal` には含めない。
- **マルチテナント**: 必要になったら全テーブルに `company_id` を足す。最初は単一の会社で作る。
- **性能**: 照会のたびに「期の全仕訳をメモリに読み込んで計算する」。件数が問題になってから、読み取り用のキャッシュを検討する(保存した集計値は**キャッシュ**であって正本ではない、という扱いにする)。

## 10. テスト方針

| 対象 | 方法 |
|---|---|
| ユースケース | インメモリのリポジトリ実装 + `Clock.fixed` で単体テスト。期待値は、ドメインと同じく**問題集の解答例**を使う |
| リポジトリ | Testcontainers の PostgreSQL で、保存 → 復元の往復テスト(`Journal` が等しくなること) |
| `saveAll` | 途中の行で制約違反を起こさせ、1件も残らないことを確認する |
| 締め | ドメインの自己検証(収益・費用の残高がゼロ、資産 = 負債 + 純資産、期末残高 = 次期の期首残高)を、**DB を通したあとでも**確認する |
| API | `@WebMvcTest` で DTO の変換と例外の HTTP 変換を確認 |

## 11. 実装の順序(案)

1. プロジェクトを作り、ドメインを依存に追加し、空の状態でビルドが通るようにする
2. ライブラリのリポジトリのインターフェースに対するインメモリ実装
3. 記帳 → 試算表の照会(ユースケースと API)
4. JDBC 実装 + Flyway + Testcontainers
5. 赤黒訂正
6. 決算整理 → 締め → 再振替
7. 残りの照会(C/F、精算表、株主資本等変動計算書、元帳)
8. 個別取引、固定資産、商品有高帳
9. 認可・監査

## 12. 未決事項

1. (決定済み)リポジトリのインターフェースは、ライブラリの集約と同じパッケージに置く
2. 同じ仕訳を2回取り消すことを防ぐのは、ドメインかアプリか
3. 再振替を締めのあとに自動実行するか、別の操作にするか
4. 締め前の照会で、損益振替前の状態を見せるかどうか
5. 商品有高帳の入出庫明細を、仕訳とは別に保存するかどうか(数量は仕訳に含まれないため)
6. マルチテナント対応の要否
