# AppUpdateResponseReader と検証スクラッチの関数解説

アプリ内アップデートで、サーバから受け取った JSON を APK ファイルへ復元する部分の解説。
対象は次の 2 ファイル。

| ファイル | 役割 |
|---|---|
| `app/src/main/java/.../util/AppUpdateResponseReader.kt` | 本番コード |
| `server/src/test/kotlin/.../scratch/AppUpdateReaderScratch.kt` | 各ヘルパーを素で動かして戻り値を見る検証用 |

## 前提となるレスポンス

```json
{"resultCode":"0","apkBase64":"UEsDBBQ....（約106MB）"}
```

`apkBase64` が 100MB 級になるため、**JSON を丸ごとパースすることはできない**。
値が `String` へ展開された時点でヒープが尽きる（106M 文字 = 約 212MB）。

そこで「先頭の小さな部分だけを覗いて `resultCode` と値の開始位置を確定し、
残りはストリームのままデコードしてファイルへ流す」という構造になっている。
メモリ使用量は応答サイズに依存しない。

この方式の成立条件として **`apkBase64` を JSON の最後のフィールドに置く**ことをサーバと取り決めている。
後ろに別のフィールドがあると、それを読むために本体全体をバッファすることになる。

---

## 全体の流れ

```
read(source, destination)
 │
 ├─ peekHead()                     先頭 8KB を「消費せずに」覗く
 │
 ├─ jsonStringValueOf("resultCode")   値を取り出して成否を判定
 │   └─ jsonStringValueStartOf()      ← 値の開始位置
 │        ├─ indexOfSubArray()        ← キーの位置
 │        └─ skipWhitespaceFrom()     ← 空白読み飛ばし
 │
 ├─ jsonStringValueStartOf("apkBase64")  値の開始位置（バイト）
 ├─ source.skip(valueStart)              Base64 の先頭へ頭出し
 │
 └─ ApkBase64Decoder.decodeToFile(JsonStringValueStream(source), destination)
```

---

# 1. AppUpdateResponseReader.kt（本番）

## 公開されているもの

### `AppUpdateResultCodeException(val resultCode: String)`

サーバが成功以外の結果コードを返したことを表す例外。`IOException` を継承しているので、
`util/ErrorMessages.kt` の分岐で画面向けの文言に変換される。
`IOException` の分岐より**手前**に置かないと「通信に失敗しました」に吸われる点に注意。

### `AppUpdateResponse(resultCode, apk)`

`read()` の戻り値。ただし **成功時の `resultCode` は必ず `"0"`** なので実質的な情報は無く、
本番コードは `apk.file` しか見ていない。

### `RESULT_CODE_SUCCESS = "0"`

成功を表す結果コード。これ以外はすべてエラー扱い。

### `read(source: BufferedSource, destination: File): AppUpdateResponse`

入口。次の順で処理する。

1. `peekHead()` で先頭 8KB を覗く
2. `resultCode` を読む。読めなければ `IOException`
3. `resultCode != "0"` なら `AppUpdateResultCodeException` を投げて**ここで打ち切る**
4. `apkBase64` の値の開始位置を求める。見つからなければ `IOException`
5. `source.skip(valueStart)` で Base64 の先頭へ頭出し
6. `JsonStringValueStream` で包んで `ApkBase64Decoder.decodeToFile()` へ渡す

**3 で打ち切るのが重要**で、エラー応答のときに 106MB のダウンロードに入らずに済む。

## private ヘルパー

### `ByteArray.indexOfSubArray(target): Int`

バイト列の中から部分バイト列を総当たりで探し、先頭位置を返す。見つからなければ `-1`。
`jsonStringValueStartOf` がキー名を探すためだけの下請け。

**最初の 1 件しか返さない**のが後述の落とし穴につながる。

| 入力 | 戻り値 |
|---|---|
| `abcdef` から `abc` / `cd` / `ef` | 0 / 2 / 4 |
| `abcdef` から `xyz` | -1 |
| `aaab` から `aab`（部分一致の先） | 1 |
| target のほうが長い | -1 |
| **target が空** | **0** |
| `あいうえお` から `うえ` | **6**（文字数でなくバイト位置） |

### `ByteArray.skipWhitespaceFrom(from): Int`

`from` から空白を読み飛ばし、空白でない最初の位置を返す。
`{"key" : "value"}` のような空白を許容するためだけに存在する。

空白とみなすのは **半角スペース(32) / タブ(9) / LF(10) / CR(13) の 4 つだけ**。
**全角スペースは空白扱いされない**（JSON の仕様としてはこれで正しい）。
`from` が `size` 以上でもそのまま返すので、範囲外アクセスで落ちることはない。

