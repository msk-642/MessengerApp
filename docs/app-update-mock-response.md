# 模擬レスポンスをアプリ外で用意する方法の検討メモ

PR #8（アプリ内アップデートの APK 取得・復号処理）のマージ後に行った検討の記録。
実装はまだ行っていない。サーバ実装や実機確認に着手するときの出発点として残す。

## 背景

PR #8 で実装済みなのは次の範囲。

- 単一エンドポイント `GET /api/v1/app/latest` が返す `{"resultCode":"0","apkBase64":"..."}` を
  分割受信しながらデコードし、キャッシュディレクトリへ APK として書き出す
- 模擬レスポンスは Gradle タスク `:app:generateMockUpdatePayload` がデバッグ APK から生成し、
  `app/build/mock-update/` に出力する

現状は「アプリのビルド成果物からアプリ自身が模擬データを作る」形になっている。
これを **アプリ外で JSON を作り置きし、それを API の返り値とする** 形に変えたい、
というのが検討の出発点。

### 前提となる契約（変更不可）

```json
{"resultCode":"0","apkBase64":"UEsDBBQ....（約106MB）"}
```

`apkBase64` は **必ず最後のフィールド**に置く。
後続フィールドがあると、それを読むためにクライアントが本体全体をバッファすることになり、
ストリーム処理が成立しない。

---

## 1. アプリ外で JSON を作り、サーバから返す

必要な作業は 3 つだけで、**アプリ側の変更は不要**。

| # | 作業 | 場所 |
|---|---|---|
| 1 | APK から `response.json` を作るスクリプト | アプリ外 |
| 2 | 生成した JSON の置き場所を決める | `server/data/app-update/` |
| 3 | ファイルを返すエンドポイントを足す | `:server` |

### 置き場所

`server/data/app-update/response.json` とする。
`server/.gitignore` が既に `data/` を除外しているため、106MB が誤ってコミットされる心配がない。

既存の `generateMockUpdatePayload` はユニットテストのフィクスチャ生成に使っているので残し、
出力先に `server/data/app-update/` を追加すれば兼用もできる。

### サーバ側（Ktor）

```kotlin
// server/src/main/kotlin/.../server/route/AppUpdateRoutes.kt
fun Route.appUpdateRoutes(payloadDir: File) {
    get("/api/v1/app/latest") {
        val response = File(payloadDir, "response.json")
        if (!response.isFile) {
            throw NotFoundException("模擬レスポンスが未配置です: ${response.absolutePath}")
        }
        // respondFile は中身をメモリに載せず、Content-Length を付けてストリームで返す
        call.respondFile(response)
    }
}
```

`Application.kt` の `routing { }` に `appUpdateRoutes(File("data/app-update"))` を足すだけ。
`respondFile` は拡張子から `application/json` を付ける。

**利点**: 新しい APK を配るときは `response.json` を差し替えるだけでよく、サーバの再起動も要らない
（リクエストのたびにファイルを読むため）。

### gzip は効果が限定的

```kotlin
install(Compression) { gzip { matchContentType(ContentType.Application.Json) } }
```

APK は中身が ZIP で既に圧縮済みのため、gzip が取り返せるのは Base64 による 1.33 倍の膨張分だけ。
106MB → 約 80MB、つまり元の APK サイズに戻る程度にしかならない。

さらに `Content-Length` が消えるため、将来ダウンロード進捗を出したいときに効いてくる。
毎リクエスト 106MB を圧縮する CPU も無駄なので、やるなら事前に `response.json.gz` を作り置きして
`Content-Encoding: gzip` を手で付ける方が筋がよい。

OkHttp は `Accept-Encoding: gzip` を自動付与して透過的に解凍するので、アプリ側の変更は不要。

### サーバを書かない代替案

**静的ファイルサーバ** — ディレクトリ構造をパスに合わせればコード 0 行で済む。

```
server/data/app-update/
  api/v1/app/
    latest        <- response.json をこの名前で置く（拡張子なし）
```

