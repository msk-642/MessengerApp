import java.security.MessageDigest
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)

    // Firebase Plugins
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.messengerapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.messengerapp"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ローカルの :server モジュールの待ち受け先。10.0.2.2 はエミュレータから見たホスト PC。
        // 実機を LAN 経由で繋ぐ場合はここを PC の LAN IP に変えて
        // network_security_config.xml にも同じホストを追記する。
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// サーバ未実装の間、GET /api/v1/app/latest の代わりになる模擬レスポンスを生成する。
// 生成物は build/ 配下なのでコミットされない（Base64 は APK の約 1.33 倍のサイズになる）。
val mockUpdateDir = layout.buildDirectory.dir("mock-update")

tasks.register("generateMockUpdatePayload") {
    group = "build"
    description = "デバッグ APK から模擬レスポンス JSON を生成する"
    dependsOn("assembleDebug")

    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")
    val outDirProvider = mockUpdateDir

    doLast {
        val apk = apkDir.get().asFile.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
            ?: error("デバッグ APK が見つかりません: ${apkDir.get().asFile}")

        val outDir = outDirProvider.get().asFile
        outDir.mkdirs()

        // 復元結果と突き合わせるための原本
        val apkCopy = outDir.resolve("app-debug.apk")
        apk.copyTo(apkCopy, overwrite = true)

        // {"resultCode":"0","apkBase64":"...."} 全体が約 106MB になるため、
        // こちらもメモリへ載せずストリームで組み立てる。apkBase64 は必ず最後のフィールドに置く。
        val responseFile = outDir.resolve("response.json")
        responseFile.outputStream().buffered().use { out ->
            out.write("{\"resultCode\":\"0\",\"apkBase64\":\"".toByteArray(Charsets.UTF_8))
            // wrap した側を閉じないと Base64 のパディングが書かれないが、閉じると out まで閉じてしまう
            Base64.getEncoder().wrap(out).use { encoded ->
                apkCopy.inputStream().buffered().use { it.copyTo(encoded) }
            }
        }
        // そのため JSON の終端は追記で書く
        responseFile.appendText("\"}", Charsets.UTF_8)

        val digest = MessageDigest.getInstance("SHA-256")
        apkCopy.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }

        logger.lifecycle("模擬レスポンスを生成しました: ${outDir.absolutePath}")
        logger.lifecycle("  APK          : ${apkCopy.length()} bytes")
        logger.lifecycle("  response.json: ${responseFile.length()} bytes")
        logger.lifecycle("  SHA-256      : ${digest.digest().joinToString("") { "%02x".format(it) }}")
    }
}

tasks.withType<Test> {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    // 実 APK のラウンドトリップテストから模擬レスポンスを参照する（未生成ならテストはスキップされる）
    systemProperty("mockUpdateDir", mockUpdateDir.get().asFile.absolutePath)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation and UI
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Retrofit & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Firebase Dependencies
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics:23.2.0")
    implementation("com.google.firebase:firebase-auth-ktx:23.2.1")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.4")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.2")
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.2")

    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

}