### `ByteArray.jsonStringValueStartOf(field): Int?`

キー → 空白 → `:` → 空白 → `"` の順に辿り、**開き引用符の次のバイト位置**を返す。
1 つでも崩れたら `null`。

`read()` はこの戻り値で `source.skip()` するため、**この関数が返す「位置」だけが
ストリーム処理を成立させている**。その意味で中核。

```
{"resultCode":"0","apkBase64":"UEsDBBQ"}
 resultCode → 15     apkBase64 → 31
```

| 入力 | 戻り値 |
|---|---|
| `{ "resultCode" : "0" }`（空白あり） | 18 |
| 存在しないキー | null |
| `{"resultCode" "0"}`（コロン無し） | null |
| `{"resultCode":0}`（値が文字列でない） | null |
| `{"resultCode"`（キー直後で切れ） | null |

**落とし穴**: 別フィールドの値の中に同じキー名が埋まっていると、
`indexOfSubArray` が**そちらを先に拾ってしまう**。

```
{"note":"xx "resultCode" xx","resultCode":"0"}
  → 位置 = null / 取れる値 = null
```

最初のマッチの後ろがコロンでないため `null` になり、**本物の `resultCode` には辿り着けない**。
書式が固定されている前提なら起きないが、素朴な実装であることは意識しておく。

### `ByteArray.jsonStringValueOf(field): String?`

開始位置から閉じ引用符までを読み、値そのものを返す。
逆スラッシュが来たら次の 1 文字をそのまま採用する（`\/` → `/`）。

**`read()` では `resultCode` の判定にしか使わない。** `apkBase64` の値には使えない
（100MB が `String` になってしまう）。

| 入力 | 戻り値 |
|---|---|
| `a\/b` | `a/b` |
| `エラー発生` | `エラー発生`（UTF-8 が壊れない） |
| 空文字 | `""` |
| 閉じ引用符が無い | null |
| 末尾が逆スラッシュで切れた | null |

### `BufferedSource.peekHead(): ByteArray`

先頭を最大 8KB だけ **消費せずに** 覗く。100MB の本文を読み進めてしまわないための入口で、
覗いた内容を `jsonStringValueOf` と `jsonStringValueStartOf` に渡す。

okio の `peek()` を使っているのが肝で、**覗いたあとも `source` は 1 バイトも減っていない**。
だから続く `skip(valueStart)` で正確に頭出しできる。

| 入力 | 戻り値 |
|---|---|
| 15 バイト | 15 バイト（そのあと source から同じ内容を読み直せる） |
| 20000 バイト | 8192 バイト（source の残りは 20000 バイトのまま） |
| 空 | 0 バイト |

### `JsonStringValueStream`（private class）

JSON 文字列値の中身だけを返す `InputStream`。エスケープされていない `"` に達したら EOF を返す。

- `read(ByteArray, Int, Int)` を**一括コピーで実装**している。
  `source.buffer.indexOfElement()` でバッファ内の次の区切りを探し、そこまでを一気に移す。
  1 バイトずつ読むと 100MB で 1 億回の呼び出しになるため
- `Buffer.indexOfElement` は**バッファ済みの範囲しか見ない**ので、
  はるか先にある終端の `"` を探して本体全体を抱え込むことはない
- 値が閉じないまま EOF に達したら `EOFException`。**切れたレスポンスを黙って通さない**
- `close()` は何もしない。下層の `ResponseBody` は Repository 側の `use` が閉じる
- 1 バイト読み用に `ByteArray(1)` を使い回し、毎回確保しないようにしている

## 定数

| 定数 | 値 | 意味 |
|---|---|---|
| `MAX_HEAD_BYTES` | 8192 | 先頭から覗く上限。これより後ろに `apkBase64` があると失敗する |
| `QUOTE` / `BACKSLASH` / `COLON` | 34 / 92 / 58 | 文字コード直書き（Kotlin のエスケープを避けるため） |
| `WHITESPACE` | 32, 9, 10, 13 | 空白とみなす 4 バイト |

---

# 2. AppUpdateReaderScratch.kt（検証用）

`server/src/test/kotlin/com/example/messengerapp/scratch/AppUpdateReaderScratch.kt`

## 実行方法

`fun main()` の左の **▶ を押すだけ**。出力は Run ツールウィンドウに出る。

## なぜ :server の test に置いてあるのか

このファイル自体は **JDK 標準ライブラリしか使っていない**ので置き場所は何でもよいが、
次の 2 つを避けるために `:server` にしている。

1. **Kotlin の Scratch File は K2 モードで動かない** — ▶ を押してもエラーも出ず無反応になる
2. **`:app` を選ぶとコマンドライン長 32KB 制限に当たる** — Compose / Firebase / CameraX などの
   jar が数百個 `-cp` に並び、`CreateProcess error=206` で落ちる