```bash
python -m http.server 8081 --directory server/data/app-update
```

`BASE_URL` を `http://10.0.2.2:8081/` にすればそのまま当たる。
Content-Type は `application/octet-stream` になるが、生ストリームとして読んでいるので問題ない。

**MockWebServer** — テストコード用。ただし `Buffer().readFrom()` は全体をメモリに載せるため、
Instrumentation テストで 106MB は厳しい。回避するにはカスタム `Dispatcher` が要り、
結局 Ktor で書く方が楽になる。

---

## 2. 生成スクリプトの比較（実測）

同じ APK（79,747,049 バイト）で 3 通りを比較した。

| 手段 | 所要時間 | 出力サイズ |
|---|---|---|
| Git Bash `base64 -w0` | 0.120s | 106,329,433 bytes |
| Python | 0.335s | 106,329,433 bytes |
| Gradle タスク（既存） | - | 106,329,433 bytes |

**3 つともバイト単位で完全一致**。

結論として、`base64` が使える環境なら **Git Bash のワンライナーで十分**。
この PC の Git Bash には GNU coreutils 8.32 の `base64` が入っていることを確認済み。

```bash
{ printf '{"resultCode":"0","apkBase64":"'; base64 -w0 app-debug.apk; printf '"}'; } > response.json
```

Python が勝つのは「PowerShell しか開きたくない」場合や、
検算・sha256 出力を同じスクリプトに同居させたい場合くらい。

### Python 版（PowerShell から使いたい場合）

```python
import base64, sys

apk, out = sys.argv[1], sys.argv[2]
CHUNK = 3 * 1024 * 1024   # 必ず 3 の倍数

with open(out, "wb") as f:
    f.write(b'{"resultCode":"0","apkBase64":"')
    with open(apk, "rb") as src:
        while True:
            chunk = src.read(CHUNK)
            if not chunk:
                break
            f.write(base64.b64encode(chunk))
    f.write(b'"}')
```

### 落とし穴

1. **チャンクは必ず 3 の倍数で読む**。3 で割り切れないと各チャンクの末尾にパディングが入り、
   途中にパディングが散らばった壊れた Base64 になる。
2. **`certutil -encode` は使わない**。出力に PEM のヘッダ行が付き、
   その文字列は Base64 アルファベットに含まれる文字だけで構成されているため、
   MIME デコーダがデータとして取り込んでしまう。**エラーも出ずに APK が壊れる。**
3. **全体を一度に Base64 化しない**。80MB の APK から 106M 文字の文字列（約 212MB）が生まれる。
   PowerShell の `[Convert]::ToBase64String()` や、`-w0` なしの `base64` の素朴な使い方に注意。
4. **BOM を付けない**。PowerShell の `Out-File` は BOM を付けがちなので、バイト書き込みを使う。

### 検算

```
期待サイズ = 31 (prefix) + ceil(apkSize / 3) * 4 + 2 (suffix)
```

生成後にこれと実ファイルサイズを突き合わせれば、途中で壊れていないことがすぐ分かる。

---

## 3. サーバを立てずに実機でパススルー確認する最小構成

**OkHttp の Interceptor を 1 つ足すだけ**でよい。
Retrofit → OkHttp → `ResponseBody` → `AppUpdateResponseReader` → `ApkBase64Decoder` → `cacheDir`
という実装したパス全体がそのまま通り、差し替わるのはソケットの部分だけ。

### 手順 1: JSON を端末に置く

```bash
adb push response.json /sdcard/Android/data/com.example.messengerapp/files/response.json
```

このディレクトリは `context.getExternalFilesDir(null)` に対応し、
**パーミッション宣言もユーザー許可も一切不要**でアプリから読める。
`/sdcard/Download/` は Android 16 のスコープドストレージに阻まれるので使わない。

OEM によって `Android/data` への push が蹴られる場合は、デバッグビルドなので `run-as` が使える。

