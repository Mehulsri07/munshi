# Munshi Android Soundbox App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android app that is the Munshi Soundbox on the counter: it pairs with the server, speaks first in Hindi, takes "haan" by voice or tap, shows the pay QR, stamps "Chuka diya" when a payment lands, and never blocks when the network, mic, camera or Claude fails.

**Architecture:** One Gradle module in `android/`. A pure-Kotlin `Conductor` (a port of `web/Soundbox.tsx`'s session logic) owns a `StateFlow<UiState>` and talks to the server, speech and events only through interfaces, so it is unit-tested with fakes. Compose screens render `UiState` through a stateless `MainContent`, which Paparazzi screenshot-tests on phone and tablet. An `Application`-level graph wires real implementations; a foreground service keeps the event stream alive.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 as a base only), kotlinx.coroutines, kotlinx.serialization, OkHttp + okhttp-sse, DataStore, Google code scanner (ML Kit), ZXing core, Paparazzi, JUnit 4, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-25-android-app-and-ui-design.md` (§3–6, §9–10). Depends on plan `2026-10-25-tokens-and-server.md` being done (it provides `Tokens.kt`, `display`, `spoken`, `/api/brief`, richer events and the pairing QR).

## Global Constraints

- Application id and package: `com.opuscube.munshi`. minSdk 26, compileSdk/targetSdk 36.
- Colours, radii, spacing, type sizes and motion come only from generated `Tokens` (never hard-coded hex in Compose code).
- Fonts: Fraunces (display, rupee figures, italic lines) and Inter (labels, body, buttons), bundled in `res/font`.
- Every side effect goes through the server's confirm endpoint; the app never acts without Haan (tap or recognised speech).
- The app never shows or stores a customer's payer identity.
- Money: whole rupees, `₹` + Indian grouping (`₹1,192`, `₹50,000`).
- No dependency-injection framework; manual wiring in `MunshiApp`.
- Work on branch `feat/app-redesign`; run Gradle from `android/`.
- Deliberately not built (spec allows it): the optional idle tick. On stage `/control` → "Munshi, aur kuch?" cues the same nudge.
- Toolchain as built (changed from Task 1's catalog during Task 8): Gradle 8.14.3, AGP 8.13.1, Kotlin 2.1.21, Paparazzi 2.0.0-alpha02, run with JBR 21 (`~/.jdks/jbr-21.0.11`). Paparazzi 1.3.5's layoutlib cannot render compileSdk 36; 2.0.0-alpha02 can, and it needs Kotlin 2.1.21 and Gradle 8.14 on the build classpath.
- CI runs `testDebugUnitTest assembleDebug` (screenshots render, so layout crashes fail the build) but not `verifyPaparazziDebug`: the golden images are recorded on Windows and Linux text anti-aliasing differs by a few pixels. Verify locally before committing UI changes.
- Shelf camera: the system camera app via `ActivityResultContracts.TakePicture` (full-screen, no camera code to maintain) instead of an in-app CameraX preview; gallery picker as fallback. Same user-visible behaviour as spec §4.

## File Structure

```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/font/{fraunces_regular,fraunces_semibold,fraunces_italic,inter_regular,inter_semibold}.ttf
  app/src/main/res/xml/file_paths.xml
  app/src/main/java/com/opuscube/munshi/
    MunshiApp.kt                 Application + Graph (manual wiring)
    MainActivity.kt              Pair or Main, keep-screen-on, permissions
    api/Models.kt                @Serializable request/response + event types
    api/MunshiApi.kt             interface + ApiError
    api/HttpMunshiApi.kt         OkHttp implementation
    events/EventStream.kt        SSE flow with reconnect/backoff
    pairing/Config.kt            Config, parse(), DataStore store
    voice/Line.kt                Line(text, spoken)
    voice/Speaker.kt             interface + AndroidSpeaker (TTS queue)
    voice/Listener.kt            interface + AndroidListener (SpeechRecognizer)
    voice/IntentMatcher.kt       haan / nahi fast path (port of classifyReply)
    voice/Chime.kt               two-note chime + listen tick
    conductor/UiState.kt         state the screen renders
    conductor/Conductor.kt       the session state machine
    camera/ShelfPhoto.kt         downscale to 1280 px JPEG, base64
    service/MunshiService.kt     foreground service
    ui/theme/Tokens.kt           GENERATED (plan a)
    ui/theme/Type.kt             FontFamilies, text styles
    ui/components/*.kt           LedgerPage, RupeeFigure, InkDock, WeekChip, ChukaDiyaStamp, QrCard, Banner, HisaabSheet, Kicker
    ui/screens/MainContent.kt    stateless, renders UiState (phone + tablet)
    ui/screens/MainScreen.kt     collects Conductor state, wires actions
    ui/screens/PairScreen.kt
  app/src/test/java/com/opuscube/munshi/...   unit, contract and screenshot tests
  app/src/test/resources/fixtures/*.json
```

---

### Task 1: Scaffold the project and prove the toolchain

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/java/com/opuscube/munshi/MainActivity.kt`, `android/.gitignore`
- Create: Gradle wrapper files

- [ ] **Step 1: Version catalog**

`android/gradle/libs.versions.toml` (these versions are already in this machine's Gradle cache; Step 6 says what to do if Gradle refuses the bundled JDK):

```toml
[versions]
agp = "8.13.1"
kotlin = "2.0.21"
composeBom = "2024.09.00"
coroutines = "1.9.0"
serialization = "1.7.3"
okhttp = "4.12.0"
activityCompose = "1.9.2"
lifecycle = "2.8.6"
datastore = "1.1.1"
codeScanner = "16.1.0"
zxing = "3.5.3"
junit = "4.13.2"
paparazzi = "1.3.5"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-sse = { group = "com.squareup.okhttp3", name = "okhttp-sse", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
code-scanner = { group = "com.google.android.gms", name = "play-services-code-scanner", version.ref = "codeScanner" }
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
paparazzi = { id = "app.cash.paparazzi", version.ref = "paparazzi" }
```

- [ ] **Step 2: Root Gradle files**

`android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "munshi"
include(":app")
```

`android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.paparazzi) apply false
}
```

`android/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

`android/.gitignore`:

```
.gradle/
build/
local.properties
.idea/
*.iml
```

- [ ] **Step 3: App module**

`android/app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.opuscube.munshi"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.opuscube.munshi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.datastore.preferences)
    implementation(libs.code.scanner)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
```

- [ ] **Step 4: Manifest and a placeholder activity**

`android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <queries>
        <intent><action android:name="android.speech.RecognitionService" /></intent>
        <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
    </queries>

    <application
        android:name=".MunshiApp"
        android:label="Munshi"
        android:usesCleartextTraffic="true"
        android:supportsRtl="false"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

(`usesCleartextTraffic` lets the app reach `http://10.0.2.2:3000` on the emulator; the tunnel is HTTPS.)

`android/app/src/main/java/com/opuscube/munshi/MainActivity.kt` (placeholder, replaced in Task 11):

```kotlin
package com.opuscube.munshi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Munshi") }
    }
}
```

and a temporary `android/app/src/main/java/com/opuscube/munshi/MunshiApp.kt`:

```kotlin
package com.opuscube.munshi

import android.app.Application

class MunshiApp : Application()
```

- [ ] **Step 5: Gradle wrapper**

From `android/`, generate the wrapper using the cached distribution:

```bash
"C:/Users/manvi/.gradle/wrapper/dists/gradle-8.13-bin/"*/gradle-8.13/bin/gradle wrapper --gradle-version 8.13
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` created.

- [ ] **Step 6: Build with Android Studio's JDK**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`.

If Gradle fails with `Unsupported class file major version` or "Gradle does not support Java 25": set `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to `https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip`, rerun; if AGP then reports it needs a newer/older Gradle, open `android/` in Android Studio once and accept the AGP Upgrade Assistant's recommended AGP + Kotlin versions, copy them into `libs.versions.toml`, and rerun this step. Record the final versions in the commit message.

- [ ] **Step 7: Run on the emulator**

```bash
EMU="$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"; ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$EMU" -avd Medium_Phone_API_36.1 -no-snapshot-save &
"$ADB" wait-for-device && "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.opuscube.munshi/.MainActivity
"$ADB" exec-out screencap -p > /tmp/munshi-hello.png
```

Expected: the screenshot shows "Munshi".

- [ ] **Step 8: Commit**

```bash
git add android/
git commit -m "feat(android): scaffold Compose app and toolchain"
```

---

### Task 2: API models and HTTP client, with contract tests

**Files:**
- Create: `android/app/src/main/java/com/opuscube/munshi/api/Models.kt`, `api/MunshiApi.kt`, `api/HttpMunshiApi.kt`
- Create: `android/app/src/test/resources/fixtures/{next,brief,confirm,shelf,ledger,events}.json`
- Test: `android/app/src/test/java/com/opuscube/munshi/api/ContractTest.kt`, `api/HttpMunshiApiTest.kt`

**Interfaces:**
- Produces: `MunshiJson` (configured `Json`), data classes `Display, Next, BriefItem, Brief, PayFor, ActionResult, Offer, ShelfResult, LedgerItem, Loan, Ledger`, sealed `SoundboxEvent` (`Announce, BriefCue, Nudge, LedgerChanged, Paid, Reset, Shelf`), `fun parseEvent(data: String): SoundboxEvent?`, `interface MunshiApi`, `class ApiError(status: Int, message: String)`, `class HttpMunshiApi(baseUrl: String, key: String, client: OkHttpClient)`.

- [ ] **Step 1: Capture real fixtures from the server**

With the server from plan (a) running (`npm run server` in the repo root, fresh boot), run from `android/`:

```bash
F=app/src/test/resources/fixtures; mkdir -p $F
curl -s -X POST localhost:3000/api/next -H 'content-type: application/json' -d '{}' > $F/next.json
curl -s localhost:3000/api/brief > $F/brief.json
ID=$(node -e "console.log(require('./$F/next.json').id)")
curl -s -X POST localhost:3000/api/opportunities/$ID/confirm -H 'content-type: application/json' -d '{}' > $F/confirm.json
curl -s -X POST localhost:3000/api/control/shelf > $F/shelf.json
curl -s localhost:3000/api/ledger > $F/ledger.json
```

Then write `$F/events.json` by hand (one SSE `data:` payload per array element, as the server sends them):

```json
[
  {"type":"announce","text":"Sharma ji ne ₹450 chuka diye","spoken":"Sharma ji ने ₹450 चुका दिए"},
  {"type":"paid","ref":"ab12cd","name":"Sharma ji","amount":450},
  {"type":"brief"}, {"type":"nudge"}, {"type":"ledger"}, {"type":"reset"},
  {"type":"shelf","line":"Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain.","spoken":"शेल्फ़ देख ली: Surf Excel 1kg और Parle-G कम हैं।","next":{"id":7,"line":"Surf Excel 1kg ka group order chal raha hai","spoken":"…","display":{"who":"Surf Excel 1kg group order","amount":143,"rest":"bachenge: 4 dukaanein, 8% bachat. Jud jaaun?"}}},
  {"type":"something_new","x":1}
]
```

- [ ] **Step 2: Write the failing contract test**

`api/ContractTest.kt`:

```kotlin
package com.opuscube.munshi.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.*
import org.junit.Test

class ContractTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun `next has display and spoken`() {
        val n = MunshiJson.decodeFromString<Next>(fixture("next.json"))
        assertNotNull(n.id)
        assertEquals("Sharma ji ka", n.display!!.who)
        assertEquals(450, n.display!!.amount)
        assertTrue(n.spoken!!.contains("उधार"))
    }

    @Test fun `brief lists three with a total`() {
        val b = MunshiJson.decodeFromString<Brief>(fixture("brief.json"))
        assertEquals(3, b.items.size)
        assertEquals(b.items.sumOf { it.amount }, b.total)
    }

    @Test fun `confirm returns a pay link for udhaar`() {
        val r = MunshiJson.decodeFromString<ActionResult>(fixture("confirm.json"))
        assertTrue(r.payUrl!!.contains("/pay/"))
        assertEquals(450, r.payFor!!.amount)
    }

    @Test fun `shelf next carries a display`() {
        val s = MunshiJson.decodeFromString<ShelfResult>(fixture("shelf.json"))
        assertEquals("Surf Excel 1kg group order", s.next!!.display!!.who)
    }

    @Test fun `ledger parses with or without a loan`() {
        val l = MunshiJson.decodeFromString<Ledger>(fixture("ledger.json"))
        assertTrue(l.total >= 0)
    }

    @Test fun `every known event parses and unknown ones are ignored`() {
        val arr = MunshiJson.parseToJsonElement(fixture("events.json")) as JsonArray
        val parsed = arr.map { parseEvent(it.toString()) }
        assertEquals(SoundboxEvent.Announce("Sharma ji ne ₹450 chuka diye", "Sharma ji ने ₹450 चुका दिए"), parsed[0])
        assertEquals(SoundboxEvent.Paid("ab12cd", "Sharma ji", 450), parsed[1])
        assertEquals(listOf(SoundboxEvent.BriefCue, SoundboxEvent.Nudge, SoundboxEvent.LedgerChanged, SoundboxEvent.Reset), parsed.subList(2, 6))
        assertEquals(143, (parsed[6] as SoundboxEvent.Shelf).next!!.display!!.amount)
        assertNull(parsed[7])
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*ContractTest*'`
Expected: FAIL — unresolved references `Next`, `MunshiJson`, `parseEvent`.

- [ ] **Step 4: Write the models**

`api/Models.kt`:

```kotlin
package com.opuscube.munshi.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val MunshiJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type"; explicitNulls = false }

@Serializable data class Display(val who: String, val amount: Int, val rest: String)
@Serializable data class Next(val id: Long? = null, val type: String? = null, val line: String? = null, val spoken: String? = null, val display: Display? = null)
@Serializable data class BriefItem(val id: Long, val type: String, val title: String, val amount: Int)
@Serializable data class Brief(val items: List<BriefItem>, val total: Int)
@Serializable data class PayFor(val name: String, val amount: Int)
@Serializable data class ActionResult(
    val line: String, val spoken: String? = null, val payUrl: String? = null,
    val payFor: PayFor? = null, val opportunityId: Long? = null,
)
@Serializable data class Offer(val id: Long, val line: String, val spoken: String? = null, val display: Display? = null)
@Serializable data class ShelfResult(val line: String, val spoken: String? = null, val next: Offer? = null)
@Serializable data class LedgerItem(val kind: String, val amount: Int, val label: String, val ts: String, val tool: String)
@Serializable data class Loan(val principal: Int, val totalCost: Int, val dailyDeduction: Int, val tenureDays: Int)
@Serializable data class Ledger(val total: Int, val items: List<LedgerItem>, val loan: Loan? = null)

@Serializable
sealed interface SoundboxEvent {
    @Serializable @SerialName("announce") data class Announce(val text: String, val spoken: String? = null) : SoundboxEvent
    @Serializable @SerialName("brief") data object BriefCue : SoundboxEvent
    @Serializable @SerialName("nudge") data object Nudge : SoundboxEvent
    @Serializable @SerialName("ledger") data object LedgerChanged : SoundboxEvent
    @Serializable @SerialName("paid") data class Paid(val ref: String, val name: String? = null, val amount: Int? = null) : SoundboxEvent
    @Serializable @SerialName("reset") data object Reset : SoundboxEvent
    @Serializable @SerialName("shelf") data class Shelf(val line: String, val spoken: String? = null, val next: Offer? = null) : SoundboxEvent
}

// Newer servers may send event types this build does not know; those are skipped, not fatal.
fun parseEvent(data: String): SoundboxEvent? =
    runCatching { MunshiJson.decodeFromString(SoundboxEvent.serializer(), data) }.getOrNull()
```

`api/MunshiApi.kt`:

```kotlin
package com.opuscube.munshi.api

class ApiError(val status: Int, message: String) : Exception(message)

interface MunshiApi {
    suspend fun brief(): Brief
    suspend fun next(scope: String = "brief", interrupt: Boolean = false): Next
    suspend fun confirm(id: Long): ActionResult
    suspend fun decline(id: Long): ActionResult
    suspend fun converse(text: String): ActionResult
    suspend fun shelf(jpegBase64: String): ShelfResult
    suspend fun ledger(): Ledger
}
```

- [ ] **Step 5: Run the contract test**

Run: `./gradlew testDebugUnitTest --tests '*ContractTest*'`
Expected: PASS (6 tests).

- [ ] **Step 6: Write the failing HTTP client test**

`api/HttpMunshiApiTest.kt`:

```kotlin
package com.opuscube.munshi.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpMunshiApiTest {
    private val server = MockWebServer()
    private lateinit var api: HttpMunshiApi

    @Before fun up() { server.start(); api = HttpMunshiApi(server.url("/").toString().trimEnd('/'), "k1", OkHttpClient()) }
    @After fun down() = server.shutdown()

    @Test fun `next posts scope and sends the demo key`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":3,"line":"x","display":{"who":"a","amount":1,"rest":"b"}}"""))
        val n = api.next(scope = "all", interrupt = true)
        val req = server.takeRequest()
        assertEquals("/api/next", req.path)
        assertEquals("k1", req.getHeader("x-demo-key"))
        assertEquals("""{"scope":"all","interrupt":true}""", req.body.readUtf8())
        assertEquals(3L, n.id)
    }

    @Test fun `server errors become ApiError with the status and message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"not_proposed"}"""))
        val e = runCatching { api.confirm(5) }.exceptionOrNull() as ApiError
        assertEquals(409, e.status)
        assertEquals("not_proposed", e.message)
        assertEquals("/api/opportunities/5/confirm", server.takeRequest().path)
    }
}
```

- [ ] **Step 7: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*HttpMunshiApiTest*'`
Expected: FAIL — unresolved reference `HttpMunshiApi`.

- [ ] **Step 8: Write the client**

`api/HttpMunshiApi.kt`:

```kotlin
package com.opuscube.munshi.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpMunshiApi(private val baseUrl: String, private val key: String, private val client: OkHttpClient) : MunshiApi {
    private val jsonType = "application/json".toMediaType()

    private suspend inline fun <reified T> call(method: String, path: String, body: String? = null): T = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path).header("x-demo-key", key)
            .method(method, body?.toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { MunshiJson.parseToJsonElement(text).jsonObject["error"]!!.jsonPrimitive.content }.getOrNull()
                throw ApiError(res.code, msg ?: res.message)
            }
            MunshiJson.decodeFromString(serializer<T>(), text)
        }
    }

    override suspend fun brief(): Brief = call("GET", "/api/brief")
    override suspend fun next(scope: String, interrupt: Boolean): Next =
        call("POST", "/api/next", buildJsonObject { put("scope", JsonPrimitive(scope)); put("interrupt", JsonPrimitive(interrupt)) }.toString())
    override suspend fun confirm(id: Long): ActionResult = call("POST", "/api/opportunities/$id/confirm", "{}")
    override suspend fun decline(id: Long): ActionResult = call("POST", "/api/opportunities/$id/decline", "{}")
    override suspend fun converse(text: String): ActionResult =
        call("POST", "/api/converse", buildJsonObject { put("text", JsonPrimitive(text)) }.toString())
    override suspend fun shelf(jpegBase64: String): ShelfResult =
        call("POST", "/api/shelf", buildJsonObject { put("image", JsonPrimitive(jpegBase64)); put("mediaType", JsonPrimitive("image/jpeg")) }.toString())
    override suspend fun ledger(): Ledger = call("GET", "/api/ledger")
}
```

(`put` on `JsonObjectBuilder` needs `import kotlinx.serialization.json.put` if your IDE prefers the extension form; the member form above compiles as written.)

- [ ] **Step 9: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (8 tests).

- [ ] **Step 10: Commit**

```bash
git add android/app/src
git commit -m "feat(android): typed API client with contract tests against real fixtures"
```

---

### Task 3: Event stream with reconnect

**Files:**
- Create: `android/app/src/main/java/com/opuscube/munshi/events/EventStream.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/events/EventStreamTest.kt`

**Interfaces:**
- Consumes: `parseEvent`, `SoundboxEvent` (Task 2).
- Produces: `class EventStream(baseUrl: String, key: String, client: OkHttpClient)` with `val live: StateFlow<Boolean>` and `fun events(): Flow<SoundboxEvent>`; `fun backoffMs(attempt: Int): Long`.

- [ ] **Step 1: Write the failing test**

`events/EventStreamTest.kt`:

```kotlin
package com.opuscube.munshi.events

import com.opuscube.munshi.api.SoundboxEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class EventStreamTest {
    @Test fun `backoff grows to a 15 second ceiling`() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L), (0..5).map(::backoffMs))
    }

    @Test fun `parses SSE data lines, skips comments and unknown types, reconnects after close`() = runBlocking {
        val server = MockWebServer().apply { start() }
        val sse = { body: String -> MockResponse().setHeader("content-type", "text/event-stream").setBody(body) }
        server.enqueue(sse(": connected\n\ndata: {\"type\":\"brief\"}\n\ndata: {\"type\":\"mystery\"}\n\n"))
        server.enqueue(sse("data: {\"type\":\"paid\",\"ref\":\"r1\",\"name\":\"Sharma ji\",\"amount\":450}\n\n"))
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val stream = EventStream(server.url("/").toString().trimEnd('/'), "k", client, backoff = { 10L })
        val got = withTimeout(5000) { stream.events().take(2).toList() }
        assertEquals(listOf(SoundboxEvent.BriefCue, SoundboxEvent.Paid("r1", "Sharma ji", 450)), got)
        assertEquals("/api/events?key=k", server.takeRequest().path)
        server.shutdown()
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*EventStreamTest*'`
Expected: FAIL — unresolved reference `EventStream`.

- [ ] **Step 3: Write the stream**

`events/EventStream.kt`:

```kotlin
package com.opuscube.munshi.events

import com.opuscube.munshi.api.SoundboxEvent
import com.opuscube.munshi.api.parseEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.URLEncoder

fun backoffMs(attempt: Int): Long = minOf(15_000L, 1000L shl minOf(attempt, 4))

// The server's live feed: announcements, payments, cues from /control. Reconnects forever
// with backoff; `live` drives the "Jud raha hai…" dot.
class EventStream(
    private val baseUrl: String,
    private val key: String,
    private val client: OkHttpClient,
    private val backoff: (Int) -> Long = ::backoffMs,
) {
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live

    fun events(): Flow<SoundboxEvent> = channelFlow {
        var attempt = 0
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            val req = Request.Builder().url("$baseUrl/api/events?key=${URLEncoder.encode(key, "UTF-8")}").build()
            val source = EventSources.createFactory(client).newEventSource(req, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) { _live.value = true; attempt = 0 }
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    parseEvent(data)?.let { trySendBlocking(it) }
                }
                override fun onClosed(eventSource: EventSource) { _live.value = false; closed.complete(Unit) }
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) { _live.value = false; closed.complete(Unit) }
            })
            try { closed.await() } finally { source.cancel() }
            delay(backoff(attempt++))
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "feat(android): SSE event stream with backoff reconnect"
```

---

### Task 4: Haan / nahi fast path

**Files:**
- Create: `android/app/src/main/java/com/opuscube/munshi/voice/IntentMatcher.kt`, `voice/Line.kt`
- Create: `android/app/src/main/java/com/opuscube/munshi/conductor/Answer.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/voice/IntentMatcherTest.kt`

**Interfaces:**
- Produces: `sealed interface Answer { Yes; No; data class Text(text); Skip }`; `object IntentMatcher { fun match(text: String): Answer? }` (Yes / No / null = "send to the agent"); `data class Line(text: String, spoken: String? = null)`.

- [ ] **Step 1: Write the failing test** (same cases as `server/test/agent.test.ts`, so app and server agree)

`voice/IntentMatcherTest.kt`:

```kotlin
package com.opuscube.munshi.voice

import com.opuscube.munshi.conductor.Answer
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentMatcherTest {
    @Test fun yes() {
        for (t in listOf("haan", "Haan ji", "हाँ", "हां जी", "theek hai", "bhej do", "हाँ भेज दो", "हां हां", "ओके", "हाँ,", "Haan, bhej do!", "ok ji", "kar do", "ठीक है जी", "bhej do na", "भेज दो ना"))
            assertEquals(t, Answer.Yes, IntentMatcher.match(t))
    }
    @Test fun no() {
        for (t in listOf("nahi", "नहीं", "rehne do", "नहीं चाहिए", "nahi chahiye", "na na", "mat bhejo", "अभी नहीं", "नहीं।"))
            assertEquals(t, Answer.No, IntentMatcher.match(t))
    }
    @Test fun other() {
        for (t in listOf("kitna udhaar hai?", "haan par kal bhejna", "haan nahi", "pehle Pinky ko bhejo", ""))
            assertEquals(t, null, IntentMatcher.match(t))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*IntentMatcherTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the types and matcher**

`conductor/Answer.kt`:

```kotlin
package com.opuscube.munshi.conductor

sealed interface Answer {
    data object Yes : Answer
    data object No : Answer
    data class Text(val text: String) : Answer
    data object Skip : Answer
}
```

`voice/Line.kt`:

```kotlin
package com.opuscube.munshi.voice

// What Munshi says: the Roman Hinglish line (shown, and spoken by an en-IN voice) and,
// when the server knows it, the same line in Devanagari for a hi-IN voice.
data class Line(val text: String, val spoken: String? = null)
```

`voice/IntentMatcher.kt`:

```kotlin
package com.opuscube.munshi.voice

import com.opuscube.munshi.conductor.Answer
import java.text.Normalizer

// Port of classifyReply in server/agent.ts: yes (or no) when every word is a yes-word or a polite
// filler and at least one is a real yes. Anything else goes to the agent.
object IntentMatcher {
    private fun words(s: String) = s.split(' ').toSet()
    private val YES_CORE = words("haan haa han ha hanji haanji yes ok okay theek thik zaroor bhej bhejo kar karo हाँ हां हा ओके ठीक ज़रूर जरूर भेज भेजो कर करो")
    private val YES_FILLER = words("ji जी do दो de दे dijiye दीजिए hai है please")
    private val NO_CORE = words("nahi nahin na no mat nako rehne नहीं नही ना मत रहने")
    private val NO_FILLER = words("ji जी chahiye चाहिए abhi अभी do दो bhej bhejo kar karo भेज भेजो कर करो hai है")
    private val PUNCT = Regex("[\\p{P}\\p{S}]")

    private fun allOf(tokens: List<String>, core: Set<String>, filler: Set<String>) =
        tokens.any { it in core } && tokens.all { it in core || it in filler }

    fun match(text: String): Answer? {
        val tokens = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
            .replace(PUNCT, " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.size > 1 && tokens.last() in setOf("na", "ना")) tokens.removeAt(tokens.lastIndex)
        if (tokens.isEmpty()) return null
        if (allOf(tokens, NO_CORE, NO_FILLER)) return Answer.No
        return if (allOf(tokens, YES_CORE, YES_FILLER)) Answer.Yes else null
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "feat(android): haan/nahi fast path matching the server's classifier"
```

---

### Task 5: The Conductor

**Files:**
- Create: `android/app/src/main/java/com/opuscube/munshi/voice/Speaker.kt` (interface only here), `voice/Listener.kt` (interface only here)
- Create: `android/app/src/main/java/com/opuscube/munshi/conductor/UiState.kt`, `conductor/Conductor.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/conductor/ConductorTest.kt`, `conductor/Fakes.kt`

**Interfaces:**
- Consumes: `MunshiApi`, `ApiError`, models, `SoundboxEvent` (Task 2); `Answer`, `Line`, `IntentMatcher` (Task 4).
- Produces:
  - `interface Speaker { suspend fun say(line: Line); fun stop() }`
  - `interface Listener { val available: Boolean; suspend fun listen(windowMs: Long, onPartial: (String) -> Unit): String? }`
  - `enum class Mode { Brief, Speaking, Listening, Working, Idle }`
  - `data class Ask(val id: Long, val display: Display?, val line: Line)`, `data class PayQr(val url: String, val ref: String, val name: String?, val amount: Int?)`, `data class Stamp(val ref: String, val name: String, val amount: Int)`, `data class UiState(...)` (fields below)
  - `class Conductor(api, speaker, listener, scope, listenWindowMs = Tokens.MotionListenWindowMs)` with `val state: StateFlow<UiState>`, `fun start()`, `fun openShop()`, `fun answer(a: Answer)`, `fun mic()`, `fun shelf(jpegBase64: String)`, `fun onEvent(e: SoundboxEvent)`, `fun onLive(live: Boolean)`

- [ ] **Step 1: Interfaces and state**

`voice/Speaker.kt`:

```kotlin
package com.opuscube.munshi.voice

interface Speaker {
    /** Speaks after anything already queued; returns when done or stopped. */
    suspend fun say(line: Line)
    /** Stops the current utterance (barge-in). Queued lines still play. */
    fun stop()
}
```

`voice/Listener.kt`:

```kotlin
package com.opuscube.munshi.voice

interface Listener {
    val available: Boolean
    /** Listens once; returns the final transcript, or null on silence, error or timeout. */
    suspend fun listen(windowMs: Long, onPartial: (String) -> Unit): String?
}
```

`conductor/UiState.kt`:

```kotlin
package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.Brief
import com.opuscube.munshi.api.Display
import com.opuscube.munshi.api.Ledger
import com.opuscube.munshi.voice.Line

enum class Mode { Brief, Speaking, Listening, Working, Idle }

data class Ask(val id: Long, val display: Display?, val line: Line)
data class PayQr(val url: String, val ref: String, val name: String?, val amount: Int?)
data class Stamp(val ref: String, val name: String, val amount: Int)

data class UiState(
    val mode: Mode = Mode.Brief,
    val brief: Brief? = null,
    val caption: Line? = null,       // what Munshi last said, when it is not a question
    val ask: Ask? = null,            // the open question; Haan / Nahi are enabled only when set
    val heard: String? = null,       // "Aapne kaha: …"
    val payQr: PayQr? = null,        // stays until its ref is paid
    val stamp: Stamp? = null,        // "Chuka diya", until the next question
    val banner: String? = null,      // announcement band, 6.5 s
    val ledger: Ledger? = null,
    val live: Boolean = false,
    val shopOpen: Boolean = false,
)
```

- [ ] **Step 2: Test fakes**

`conductor/Fakes.kt` (test source set):

```kotlin
package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.*
import com.opuscube.munshi.voice.Line
import com.opuscube.munshi.voice.Listener
import com.opuscube.munshi.voice.Speaker
import kotlinx.coroutines.CompletableDeferred

class FakeSpeaker : Speaker {
    val said = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null   // set to hold the next say() until released
    var stops = 0
    override suspend fun say(line: Line) { said += line.text; gate?.let { it.await(); gate = null } }
    override fun stop() { stops++; gate?.complete(Unit) }
}

class FakeListener(var available0: Boolean = false, val replies: ArrayDeque<String?> = ArrayDeque()) : Listener {
    override val available get() = available0
    override suspend fun listen(windowMs: Long, onPartial: (String) -> Unit): String? = replies.removeFirstOrNull()
}

val SHARMA = Next(1, "udhaar_overdue", "Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?", "…", Display("Sharma ji ka", 450, "udhaar 12 din se baaki hai. Reminder bhej doon?"))
val MITHAI = Next(2, "local_demand_spike", "Mohalle mein mithai…", null, Display("Mohalle mein mithai ki maang 39% upar", 924, "ka stock…"))
val PINKY = Next(3, "udhaar_overdue", "Pinky didi ka ₹230 udhaar…", null, Display("Pinky didi ka", 230, "udhaar…"))
val SURF = Next(7, "group_buy_open", "Surf Excel 1kg ka group order…", null, Display("Surf Excel 1kg group order", 143, "bachenge…"))

class FakeApi : MunshiApi {
    val nexts = ArrayDeque<Next>()
    val calls = mutableListOf<String>()
    var confirmResult: (Long) -> ActionResult = { ActionResult("Ho gaya.") }
    var converseResult: (String) -> ActionResult = { ActionResult("Samjha.") }
    var current: Next? = null
    override suspend fun brief() = Brief(listOf(BriefItem(1, "udhaar_overdue", "Sharma ji ka udhaar", 450)), 450).also { calls += "brief" }
    override suspend fun next(scope: String, interrupt: Boolean): Next {
        calls += "next:$scope"
        current = current ?: nexts.removeFirstOrNull()
        return current ?: Next()
    }
    override suspend fun confirm(id: Long): ActionResult { calls += "confirm:$id"; current = null; return confirmResult(id) }
    override suspend fun decline(id: Long): ActionResult { calls += "decline:$id"; current = null; return ActionResult("Theek hai, rehne dete hain.") }
    override suspend fun converse(text: String): ActionResult { calls += "converse:$text"; return converseResult(text) }
    override suspend fun shelf(jpegBase64: String) = ShelfResult("Shelf dekh li").also { calls += "shelf" }
    override suspend fun ledger() = Ledger(742, emptyList()).also { calls += "ledger" }
}
```

(`FakeApi.next` mirrors the server: a proposed opportunity stays current until confirmed or declined.)

- [ ] **Step 3: Write the failing tests**

`conductor/ConductorTest.kt`:

```kotlin
package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConductorTest {
    private val api = FakeApi()
    private val speaker = FakeSpeaker()
    private val listener = FakeListener()

    private fun TestScope.conductor() = Conductor(api, speaker, listener, backgroundScope, listenWindowMs = 6000)

    @Test fun `opening the shop greets, asks the top task, and waits for an answer`() = runTest {
        api.nexts += SHARMA
        val c = conductor()
        c.openShop(); runCurrent()
        assertTrue(c.state.value.shopOpen)
        assertEquals(1L, c.state.value.ask!!.id)
        assertEquals(450, c.state.value.ask!!.display!!.amount)
        assertTrue(speaker.said.first().startsWith("Suprabhat Ramesh ji"))
        assertTrue(api.calls.none { it.startsWith("confirm") })
    }

    @Test fun `haan confirms, shows the pay QR, and moves to the next task`() = runTest {
        api.nexts += listOf(SHARMA, MITHAI)
        api.confirmResult = { ActionResult("Sharma ji ko reminder bhej diya.", payUrl = "https://x/pay/ab12", payFor = PayFor("Sharma ji", 450)) }
        val c = conductor()
        c.openShop(); runCurrent()
        c.answer(Answer.Yes); runCurrent()
        assertTrue("confirm:1" in api.calls)
        assertEquals(PayQr("https://x/pay/ab12", "ab12", "Sharma ji", 450), c.state.value.payQr)
        assertTrue("Sharma ji ko reminder bhej diya." in speaker.said)
        assertEquals(2L, c.state.value.ask!!.id)
    }

    @Test fun `an answer during speech stops the voice and acts at once`() = runTest {
        api.nexts += SHARMA
        val c = conductor()
        c.openShop(); runCurrent()
        speaker.gate = CompletableDeferred()          // hold the next line mid-speech
        c.answer(Answer.No); runCurrent()
        assertTrue("decline:1" in api.calls)
    }

    @Test fun `the pay QR stays until its ref is paid, then the stamp lands`() = runTest {
        api.nexts += SHARMA
        api.confirmResult = { ActionResult("ok", payUrl = "https://x/pay/ab12", payFor = PayFor("Sharma ji", 450)) }
        val c = conductor()
        c.openShop(); runCurrent(); c.answer(Answer.Yes); runCurrent()
        c.onEvent(SoundboxEvent.Paid("other", "Pinky didi", 230)); runCurrent()
        assertNotNull(c.state.value.payQr)
        c.onEvent(SoundboxEvent.Paid("ab12", "Sharma ji", 450)); runCurrent()
        assertNull(c.state.value.payQr)
        assertEquals(Stamp("ab12", "Sharma ji", 450), c.state.value.stamp)
    }

    @Test fun `a 409 on confirm says the task is gone and carries on`() = runTest {
        api.nexts += listOf(SHARMA, MITHAI)
        api.confirmResult = { throw ApiError(409, "not_proposed") }
        val c = conductor()
        c.openShop(); runCurrent(); c.answer(Answer.Yes); runCurrent()
        assertTrue("Yeh kaam ab baaki nahi." in speaker.said)
        assertEquals(2L, c.state.value.ask!!.id)
    }

    @Test fun `free speech goes to the agent, and a redirect to another task is asked without repeating it`() = runTest {
        api.nexts += listOf(SHARMA)
        api.converseResult = { ActionResult("Pinky didi ko bhejun?", opportunityId = 3) }
        val c = conductor()
        c.openShop(); runCurrent()
        api.current = PINKY                            // the agent proposed Pinky on the server
        c.answer(Answer.Text("pehle Pinky ko bhejo")); runCurrent()
        assertTrue("converse:pehle Pinky ko bhejo" in api.calls)
        assertEquals(3L, c.state.value.ask!!.id)
        assertTrue("Pinky didi ko bhejun?" in speaker.said)
        assertFalse(PINKY.line in speaker.said)        // the agent already asked; not repeated
    }

    @Test fun `a shelf reading interrupts the open question and the restock offer comes next`() = runTest {
        api.nexts += listOf(SHARMA)
        val c = conductor()
        c.openShop(); runCurrent()
        api.current = SURF                             // the shelf flow proposed the group order on the server
        c.onEvent(SoundboxEvent.Shelf("Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain.", next = Offer(7, SURF.line!!, display = SURF.display))); runCurrent()
        assertTrue("Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain." in speaker.said)
        assertEquals(7L, c.state.value.ask!!.id)
    }

    @Test fun `heard haan from the listener confirms without a tap`() = runTest {
        listener.available0 = true; listener.replies += "हाँ भेज दो"
        api.nexts += SHARMA
        val c = conductor()
        c.openShop(); runCurrent()
        assertTrue("confirm:1" in api.calls)
    }

    @Test fun `reset returns to the brief with nothing pending`() = runTest {
        api.nexts += SHARMA
        val c = conductor()
        c.openShop(); runCurrent()
        c.onEvent(SoundboxEvent.Reset); runCurrent()
        assertEquals(Mode.Brief, c.state.value.mode)
        assertNull(c.state.value.ask)
        assertFalse(c.state.value.shopOpen)
    }

    @Test fun `announcements show a banner that clears after 6_5 seconds`() = runTest {
        val c = conductor()
        c.onEvent(SoundboxEvent.Announce("₹120 prapt hue")); runCurrent()
        assertEquals("₹120 prapt hue", c.state.value.banner)
        advanceTimeBy(6600); runCurrent()
        assertNull(c.state.value.banner)
    }
}
```

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew testDebugUnitTest --tests '*ConductorTest*'`
Expected: FAIL — unresolved reference `Conductor`.

- [ ] **Step 5: Write the Conductor**

`conductor/Conductor.kt`:

```kotlin
package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.*
import com.opuscube.munshi.voice.IntentMatcher
import com.opuscube.munshi.voice.Line
import com.opuscube.munshi.voice.Listener
import com.opuscube.munshi.voice.Speaker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

private val OOPS = Line("Kuch gadbad hui. Dobara koshish kijiye.", "कुछ गड़बड़ हुई। दोबारा कोशिश कीजिए।")
private val GONE = Line("Yeh kaam ab baaki nahi.", "यह काम अब बाकी नहीं।")
private val GREET = Line("Suprabhat Ramesh ji! Aaj ke kaam sunaata hoon.", "सुप्रभात रमेश जी! आज के काम सुनाता हूँ।")
private val BYE_DONE = Line("Bas itna hi. Aapka din shubh ho!", "बस इतना ही। आपका दिन शुभ हो!")
private val BYE_NONE = Line("Aaj koi naya kaam nahi. Aapka din shubh ho!", "आज कोई नया काम नहीं। आपका दिन शुभ हो!")

// Port of web/Soundbox.tsx: one proposal at a time, answers accepted while speaking,
// free speech goes to the agent, a shelf reading interrupts, a pay QR stays until paid.
class Conductor(
    private val api: MunshiApi,
    private val speaker: Speaker,
    private val listener: Listener,
    private val scope: CoroutineScope,
    private val listenWindowMs: Long,
) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var pending: CompletableDeferred<Answer>? = null
    private val running = AtomicBoolean(false)
    private var bannerJob: Job? = null
    private var sessionJob: Job? = null

    fun start() { scope.launch { refreshBrief(); refreshLedger() } }

    fun openShop() { session(greet = true, scope = "brief", max = 3) }

    fun answer(a: Answer) {
        if (pending?.isActive == true && a != Answer.Skip) speaker.stop()
        pending?.complete(a)
    }

    fun mic() {
        if (!listener.available) return
        scope.launch {
            val text = listenOnce() ?: return@launch
            val matched = IntentMatcher.match(text)
            if (pending?.isActive == true) { answer(matched ?: Answer.Text(text)); return@launch }
            if (!running.compareAndSet(false, true)) return@launch
            try { converseAndFollow(text) } finally { running.set(false) }
        }
    }

    fun shelf(jpegBase64: String) {
        scope.launch {
            _state.update { it.copy(mode = Mode.Working) }
            try { handleShelf(api.shelf(jpegBase64)) }
            catch (e: ApiError) { say(if (e.status == 503 || e.status == 422) Line(e.message ?: OOPS.text) else OOPS) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { say(OOPS) }
        }
    }

    fun onEvent(e: SoundboxEvent) {
        when (e) {
            is SoundboxEvent.Announce -> { showBanner(e.text); scope.launch { speaker.say(Line(e.text, e.spoken)) } }
            is SoundboxEvent.Paid -> _state.update { s ->
                if (s.payQr?.ref != e.ref) s
                else s.copy(payQr = null, stamp = Stamp(e.ref, e.name ?: s.payQr.name.orEmpty(), e.amount ?: s.payQr.amount ?: 0))
            }
            SoundboxEvent.LedgerChanged -> scope.launch { refreshLedger() }
            SoundboxEvent.BriefCue -> openShop()
            SoundboxEvent.Nudge -> session(greet = false, scope = "all", max = 1)
            // Like the web page reloading: stop whatever is running, nothing stale left pending.
            SoundboxEvent.Reset -> scope.launch {
                sessionJob?.cancelAndJoin()
                pending = null
                speaker.stop()
                _state.value = UiState(live = _state.value.live)
                start()
            }
            is SoundboxEvent.Shelf -> scope.launch { handleShelf(ShelfResult(e.line, e.spoken, e.next)) }
        }
    }

    fun onLive(live: Boolean) {
        val was = _state.value.live
        _state.update { it.copy(live = live) }
        if (live && !was) scope.launch { refreshLedger() }
    }

    // ---------------------------------------------------------------------------------

    private fun session(greet: Boolean, scope: String, max: Int) {
        if (!running.compareAndSet(false, true)) return
        sessionJob = this.scope.launch { runSession(greet, scope, max) }
    }

    private suspend fun runSession(greet: Boolean, scope: String, max: Int) {
        _state.update { it.copy(shopOpen = true, mode = Mode.Idle) }
        try {
            if (greet) say(GREET)
            var handled = 0
            while (handled < max) {
                val n = api.next(scope)
                val id = n.id ?: break
                if (resolve(id, n.display, Line(n.line.orEmpty(), n.spoken), speak = true) == Outcome.Done) handled++
            }
            if (greet) say(if (handled > 0) BYE_DONE else BYE_NONE)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { say(OOPS) }
        finally { running.set(false); _state.update { it.copy(mode = Mode.Idle, ask = null) } }
    }

    private enum class Outcome { Done, Skipped }

    private suspend fun resolve(id: Long, display: Display?, line: Line, speak: Boolean): Outcome = coroutineScope {
        val answered = CompletableDeferred<Answer>().also { pending = it }
        _state.update { it.copy(ask = Ask(id, display, line), stamp = null, heard = null, mode = Mode.Speaking) }
        val voice = launch {
            if (speak) speaker.say(line)
            if (listener.available && answered.isActive) {
                val text = listenOnce()
                if (text != null) answered.complete(IntentMatcher.match(text) ?: Answer.Text(text))
            }
            if (answered.isActive) _state.update { it.copy(mode = Mode.Idle) }
        }
        val a = answered.await()
        voice.cancel()
        pending = null
        _state.update { it.copy(ask = null) }
        if (a == Answer.Skip) return@coroutineScope Outcome.Skipped
        _state.update { it.copy(mode = Mode.Working) }
        val result = try {
            when (a) {
                Answer.Yes -> api.confirm(id)
                Answer.No -> api.decline(id)
                is Answer.Text -> api.converse(a.text)
                Answer.Skip -> error("unreachable")
            }
        } catch (e: ApiError) {
            if (e.status == 409) { say(GONE); return@coroutineScope Outcome.Done }
            say(OOPS); return@coroutineScope Outcome.Done
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { say(OOPS); return@coroutineScope Outcome.Done }
        show(result)
        say(Line(result.line, result.spoken))
        if (a is Answer.Text && result.opportunityId != null && result.opportunityId != id && result.payUrl == null) {
            val n = api.next()
            if (n.id == result.opportunityId) resolve(n.id, n.display, Line(n.line.orEmpty(), n.spoken), speak = false)
        }
        Outcome.Done
    }

    private suspend fun converseAndFollow(text: String) {
        _state.update { it.copy(mode = Mode.Working) }
        val r = try { api.converse(text) } catch (e: CancellationException) { throw e } catch (e: Exception) { ActionResult(OOPS.text, OOPS.spoken) }
        show(r)
        say(Line(r.line, r.spoken))
        if (r.opportunityId != null && r.payUrl == null) {
            val n = api.next()
            if (n.id == r.opportunityId) resolve(n.id, n.display, Line(n.line.orEmpty(), n.spoken), speak = false)
        }
    }

    // A shelf reading skips the open question; the running session then asks the restock offer
    // (the server has already proposed it). With no session running, ask it here.
    private suspend fun handleShelf(r: ShelfResult) {
        if (pending?.isActive == true) { answer(Answer.Skip); say(Line(r.line, r.spoken)); return }
        if (!running.compareAndSet(false, true)) { say(Line(r.line, r.spoken)); return }
        try {
            say(Line(r.line, r.spoken))
            r.next?.let { resolve(it.id, it.display, Line(it.line, it.spoken), speak = true) }
        } finally { running.set(false); _state.update { it.copy(mode = Mode.Idle) } }
    }

    private suspend fun listenOnce(): String? {
        _state.update { it.copy(mode = Mode.Listening, heard = null) }
        val text = listener.listen(listenWindowMs) { partial -> _state.update { it.copy(heard = partial) } }?.trim()
        _state.update { it.copy(mode = Mode.Idle, heard = text?.takeIf { t -> t.isNotEmpty() }) }
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun show(r: ActionResult) {
        val url = r.payUrl ?: return
        _state.update { it.copy(payQr = PayQr(url, url.substringAfterLast('/'), r.payFor?.name, r.payFor?.amount)) }
    }

    private suspend fun say(line: Line) {
        _state.update { it.copy(caption = line, mode = Mode.Speaking) }
        speaker.say(line)
        _state.update { it.copy(mode = Mode.Idle) }
    }

    private fun showBanner(text: String) {
        bannerJob?.cancel()
        _state.update { it.copy(banner = text) }
        bannerJob = scope.launch { delay(6500); _state.update { it.copy(banner = null) } }
    }

    private suspend fun refreshBrief() = runCatching { api.brief() }.onSuccess { b -> _state.update { it.copy(brief = b) } }
    private suspend fun refreshLedger() = runCatching { api.ledger() }.onSuccess { l -> _state.update { it.copy(ledger = l) } }
}
```

- [ ] **Step 6: Run the tests and fix only the Conductor until they pass**

Run: `./gradlew testDebugUnitTest --tests '*ConductorTest*'`
Expected: PASS (10 tests). If a test fails, fix `Conductor.kt`, not the test, unless the test contradicts the behaviour list in the header comment (then fix the test and say why in the commit).

- [ ] **Step 7: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (23 tests).

- [ ] **Step 8: Commit**

```bash
git add android/app/src
git commit -m "feat(android): Conductor session state machine with unit tests"
```

---

### Task 6: Pairing config

**Files:**
- Create: `android/app/src/main/java/com/opuscube/munshi/pairing/Config.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/pairing/ConfigTest.kt`

**Interfaces:**
- Produces: `@Serializable data class Config(val url: String, val key: String = "")`, `fun parseConfig(raw: String): Config?`, `class ConfigStore(context: Context)` with `val config: Flow<Config?>` and `suspend fun save(c: Config)` / `suspend fun clear()`.

- [ ] **Step 1: Write the failing test**

`pairing/ConfigTest.kt`:

```kotlin
package com.opuscube.munshi.pairing

import org.junit.Assert.*
import org.junit.Test

class ConfigTest {
    @Test fun `parses the control page QR and trims the trailing slash`() {
        assertEquals(Config("https://abc.trycloudflare.com", "k9"), parseConfig("""{"url":"https://abc.trycloudflare.com/","key":"k9"}"""))
    }
    @Test fun `accepts an empty key`() {
        assertEquals(Config("http://10.0.2.2:3000", ""), parseConfig("""{"url":"http://10.0.2.2:3000","key":""}"""))
    }
    @Test fun `rejects anything that is not a Munshi pairing code`() {
        assertNull(parseConfig("hello"))
        assertNull(parseConfig("""{"url":"ftp://x"}"""))
        assertNull(parseConfig("""{"key":"k"}"""))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*ConfigTest*'`
Expected: FAIL — unresolved reference `parseConfig`.

- [ ] **Step 3: Write the config and store**

`pairing/Config.kt`:

```kotlin
package com.opuscube.munshi.pairing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opuscube.munshi.api.MunshiJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable data class Config(val url: String, val key: String = "")

// The /control page QR: {"url": "<public origin>", "key": "<demo key>"}.
fun parseConfig(raw: String): Config? = runCatching { MunshiJson.decodeFromString(Config.serializer(), raw) }
    .getOrNull()?.takeIf { it.url.startsWith("http://") || it.url.startsWith("https://") }
    ?.let { it.copy(url = it.url.trimEnd('/')) }

private val Context.store by preferencesDataStore("pairing")
private val URL = stringPreferencesKey("url")
private val KEY = stringPreferencesKey("key")

class ConfigStore(private val context: Context) {
    val config: Flow<Config?> = context.store.data.map { p -> p[URL]?.let { Config(it, p[KEY].orEmpty()) } }
    suspend fun save(c: Config) { context.store.edit { it[URL] = c.url; it[KEY] = c.key } }
    suspend fun clear() { context.store.edit { it.clear() } }
}
```

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (26 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "feat(android): pairing config parsed from the /control QR"
```

---

### Task 7: Real voice — TTS queue, speech recognition, chime

**Files:**
- Modify: `voice/Speaker.kt` (add `AndroidSpeaker`), `voice/Listener.kt` (add `AndroidListener`)
- Create: `voice/Chime.kt`

**Interfaces:**
- Consumes: `Speaker`, `Listener`, `Line`.
- Produces: `class AndroidSpeaker(context: Context) : Speaker`, `class AndroidListener(context: Context) : Listener`, `object Chime { fun payment(); fun tick() }`.

These wrap Android services that do not run under JVM unit tests; they are verified on the emulator in Step 4.

- [ ] **Step 1: AndroidSpeaker**

Append to `voice/Speaker.kt`:

```kotlin
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

// One FIFO queue (Mutex is fair): announcements wait behind a question, never cut it off.
// Devanagari goes to a hi-IN voice; Roman Hinglish to en-IN; no voice at all = captions only.
class AndroidSpeaker(context: Context) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private val queue = Mutex()
    @Volatile private var current: CompletableDeferred<Unit>? = null
    private var hindi = false
    private var english = false
    private val hiIN = Locale.forLanguageTag("hi-IN")
    private val enIN = Locale.forLanguageTag("en-IN")

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            hindi = tts.isLanguageAvailable(hiIN) >= TextToSpeech.LANG_AVAILABLE
            english = tts.isLanguageAvailable(enIN) >= TextToSpeech.LANG_AVAILABLE
            tts.setSpeechRate(0.95f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { current?.complete(Unit) }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { current?.complete(Unit) }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { current?.complete(Unit) }
            })
        }
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    override suspend fun say(line: Line) = queue.withLock {
        if (!ready.await()) return@withLock
        val (text, locale) = when {
            line.spoken != null && hindi -> line.spoken to hiIN
            english -> line.text to enIN
            hindi -> line.text to hiIN
            else -> return@withLock
        }
        tts.language = locale
        val done = CompletableDeferred<Unit>().also { current = it }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        // Some engines never report done; cap by roughly how long the line takes to say.
        withTimeoutOrNull(minOf(15_000L, 2_500L + text.length * 80L)) { done.await() }
        Unit
    }

    override fun stop() { tts.stop(); current?.complete(Unit) }
}
```

Move the new `import` lines to the top of the file with the package declaration.

- [ ] **Step 2: AndroidListener**

Append to `voice/Listener.kt` (imports to the top):

```kotlin
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidListener(private val context: Context) : Listener {
    override val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override suspend fun listen(windowMs: Long, onPartial: (String) -> Unit): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(windowMs + 3_000) {
            suspendCancellableCoroutine { cont ->
                val r = SpeechRecognizer.createSpeechRecognizer(context)
                fun finish(text: String?) { if (cont.isActive) cont.resume(text); r.destroy() }
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle) = finish(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                    override fun onPartialResults(b: Bundle) { b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
                    override fun onError(error: Int) = finish(null)
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
                })
                cont.invokeOnCancellation { r.cancel(); r.destroy() }
            }
        }
    }
}
```

- [ ] **Step 3: Chime**

`voice/Chime.kt`:

```kotlin
package com.opuscube.munshi.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// The same two-note station ident as the web Soundbox (587.33 Hz then 880 Hz), and a soft tick.
object Chime {
    private const val RATE = 44_100

    private fun tone(notes: List<Pair<Double, Double>>, lengthS: Double, gain: Double): ShortArray {
        val n = (RATE * lengthS).toInt()
        val out = ShortArray(n)
        for ((freq, start) in notes) {
            val s0 = (start * RATE).toInt()
            for (i in s0 until n) {
                val t = (i - s0).toDouble() / RATE
                val env = if (t < 0.03) t / 0.03 else exp(-(t - 0.03) * 7)
                out[i] = (out[i] + sin(2 * PI * freq * t) * env * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return out
    }

    private fun play(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.release() }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }

    fun payment() = runCatching { play(tone(listOf(587.33 to 0.0, 880.0 to 0.22), 0.9, 0.18)) }
    fun tick() = runCatching { play(tone(listOf(1200.0 to 0.0), 0.08, 0.08)) }
}
```

- [ ] **Step 4: Verify on the emulator with a throwaway harness**

Temporarily change `MainActivity.onCreate` to:

```kotlin
setContent { Text("voice test") }
val speaker = com.opuscube.munshi.voice.AndroidSpeaker(this)
lifecycleScope.launch {
    speaker.say(com.opuscube.munshi.voice.Line("Sharma ji ka ₹450 udhaar 12 din se baaki hai.", "Sharma ji का ₹450 उधार 12 दिन से बाकी है।"))
    com.opuscube.munshi.voice.Chime.payment()
}
```

(import `androidx.lifecycle.lifecycleScope` and `kotlinx.coroutines.launch`), install, run, and listen.
Expected: Hindi speech, then the two-note chime. On the emulator, if no Hindi voice is installed: Settings → System → Languages → Text-to-speech → Google → install Hindi; or confirm the en-IN fallback speaks the Roman line.
Revert `MainActivity` to the Task 1 placeholder before committing.

- [ ] **Step 5: Build and commit**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS, `BUILD SUCCESSFUL`.

```bash
git add android/app/src
git commit -m "feat(android): Hindi TTS queue, speech recognition and chime"
```

---

### Task 8: Theme, fonts and components, with screenshot tests

**Files:**
- Create: `android/app/src/main/res/font/{fraunces_regular,fraunces_semibold,fraunces_italic,inter_regular,inter_semibold}.ttf`, `android/app/src/main/res/font/OFL.txt`
- Create: `ui/theme/Type.kt`, `ui/components/{LedgerPage,RupeeFigure,Kicker,InkDock,WeekChip,ChukaDiyaStamp,QrCard,Banner}.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/ui/ComponentsScreenshotTest.kt`

**Interfaces:**
- Consumes: generated `Tokens` (plan a).
- Produces (all `@Composable`): `LedgerPage(modifier, content)`, `RupeeFigure(amount: Int, modifier, size: TextUnit = Tokens.TypeRupeeSize.sp, color)`, `Kicker(text: String, modifier, speaking: Boolean = false)`, `InkDock(modifier, content: @Composable ColumnScope.() -> Unit)`, `HaanButton(enabled, onClick, modifier, label = "✓  Haan")`, `NahiButton(enabled, onClick, modifier)`, `DockStatus(mode: Mode, heard: String?, live: Boolean)`, `WeekChip(total: Int, flash: Boolean, onClick)`, `ChukaDiyaStamp(date: String, modifier, animate: Boolean = true)`, `QrCard(url: String, name: String?, amount: Int?, modifier)`, `Banner(text: String, modifier)`; `fun rupees(n: Int): String`; `val Fraunces: FontFamily`, `val Inter: FontFamily`.

- [ ] **Step 1: Fonts**

Copy the static instances made for the deck (OFL licensed) into the app, lowercase names:

```bash
F=app/src/main/res/font; mkdir -p $F
cp ../../deck/fonts/FrauncesDeck-Regular.ttf $F/fraunces_regular.ttf
cp ../../deck/fonts/FrauncesDeck-Bold.ttf $F/fraunces_semibold.ttf
cp ../../deck/fonts/FrauncesDeck-Italic.ttf $F/fraunces_italic.ttf
cp ../../deck/fonts/InterDeck-Regular.ttf $F/inter_regular.ttf
cp ../../deck/fonts/InterDeck-Bold.ttf $F/inter_semibold.ttf
mkdir -p app/src/main/assets && cp ../../deck/fonts/OFL.txt app/src/main/assets/fonts-OFL.txt
```

(`res/font` accepts only font files, so the OFL licence goes in `assets/`. `../../deck/fonts` is the deck folder next to the repo, `C:\Project\munshi\deck\fonts`.)

- [ ] **Step 2: Type**

`ui/theme/Type.kt`:

```kotlin
package com.opuscube.munshi.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.opuscube.munshi.R
import java.text.NumberFormat
import java.util.Locale

val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

private val indian = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"))
fun rupees(n: Int): String = "₹" + indian.format(n)
```

- [ ] **Step 3: Components**

`ui/components/LedgerPage.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.opuscube.munshi.ui.theme.Tokens

// Ivory paper with faint ruled lines and the red margin line, as in the deck.
@Composable
fun LedgerPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(Tokens.Paper).drawBehind {
            val step = Tokens.SpaceRuleStep.dp.toPx()
            val hair = 1.dp.toPx()
            var y = step
            while (y < size.height) { drawLine(Tokens.Rule, Offset(0f, y), Offset(size.width, y), hair); y += step }
            val x = Tokens.SpaceMarginLineX.dp.toPx()
            drawLine(Tokens.MarginLine, Offset(x, 0f), Offset(x, size.height), hair)
        },
        content = content,
    )
}
```

`ui/components/RupeeFigure.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

// The price-in-a-fine-menu figure: small raised ₹, large tight numerals.
@Composable
fun RupeeFigure(amount: Int, modifier: Modifier = Modifier, size: TextUnit = Tokens.TypeRupeeSize.sp, color: Color = Tokens.Ink) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = size * 0.48f, baselineShift = BaselineShift(0.62f), fontWeight = FontWeight.Normal)) { append("₹") }
            append(rupees(amount).removePrefix("₹"))
        },
        modifier = modifier,
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = size,
        letterSpacing = Tokens.TypeRupeeTracking.em, lineHeight = size * 0.95f, color = color,
    )
}
```

`ui/components/Kicker.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, speaking: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (speaking) SpeakingBars()
        Text(text.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = Tokens.TypeKickerSize.sp,
            letterSpacing = Tokens.TypeKickerTracking.em, color = Tokens.Red)
    }
}

@Composable
private fun SpeakingBars() {
    val t = rememberInfiniteTransition(label = "bars")
    Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val h by t.animateFloat(3f, 10f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(2.dp).height(h.dp).background(Tokens.Red, RoundedCornerShape(1.dp)))
        }
    }
}
```

`ui/components/InkDock.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.conductor.Mode
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

@Composable
fun InkDock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Tokens.RadiusDock.dp)
    Column(
        modifier.shadow(18.dp, shape).background(Tokens.Ink, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun HaanButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "✓  Haan") {
    val shape = RoundedCornerShape(Tokens.RadiusButton.dp)
    Box(
        modifier.height(54.dp).alpha(if (enabled) 1f else 0.4f)
            .background(Brush.verticalGradient(listOf(Tokens.RedTop, Tokens.RedBottom)), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = Tokens.TypeButtonSize.sp, color = Tokens.Ivory) }
}

@Composable
fun NahiButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Tokens.RadiusButton.dp)
    Box(
        modifier.height(54.dp).alpha(if (enabled) 1f else 0.4f).border(BorderStroke(1.dp, Tokens.DockLine), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("Nahi", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Tokens.Ivory) }
}

@Composable
fun DockStatus(mode: Mode, heard: String?, live: Boolean) {
    val text = when {
        !live -> "Jud raha hai…"
        mode == Mode.Listening -> heard?.let { "“$it”" } ?: "Sun raha hoon…"
        mode == Mode.Working -> "Kar raha hoon…"
        heard != null -> "Aapne kaha: “$heard”"
        else -> null
    } ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (mode == Mode.Listening && live) { Wave(); Spacer(Modifier.width(8.dp)) }
        if (!live) { Box(Modifier.size(6.dp).background(Tokens.RedOnInk, RoundedCornerShape(3.dp))); Spacer(Modifier.width(6.dp)) }
        Text(text, fontFamily = Inter, fontSize = 11.sp, color = Tokens.Ink3, maxLines = 1)
    }
}

@Composable
private fun Wave() {
    val t = rememberInfiniteTransition(label = "wave")
    Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { i ->
            val h by t.animateFloat(3f, 14f, infiniteRepeatable(tween(550, delayMillis = (i % 3) * 140), RepeatMode.Reverse), label = "w$i")
            Box(Modifier.width(2.dp).height(h.dp).background(Tokens.RedOnInk, RoundedCornerShape(1.dp)))
        }
    }
}
```

`ui/components/WeekChip.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

@Composable
fun WeekChip(total: Int, flash: Boolean, onClick: () -> Unit) {
    val shown by animateIntAsState(total, tween(900), label = "week")
    val ink by animateColorAsState(if (flash) Tokens.Red else Tokens.Ink, label = "flash")
    Text(
        buildAnnotatedString { append("Is hafte "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) { append(rupees(shown)) } },
        Modifier.border(BorderStroke(1.dp, Tokens.Rule), RoundedCornerShape(Tokens.RadiusPill.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        fontFamily = Inter, fontSize = Tokens.TypeChipSize.sp, color = Tokens.Ink2,
    )
}
```

`ui/components/ChukaDiyaStamp.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

// A round red ledger stamp that drops onto the page when a payment lands.
@Composable
fun ChukaDiyaStamp(date: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val s = remember { Animatable(if (animate) 1.6f else 1f) }
    LaunchedEffect(Unit) { if (animate) s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Box(
        modifier.size(88.dp).scale(s.value).rotate(-12f).alpha(0.92f)
            .border(2.5.dp, Tokens.Red, CircleShape).padding(5.dp).border(1.5.dp, Tokens.Red, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CHUKA", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.14.em, color = Tokens.Red)
            Text("diya", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 17.sp, color = Tokens.Red)
            Text(date.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.14.em, color = Tokens.Red)
        }
    }
}
```

`ui/components/QrCard.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

// Drawn on the device from the pay link, so it works even when WhatsApp or the network does not.
fun qrBitmap(text: String, px: Int = 360): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val ink = Tokens.Ink.toArgb(); val paper = android.graphics.Color.WHITE
    return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until px) for (y in 0 until px) setPixel(x, y, if (m[x, y]) ink else paper)
    }
}

@Composable
fun QrCard(url: String, name: String?, amount: Int?, modifier: Modifier = Modifier) {
    val bmp = remember(url) { qrBitmap(url) }
    Row(
        modifier.background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(Tokens.RadiusCard.dp))
            .border(BorderStroke(1.dp, Tokens.Rule), RoundedCornerShape(Tokens.RadiusCard.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(bmp.asImageBitmap(), contentDescription = "${name ?: "Grahak"} ke liye bhugtaan QR", Modifier.size(96.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (name != null) "$name, scan karke chukayein" else "Scan karke chukayein",
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Tokens.Ink)
            if (amount != null) RupeeFigure(amount, size = 30.sp)
        }
    }
}
```

`ui/components/Banner.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

@Composable
fun Banner(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().background(Tokens.Ink, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ivory,
    )
}
```

- [ ] **Step 4: Screenshot test (records the golden images)**

`ui/ComponentsScreenshotTest.kt`:

```kotlin
package com.opuscube.munshi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.opuscube.munshi.conductor.Mode
import com.opuscube.munshi.ui.components.*
import org.junit.Rule
import org.junit.Test

class ComponentsScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test fun parts() = paparazzi.snapshot {
        LedgerPage {
            Column(Modifier.padding(start = 32.dp, top = 40.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                WeekChip(total = 742, flash = false, onClick = {})
                Kicker("Munshi bol raha hai", speaking = false)
                RupeeFigure(450)
                ChukaDiyaStamp("25 Oct", animate = false)
                QrCard("https://munshi.test/pay/ab12", "Sharma ji", 450)
                Banner("₹120 prapt hue")
                InkDock {
                    DockStatus(Mode.Working, heard = null, live = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HaanButton(true, {}, Modifier.weight(1.6f)); NahiButton(true, {}, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
```

Run: `./gradlew recordPaparazziDebug`
Expected: `BUILD SUCCESSFUL`; a PNG under `app/src/test/snapshots/images/`. Open it and compare with `docs/design/soundbox-app-mockup.html`: ivory ruled page with the red margin line, serif ₹450 with the small raised ₹, the stamp, the QR card, the ink dock with the red Haan pill. Fix spacing or sizes in the components until it matches, re-recording each time.

If the Paparazzi plugin fails to apply with this AGP/compileSdk, check its releases page (github.com/cashapp/paparazzi/releases) for the newest version, set it in `libs.versions.toml`, and retry.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew testDebugUnitTest verifyPaparazziDebug`
Expected: PASS.

```bash
git add android/app/src
git commit -m "feat(android): ledger-page theme, fonts and components with screenshot test"
```

---

### Task 9: Main screen (phone and tablet), with screenshot tests per state

**Files:**
- Create: `ui/screens/MainContent.kt`, `ui/components/HisaabSheet.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/ui/MainContentScreenshotTest.kt`

**Interfaces:**
- Consumes: `UiState`, `Mode`, components (Task 8).
- Produces: `@Composable fun MainContent(state: UiState, actions: MainActions, today: String, modifier: Modifier = Modifier)`; `data class MainActions(val openShop: () -> Unit, val yes: () -> Unit, val no: () -> Unit, val mic: () -> Unit, val camera: () -> Unit, val week: () -> Unit, val repair: () -> Unit)`; `@Composable fun HisaabSheet(ledger: Ledger, onDismiss: () -> Unit)`.

- [ ] **Step 1: Write the screen**

`ui/screens/MainContent.kt`:

```kotlin
package com.opuscube.munshi.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.conductor.Mode
import com.opuscube.munshi.conductor.UiState
import com.opuscube.munshi.ui.components.*
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

data class MainActions(
    val openShop: () -> Unit, val yes: () -> Unit, val no: () -> Unit,
    val mic: () -> Unit, val camera: () -> Unit, val week: () -> Unit, val repair: () -> Unit,
)

@Composable
fun MainContent(state: UiState, actions: MainActions, today: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                LedgerPage(Modifier.weight(1.45f)) { Page(state, actions, today) }
                InkDock(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) { Spacer(Modifier.weight(1f)); Dock(state, actions) }
            }
        } else {
            LedgerPage {
                Page(state, actions, today, bottomPad = 190.dp)
                InkDock(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Tokens.SpaceDockInset.dp)) { Dock(state, actions) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Page(s: UiState, a: MainActions, today: String, bottomPad: androidx.compose.ui.unit.Dp = 24.dp) {
    Column(Modifier.fillMaxSize().padding(start = Tokens.SpacePageXWithMargin.dp, end = Tokens.SpacePageX.dp, top = 20.dp, bottom = bottomPad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Munshi", Modifier.combinedClickable(onClick = {}, onLongClick = a.repair), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
            Text(" •", fontSize = 15.sp, color = Tokens.Red)
            Spacer(Modifier.weight(1f))
            s.ledger?.let { WeekChip(it.total, flash = s.stamp != null, onClick = a.week) }
        }
        s.banner?.let { Spacer(Modifier.height(10.dp)); Banner(it) }
        Spacer(Modifier.height(30.dp))
        when {
            s.ask != null -> AskBlock(s)
            s.stamp != null -> PaidBlock(s, today)
            !s.shopOpen -> BriefBlock(s)
            else -> s.caption?.let { Text(it.text, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = Tokens.TypeLineSize.sp, lineHeight = (Tokens.TypeLineSize * Tokens.TypeLineHeight).sp, color = Tokens.Ink) }
        }
        s.payQr?.let { Spacer(Modifier.height(18.dp)); QrCard(it.url, it.name, it.amount) }
    }
}

@Composable
private fun AskBlock(s: UiState) {
    val ask = s.ask ?: return
    Kicker("Munshi bol raha hai", speaking = s.mode == Mode.Speaking)
    val d = ask.display
    if (d != null) {
        Spacer(Modifier.height(20.dp))
        Text(d.who, fontFamily = Fraunces, fontSize = Tokens.TypeWhoSize.sp, color = Tokens.Ink2)
        RupeeFigure(d.amount)
        Spacer(Modifier.height(10.dp))
        Text(d.rest, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = Tokens.TypeLineSize.sp, lineHeight = (Tokens.TypeLineSize * Tokens.TypeLineHeight).sp, color = Tokens.Ink)
    } else {
        Spacer(Modifier.height(16.dp))
        Text(ask.line.text, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 30.sp, color = Tokens.Ink)
    }
}

@Composable
private fun PaidBlock(s: UiState, today: String) {
    val st = s.stamp ?: return
    Box(Modifier.fillMaxWidth()) {
        Column {
            Kicker("Payment aaya")
            Spacer(Modifier.height(20.dp))
            Text("${st.name} ne", fontFamily = Fraunces, fontSize = Tokens.TypeWhoSize.sp, color = Tokens.Ink2)
            RupeeFigure(st.amount)
            Spacer(Modifier.height(10.dp))
            Text("chuka diye. Hisaab barabar.", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = Tokens.TypeLineSize.sp, color = Tokens.Ink)
        }
        ChukaDiyaStamp(today, Modifier.align(Alignment.TopEnd).padding(top = 40.dp))
    }
}

@Composable
private fun BriefBlock(s: UiState) {
    Kicker("Subah ka hisaab")
    Spacer(Modifier.height(16.dp))
    Text("Shubh prabhat,\nRamesh ji.", fontFamily = Fraunces, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em, color = Tokens.Ink)
    val b = s.brief ?: return
    Spacer(Modifier.height(8.dp))
    Text("Aaj ${b.items.size} kaam hain, kul", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = Tokens.Ink)
    RupeeFigure(b.total, size = 52.sp)
    Spacer(Modifier.height(12.dp))
    b.items.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Bottom) {
            Text(item.title, Modifier.weight(1f), fontFamily = Inter, fontSize = 12.sp, color = Tokens.Ink)
            Text(rupees(item.amount), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
        }
    }
}

@Composable
private fun ColumnScope.Dock(s: UiState, a: MainActions) {
    DockStatus(s.mode, s.heard, s.live)
    if (!s.shopOpen && s.ask == null) {
        HaanButton(enabled = s.live, onClick = a.openShop, modifier = Modifier.fillMaxWidth(), label = "Dukaan kholo")
    } else {
        val canAnswer = s.ask != null && s.live
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HaanButton(canAnswer, a.yes, Modifier.weight(1.6f))
            NahiButton(canAnswer, a.no, Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("◉  Boliye", Modifier.clickable(onClick = a.mic).padding(horizontal = 14.dp, vertical = 6.dp),
            fontFamily = Inter, fontSize = 10.sp, color = Tokens.Ink3)
        Text("▣  Shelf photo", Modifier.clickable(onClick = a.camera).padding(horizontal = 14.dp, vertical = 6.dp),
            fontFamily = Inter, fontSize = 10.sp, color = Tokens.Ink3)
    }
}
```

(add `import androidx.compose.foundation.clickable` to the file's imports.)

- [ ] **Step 2: Hisaab sheet**

`ui/components/HisaabSheet.kt`:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.api.Ledger
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisaabSheet(ledger: Ledger, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tokens.Paper) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Kicker("Is hafte Munshi ne aapko dilaye")
            RupeeFigure(ledger.total, size = 56.sp)
            Text("Har rupaya us kaam se juda hai jisse woh aaya.", fontFamily = Inter, fontSize = 12.sp, color = Tokens.Ink2)
            Spacer(Modifier.height(16.dp))
            ledger.items.forEach { i ->
                HorizontalDivider(color = Tokens.Rule)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(i.label, Modifier.weight(1f), fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink)
                    Text("+" + rupees(i.amount), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
                }
            }
            ledger.loan?.let { l ->
                Spacer(Modifier.height(20.dp))
                Kicker("Loan · kamaai mein nahi gina")
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Roz ${rupees(l.dailyDeduction)} settlement se · kul kharcha ${rupees(l.totalCost)}", Modifier.weight(1f), fontFamily = Inter, fontSize = 12.sp, color = Tokens.Ink2)
                    Text(rupees(l.principal), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Screenshot every state, phone and tablet**

`ui/MainContentScreenshotTest.kt`:

```kotlin
package com.opuscube.munshi.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.opuscube.munshi.api.*
import com.opuscube.munshi.conductor.*
import com.opuscube.munshi.ui.screens.MainActions
import com.opuscube.munshi.ui.screens.MainContent
import com.opuscube.munshi.voice.Line
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MainContentScreenshotTest(private val device: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun devices() = listOf("phone", "tablet")
    }

    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = if (device == "phone") DeviceConfig.PIXEL_5 else DeviceConfig.NEXUS_10.copy(orientation = ScreenOrientation.LANDSCAPE),
    )

    private val noop = MainActions({}, {}, {}, {}, {}, {}, {})
    private val ledger = Ledger(742, listOf(LedgerItem("udhaar_recovered", 230, "Pinky didi ka udhaar wapas", "", "send_udhaar_reminder")))
    private val brief = Brief(listOf(BriefItem(1, "udhaar_overdue", "Sharma ji ka udhaar", 450), BriefItem(2, "local_demand_spike", "Mithai ki maang upar", 246), BriefItem(3, "udhaar_overdue", "Pinky didi ka udhaar", 230)), 926)
    private val sharma = Ask(1, Display("Sharma ji ka", 450, "udhaar 12 din se baaki hai. Reminder bhej doon?"), Line("…"))

    private fun shot(s: UiState) = paparazzi.snapshot { MainContent(s, noop, today = "25 Oct") }

    @Test fun brief() = shot(UiState(mode = Mode.Brief, brief = brief, ledger = ledger, live = true))
    @Test fun speaking() = shot(UiState(mode = Mode.Speaking, ask = sharma, ledger = ledger, live = true, shopOpen = true))
    @Test fun listening() = shot(UiState(mode = Mode.Listening, ask = sharma, heard = "haan bhej", ledger = ledger, live = true, shopOpen = true))
    @Test fun payQr() = shot(UiState(mode = Mode.Idle, ask = sharma.copy(id = 2), payQr = PayQr("https://m.test/pay/ab12", "ab12", "Sharma ji", 450), ledger = ledger, live = true, shopOpen = true))
    @Test fun paid() = shot(UiState(mode = Mode.Idle, stamp = Stamp("ab12", "Sharma ji", 450), ledger = ledger.copy(total = 1192), live = true, shopOpen = true))
    @Test fun reconnecting() = shot(UiState(mode = Mode.Idle, ask = sharma, ledger = ledger, live = false, shopOpen = true))
    @Test fun banner() = shot(UiState(mode = Mode.Idle, banner = "₹120 prapt hue", caption = Line("Bas itna hi. Aapka din shubh ho!"), ledger = ledger, live = true, shopOpen = true))
}
```

Run: `./gradlew recordPaparazziDebug`
Expected: 14 images (7 states × phone/tablet). Review each against the mockup and §4 of the spec: brief list with total; ₹ figure and italic line while asking; waveform + "haan bhej" while listening; QR card under the question; stamp at top right after payment; "Jud raha hai…" with disabled buttons when offline; ink banner. Fix layout until every image is right, re-recording.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew testDebugUnitTest verifyPaparazziDebug`
Expected: PASS.

```bash
git add android/app/src
git commit -m "feat(android): main screen states for phone and tablet with screenshots"
```

---

### Task 10: Shelf photo

**Files:**
- Create: `camera/ShelfPhoto.kt`, `android/app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider)
- Test: `android/app/src/test/java/com/opuscube/munshi/camera/ShelfPhotoTest.kt`

**Interfaces:**
- Produces: `fun scaledSize(w: Int, h: Int, max: Int = 1280): Pair<Int, Int>`, `fun jpegBase64(context: Context, uri: Uri): String` (decode, scale so the longest side is ≤ 1280, JPEG quality 82, Base64 NO_WRAP), `fun newPhotoUri(context: Context): Uri`.

- [ ] **Step 1: Failing test for the pure sizing rule**

`camera/ShelfPhotoTest.kt`:

```kotlin
package com.opuscube.munshi.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfPhotoTest {
    @Test fun `longest side is capped at 1280 and aspect is kept`() {
        assertEquals(1280 to 960, scaledSize(4000, 3000))
        assertEquals(720 to 1280, scaledSize(2250, 4000))
        assertEquals(800 to 600, scaledSize(800, 600))
    }
}
```

Run: `./gradlew testDebugUnitTest --tests '*ShelfPhotoTest*'` — Expected: FAIL (unresolved `scaledSize`).

- [ ] **Step 2: Implementation**

`camera/ShelfPhoto.kt`:

```kotlin
package com.opuscube.munshi.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

// Phone photos are large; a 1280 px JPEG is plenty to read a shelf and uploads fast.
fun scaledSize(w: Int, h: Int, max: Int = 1280): Pair<Int, Int> {
    val s = minOf(1.0, max.toDouble() / max(w, h))
    return (w * s).roundToInt() to (h * s).roundToInt()
}

fun newPhotoUri(context: Context): Uri {
    val f = File(context.cacheDir, "shelf/shelf.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
}

fun jpegBase64(context: Context, uri: Uri): String {
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val (w, h) = scaledSize(info.size.width, info.size.height); d.setTargetSize(w, h)
        }
    } else {
        val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val (w, h) = scaledSize(raw.width, raw.height); Bitmap.createScaledBitmap(raw, w, h, true)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
```

`res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths><cache-path name="shelf" path="shelf/" /></paths>
```

Add inside `<application>` in the manifest:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
        </provider>
```

(The camera itself is the system camera app via `ActivityResultContracts.TakePicture`, wired in Task 11; it is full-screen and needs no camera code here. Gallery fallback uses `PickVisualMedia`.)

- [ ] **Step 3: Verify and commit**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

```bash
git add android/app/src
git commit -m "feat(android): shelf photo scaling and file provider"
```

---

### Task 11: Wire it together — app graph, activity, pairing screen, service

**Files:**
- Modify: `MunshiApp.kt`, `MainActivity.kt`
- Create: `ui/screens/MainScreen.kt`, `ui/screens/PairScreen.kt`, `service/MunshiService.kt`
- Modify: `AndroidManifest.xml` (service)

**Interfaces:**
- Consumes: everything above.
- Produces: `class Graph(config: Config, app: Application)` exposing `conductor: Conductor`, `events: EventStream`; `MunshiApp.graph: StateFlow<Graph?>`.

- [ ] **Step 1: Application graph**

`MunshiApp.kt`:

```kotlin
package com.opuscube.munshi

import android.app.Application
import com.opuscube.munshi.api.HttpMunshiApi
import com.opuscube.munshi.conductor.Conductor
import com.opuscube.munshi.events.EventStream
import com.opuscube.munshi.pairing.Config
import com.opuscube.munshi.pairing.ConfigStore
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.voice.AndroidListener
import com.opuscube.munshi.voice.AndroidSpeaker
import com.opuscube.munshi.voice.Chime
import com.opuscube.munshi.api.SoundboxEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Graph(val config: Config, app: Application, scope: CoroutineScope) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val sse = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    val events = EventStream(config.url, config.key, sse)
    val conductor = Conductor(HttpMunshiApi(config.url, config.key, http), AndroidSpeaker(app), AndroidListener(app), scope, Tokens.MotionListenWindowMs)
    private val job = scope.launch {
        launch { events.live.collect(conductor::onLive) }
        // Soft tick each time Munshi starts listening (spec §5).
        launch { conductor.state.map { it.mode == com.opuscube.munshi.conductor.Mode.Listening }.distinctUntilChanged().filter { it }.collect { Chime.tick() } }
        events.events().collect { e -> if (e is SoundboxEvent.Paid) Chime.payment(); conductor.onEvent(e) }
    }
    init { conductor.start() }
    fun close() = job.cancel()
}

class MunshiApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var configs: ConfigStore
    val graph: StateFlow<Graph?> by lazy {
        configs.config.distinctUntilChanged()
            .runningFold<Config?, Graph?>(null) { old, c -> old?.close(); c?.let { Graph(it, this, scope) } }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }
    override fun onCreate() { super.onCreate(); configs = ConfigStore(this) }
}
```

- [ ] **Step 2: Pairing screen**

`ui/screens/PairScreen.kt`:

```kotlin
package com.opuscube.munshi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.opuscube.munshi.pairing.Config
import com.opuscube.munshi.pairing.parseConfig
import com.opuscube.munshi.ui.components.HaanButton
import com.opuscube.munshi.ui.components.Kicker
import com.opuscube.munshi.ui.components.LedgerPage
import com.opuscube.munshi.ui.components.InkDock
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

@Composable
fun PairScreen(onPaired: (Config) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }
    LedgerPage {
        Column(Modifier.padding(start = 32.dp, end = 20.dp, top = 60.dp)) {
            Kicker("Pehli baar")
            Spacer(Modifier.height(16.dp))
            Text("Jodiye", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, color = Tokens.Ink)
            Text("Laptop par /control kholiye aur wahan ka QR scan kijiye.", fontFamily = Inter, fontSize = 14.sp, color = Tokens.Ink2)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(manual, { manual = it }, label = { Text("Ya server ka pata (http://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Tokens.Red, fontSize = 13.sp) }
        }
        InkDock(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().padding(10.dp)) {
            HaanButton(true, {
                if (manual.isNotBlank()) {
                    parseConfig("""{"url":"${manual.trim()}","key":""}""")?.let(onPaired) ?: run { error = "Yeh pata sahi nahi lag raha." }
                } else {
                    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                    GmsBarcodeScanning.getClient(context, opts).startScan()
                        .addOnSuccessListener { b -> parseConfig(b.rawValue.orEmpty())?.let(onPaired) ?: run { error = "Yeh Munshi ka QR nahi hai." } }
                        .addOnFailureListener { error = "Scan nahi hua. Dobara koshish kijiye." }
                }
            }, Modifier.fillMaxWidth(), label = if (manual.isBlank()) "QR scan kijiye" else "Jodiye")
        }
    }
}
```

- [ ] **Step 3: Main screen (stateful wrapper)**

`ui/screens/MainScreen.kt`:

```kotlin
package com.opuscube.munshi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opuscube.munshi.camera.jpegBase64
import com.opuscube.munshi.camera.newPhotoUri
import com.opuscube.munshi.conductor.Answer
import com.opuscube.munshi.conductor.Conductor
import com.opuscube.munshi.ui.components.HisaabSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MainScreen(conductor: Conductor, onRepair: () -> Unit) {
    val state by conductor.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showHisaab by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { conductor.shelf(jpegBase64(context, it)) }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) conductor.shelf(jpegBase64(context, u))
        else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) }
    MainContent(state, MainActions(
        openShop = { conductor.openShop() },
        yes = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); conductor.answer(Answer.Yes) },
        no = { conductor.answer(Answer.No) },
        mic = { conductor.mic() },
        camera = { newPhotoUri(context).also { photoUri = it }.let(take::launch) },
        week = { showHisaab = true },
        repair = onRepair,
    ), today)

    val ledger = state.ledger
    if (showHisaab && ledger != null) HisaabSheet(ledger) { showHisaab = false }
}
```

- [ ] **Step 4: Foreground service**

`service/MunshiService.kt`:

```kotlin
package com.opuscube.munshi.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

// Keeps the process (and so the event stream and announcements in MunshiApp) alive
// while the app is not in front. The work itself lives in the Application graph.
class MunshiService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("counter", "Munshi dukaan par", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "counter")
            .setContentTitle("Munshi dukaan par hai")
            .setContentText("Payment aate hi bataunga.")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        return START_STICKY
    }

    companion object {
        fun start(context: Context) = context.startForegroundService(Intent(context, MunshiService::class.java))
    }
}
```

Add inside `<application>` in the manifest:

```xml
        <service android:name=".service.MunshiService" android:exported="false" android:foregroundServiceType="dataSync" />
```

- [ ] **Step 5: Activity**

`MainActivity.kt`:

```kotlin
package com.opuscube.munshi

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opuscube.munshi.service.MunshiService
import com.opuscube.munshi.ui.screens.MainScreen
import com.opuscube.munshi.ui.screens.PairScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // counter mode
        val app = application as MunshiApp
        permissions.launch(buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
        MunshiService.start(this)
        setContent {
            val scope = rememberCoroutineScope()
            val graph by app.graph.collectAsStateWithLifecycle()
            val g = graph
            if (g == null) PairScreen { c -> scope.launch { app.configs.save(c) } }
            else MainScreen(g.conductor) { scope.launch { app.configs.clear() } }
        }
    }
}
```

- [ ] **Step 6: Build and run end to end on the emulator**

```bash
./gradlew testDebugUnitTest verifyPaparazziDebug assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk && "$ADB" shell am start -n com.opuscube.munshi/.MainActivity
```

With the server running on the laptop (`npm run server` in the repo root), on the Pair screen type `http://10.0.2.2:3000` and tap Jodiye.
Expected, checked with `"$ADB" exec-out screencap -p > step.png` after each action:
1. Brief state: "Shubh prabhat, Ramesh ji", three tasks with ₹, total.
2. Tap Dukaan kholo: greeting spoken; Sharma ji ₹450 question appears; speaking bars, then waveform.
3. Tap Haan: QR card appears; "Sharma ji ko reminder bhej diya" spoken.
4. Open `http://localhost:3000/pay/<ref>` on the laptop (ref from the QR's link text in `adb logcat` or `/ledger`) and pay: chime, spoken announcement, stamp drops, week chip ticks.
5. `/control` → Shelf photo: shelf line spoken, Surf group order asked.
6. `/control` → Reset: back to brief.
7. Stop the server: dock shows "Jud raha hai…", buttons disabled; start it again: live returns.

- [ ] **Step 7: Commit**

```bash
git add android/
git commit -m "feat(android): wire graph, pairing, main screen, camera and counter service"
```

---

### Task 12: CI for the Android app

**Files:**
- Modify: `.github/workflows/ci.yml` (new `android` job)

- [ ] **Step 1: Add the job**

Append under `jobs:`:

```yaml
  android:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest verifyPaparazziDebug assembleDebug
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: munshi-debug-apk
          path: android/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Make the wrapper executable in git and commit**

```bash
git update-index --chmod=+x android/gradlew
git add .github/workflows/ci.yml android/gradlew
git commit -m "ci: build, unit-test and screenshot-test the Android app"
```

- [ ] **Step 3: Verify**

After pushing the branch (only with the user's go-ahead), the `android` job is green and the APK artifact downloads.