`:server` は Ktor だけの軽い JVM モジュールなので、どちらの問題にも当たらない。
`@Test` が無いので `:server:test` には拾われない。

## 本番との違い

| | 本番 | スクラッチ |
|---|---|---|
| 先頭を覗く手段 | okio `BufferedSource.peek()` | `BufferedInputStream` の `mark`/`reset` |
| Base64 の読み取り | `JsonStringValueStream` 経由 | `source` を直接 `decodeToFile` へ |

1 つ目は依存を無くすための置き換えで、振る舞いは同じ（消費せずに先頭 8KB を読む）。
2 つ目は **`JsonStringValueStream` を削除した場合を再現する**ためで、これが検証の主目的。

検証対象である 4 つのヘルパー（`indexOfSubArray` / `skipWhitespaceFrom` /
`jsonStringValueStartOf` / `jsonStringValueOf`）は `ByteArray` の純粋関数で、
**本番と完全に同一のコード**を写している。

## 書き方の約束

JSON が Kotlin のエスケープまみれにならないよう、二重引用符のかわりに `|` と書いて
`j()` で置換している。

```kotlin
j("{|resultCode|:|0|,|apkBase64|:|UEsDBBQ|}")
// → {"resultCode":"0","apkBase64":"UEsDBBQ"}
```

## 出力の構成

| 章 | 内容 |
|---|---|
| 1〜4 | 各ヘルパーを単体で、境界値まで含めて呼ぶ |
| 5 | `peekHead` が「消費しない」ことの実証 |
| 6 | `readResponse` を通した正常系・異常系 |
| 7 | **`JsonStringValueStream` を消すと何が変わるか** |

## 文字化け対策

Windows の JVM は `file.encoding` が UTF-8 でも **`stdout.encoding` は MS932** のままで、
IDE の Run ウィンドウは UTF-8 で読むため日本語が化ける。
`main()` の先頭で stdout を UTF-8 に固定して回避している。

```kotlin
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
```

それでも化ける場合はコンソール側が MS932 なので、
**Settings → Editor → File Encodings → Global Encoding** を UTF-8 にする。

---

# 3. JsonStringValueStream を削除した場合（判断待ち）

7 章の実測結果。`java.util.Base64.getMimeDecoder()` は
**Base64 アルファベット外の文字を黙って無視する**ため、終端の `"` を自分で見張らなくても
正常系は動いてしまう。問題は異常系。

ダミー APK 1028 バイト（Base64 の末尾にパディング `=` が付くサイズ）での結果:

| ケース | 結果 |
|---|---|
| 後続フィールドあり（`apkBase64` が最後でない） | 例外なし / 1028B / 一致 **true** |
| 末尾 1 文字欠落 | 例外なし / 1028B / 一致 **true** |
| 末尾 2 文字欠落 | 例外なし / 1027B / 一致 **false** |
| 末尾 3 文字欠落 | `IOException`（dangling byte） |
| 末尾 4 文字欠落 | 例外なし / 1026B / 一致 **false** |

**4 パターン中 2 つで「成功扱いなのに中身が違う」**。

1 文字欠落が無害なのも、後続フィールドが無害なのも、**パディング `=` に救われているだけ**で
データサイズ依存の偶然。スクラッチの `apkBytes(1024)` を `1023` に変えるとパディングが消え、
結果が変わる。

## 失うもの

1. **切断の検出** — 上記のとおり 3/4 のケースで短い APK が成功扱いになる。
   HTTP 越しのネットワーク切断は OkHttp が `Content-Length` / chunked フレーミングで検出するので
   そこは残るが、**サーバに置いた `response.json` 自体が不完全**なケースは検出できなくなる。
   JSON から `sha256` が消えた今、他に検出手段は無い
2. **「apkBase64 は最後」の契約の性質が変わる** — 今は性能のための契約（破ると遅くなるだけ）だが、
   削除後は正しさのための契約になり、違反しても気づけない
3. **テスト 1 件** — `fails and leaves no file when the response is cut off midway` が落ちる

## 削るなら

代替の検出を 1 つ入れるべき。一番安いのは書き出した APK の ZIP 構造検証で、
`ZipFile(file)` は末尾の central directory しか読まないため 80MB でも高速に切断を捕まえられる。

あるいは「Phase 2 の署名検証（`getPackageArchiveInfo` は壊れた APK に null を返す）が
実質的な防波堤になるので、それまでは許容する」という割り切りも成立する。
ただしその場合、ユーザーから見ると「ダウンロード成功 →（インストール時に）署名が読めません」
という分かりにくい出方になる。

**この判断は未決**。
