import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun buildSetting(name: String, defaultValue: String = ""): String =
    providers.environmentVariable(name).orElse(providers.gradleProperty(name))
        .getOrElse(localProperties.getProperty(name, defaultValue))

fun String.asJavaString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

val statsUrl = buildSetting("OFFICETV_STATS_URL").trim()
if (statsUrl.isNotEmpty()) {
    val uri = runCatching { URI(statsUrl) }.getOrNull()
    require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrEmpty()) {
        "OFFICETV_STATS_URL must be a full HTTPS URL, or empty to disable statistics."
    }
    require(uri?.rawUserInfo == null) {
        "OFFICETV_STATS_URL must not contain userinfo (credentials)."
    }
}
val githubRepo = buildSetting("OFFICETV_GITHUB_REPO", "droidrun/mobilerun")
require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(githubRepo)) {
    "OFFICETV_GITHUB_REPO must be owner/name"
}

val demo = buildSetting("OFFICETV_DEMO", "false")
require(demo == "true" || demo == "false") { "OFFICETV_DEMO must be true or false" }

android {
    namespace = "ai.droidrun.officetv"
    compileSdk = 34
    defaultConfig {
        applicationId = "ai.droidrun.officetv"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "OFFICETV_DEMO", demo)
        buildConfigField("String", "OFFICETV_STATS_URL", statsUrl.asJavaString())
        buildConfigField("String", "OFFICETV_GITHUB_REPO", githubRepo.asJavaString())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes { debug { isDebuggable = false } }
    lint { abortOnError = true }
}
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