```bash
adb push response.json /data/local/tmp/response.json
adb shell run-as com.example.messengerapp cp /data/local/tmp/response.json files/response.json
```

### 手順 2: Interceptor を足す（`di/NetworkModule.kt` に約 25 行）

```kotlin
/** 端末に置いた模擬レスポンスを返す。ファイルが無ければ素通しするので通常の通信は壊さない */
private class LocalUpdateResponseInterceptor(private val payload: File) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.endsWith("/api/v1/app/latest") || !payload.isFile) {
            return chain.proceed(request)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                payload.source().buffer()
                    .asResponseBody("application/json".toMediaType(), payload.length())
            )
            .build()
    }
}
```

登録は既存の `HttpLoggingInterceptor` と同じ形にできる。

```kotlin
if (BuildConfig.DEBUG) {
    builder.addInterceptor(
        LocalUpdateResponseInterceptor(File(context.getExternalFilesDir(null), "response.json"))
    )
}
```

`provideOkHttpClient` に `@ApplicationContext context: Context` を 1 つ足すだけ。

**要点**: `asResponseBody` にファイルの `BufferedSource` を渡すこと。
ここで `readBytes()` を使うとストリーム設計が台無しになる。

### 手順 3: 叩くボタン

まだ UI 導線が無いため、`ui/screen/settings/SettingsScreen.kt` に `BuildConfig.DEBUG` で囲んだ
一時ボタンを置き、`AppUpdateRepository.downloadLatestApkToCache()` を呼んで
結果を Logcat か Snackbar に出すのが最小。

### 手順 4: 結果の検証

```bash
# 端末側
adb shell run-as com.example.messengerapp sha256sum cache/update/app-latest.apk

# PC 側
sha256sum app/build/mock-update/app-debug.apk
```

一致すれば、実機上でバイト単位に復元できたことの完全な証明になる。

### この方法で確認できること / できないこと

**確認できる**（一番知りたいこと）:

- 実機のヒープ制限下で **OOM せずに 106MB を処理しきれるか**。
  Interceptor 経由でもリーダーから下のメモリ挙動はソケット経由と同一なので、
  ストリーム設計が本当に効いているかはここで分かる。Profiler でヒープが平坦なままか見れば一目瞭然
- `cacheDir` に 80MB 書き込めるか（空き容量）
- 復元結果がバイト単位で正しいか

**確認できない**:

- 実際のソケット越しの分割受信。`BufferedSource` が返すチャンクの刻まれ方が
  ネットワークとファイルで違うため、通信特有のタイミング問題は出ない。
  これは `:server` を立てて初めて確認できる

---

## 次のステップ（いずれも未着手）

1. **実機パススルー確認** — 上記 3 章の Interceptor 追加。メモリ挙動の確認が主目的
2. **サーバ実装** — `:server` に `GET /api/v1/app/latest` を追加。
   `apkBase64` を最後のフィールドに出力する契約を守ること
3. **Phase 2（署名比較とインストール）**
   - `PackageManager.getPackageArchiveInfo` での署名取得
   - 署名一致に加えて `versionCode` が現在より大きいことの確認
   - `REQUEST_INSTALL_PACKAGES` 権限と「提供元不明のアプリ」のユーザー許可
   - FileProvider（`<cache-path>`）
   - `util/ErrorMessages.kt` に `AppUpdateResultCodeException` の分岐を追加
   - UI 導線

### 積み残しの論点

- エンドポイントが 1 本になったため、「更新があるか」の確認だけでも毎回 106MB を落とすことになる。
  サーバが `resultCode = "NO_UPDATE"` のとき `apkBase64` を空で返す運用で回避できる
- JSON から `sha256` が消えたため、ダウンロード直後の完全性検証ができない。
  Phase 2 の署名検証が実質的な代替になる
- 署名一致は Android 自身も強制するため、これはセキュリティ境界ではなく UX のための事前チェック
