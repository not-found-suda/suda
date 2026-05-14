import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import java.io.File
import java.util.Properties

// 모노레포 루트 .env 파일에서 환경변수 로드
val envFile = rootDir.parentFile.resolve(".env")
val envProps =
    Properties().also { props ->
        if (envFile.exists()) props.load(envFile.inputStream())
    }
val mobileBaseUrl: String =
    envProps.getProperty("MOBILE_API_BASE_URL", "http://10.0.2.2:8080/api/")
val naverClientId: String = envProps.getProperty("NAVER_CLIENT_ID", "")
val naverClientSecret: String = envProps.getProperty("NAVER_CLIENT_SECRET", "")
val naverClientName: String = envProps.getProperty("NAVER_CLIENT_NAME", "")
val defaultSllmModelDownloadUrl =
    "https://d14zyw67949hvk.cloudfront.net/models/sllm/qwen2.5-1.5b/qwen2.5-1.5b.litertlm"
val sllmModelDownloadUrl: String =
    envProps.getProperty("SLLM_MODEL_DOWNLOAD_URL", defaultSllmModelDownloadUrl)
val localPropsFile = rootDir.resolve("local.properties")
val localProps =
    Properties().also { props ->
        if (localPropsFile.exists()) props.load(localPropsFile.inputStream())
    }
val qwenModelFileName =
    "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
val qwenDebugModelLocalPath: String =
    localProps.getProperty(
        "QWEN_MODEL_LOCAL_PATH",
        rootDir.resolve("local-models/$qwenModelFileName").absolutePath,
    )
val mobileApplicationId = "com.ssafy.mobile"
val debugQwenAssetsDir =
    layout
        .buildDirectory
        .file("generated/debugQwenAssets")
        .get()
        .asFile
val qwenDebugModelFile = File(qwenDebugModelLocalPath)
val qwenDebugModelAssetsDir = debugQwenAssetsDir.resolve("models")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ssafy.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = mobileApplicationId
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL", "\"$mobileBaseUrl\"")
        buildConfigField("String", "NAVER_CLIENT_ID", "\"$naverClientId\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"$naverClientSecret\"")
        buildConfigField("String", "NAVER_CLIENT_NAME", "\"$naverClientName\"")
        buildConfigField(
            "String",
            "SLLM_MODEL_DOWNLOAD_URL",
            "\"${sllmModelDownloadUrl.escapeBuildConfig()}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    lint {
        // local.properties path escaping can fail differently by host OS.
        disable += "PropertyEscape"
    }
    packaging {
        jniLibs {
            pickFirsts +=
                setOf(
                    "lib/arm64-v8a/libLiteRt.so",
                    "lib/arm64-v8a/libLiteRtClGlAccelerator.so",
                    "lib/x86_64/libLiteRt.so",
                    "lib/x86_64/libLiteRtClGlAccelerator.so",
                )
        }
    }
    sourceSets {
        getByName("debug") {
            assets.srcDir(debugQwenAssetsDir)
        }
    }
}

fun String.escapeBuildConfig(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

val copyDebugQwenModelAssetName = "copyDebugQwenModelAsset"
if (qwenDebugModelFile.isFile) {
    tasks.register<Copy>(copyDebugQwenModelAssetName) {
        group = "build"
        description =
            "Copies the local Qwen model into generated debug assets when the model file exists."
        from(qwenDebugModelFile)
        into(qwenDebugModelAssetsDir)
        rename(qwenDebugModelFile.name, qwenModelFileName)
    }
} else {
    tasks.register(copyDebugQwenModelAssetName) {
        group = "build"
        description = "No-op because no local Qwen model file exists for debug assets."
    }
}

tasks.matching { task -> task.name == "mergeDebugAssets" }.configureEach {
    dependsOn(copyDebugQwenModelAssetName)
}

tasks
    .matching { task ->
        task.name == "generateDebugLintReportModel" ||
            task.name == "lintAnalyzeDebug"
    }.configureEach {
        dependsOn(copyDebugQwenModelAssetName)
    }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(libs.protobuf.java)
    implementation(libs.litertlm.android)
    implementation(libs.tensorflow.lite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.naver.oauth)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.hilt.android)
    implementation(libs.coil.compose)
    ksp(libs.hilt.compiler)
    implementation("com.google.mediapipe:tasks-genai:0.10.27") {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
}
