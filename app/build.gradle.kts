import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ── release 签名配置 ───────────────────────────────────────────────
// 密钥库信息放根目录 keystore.properties（已加进 .gitignore，不入库）。
// 文件缺失或密钥库不存在时 release 打包会直接报错，避免又产出装不上的未签名 APK。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val releaseKeystoreFile = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseKeystore = releaseKeystoreFile?.exists() == true

android {
    namespace = "com.example.waterreminder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.waterreminder"
        minSdk = 26
        targetSdk = 36
        // 预发布写法：versionName 带 `-<后缀>`（如 `0.0.1-beta.1`），tag 必须与之一致
        // （`sync_version.py --expect-tag` 要求严格相等），且预发布不写回 Manifest —— 见 AGENTS.md。
        versionCode = 6
        versionName = "0.0.6"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }

    // 自定义 APK 文件名：喝水提醒_v版本号_构建类型.apk
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "WaterReminder_v${variant.versionName}_${variant.buildType.name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

// 打包 release 前强制校验签名，防止再产出装不上的未签名 APK
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        check(hasReleaseKeystore) {
            // 报错信息常被粘进 issue / 日志：只报状态与文件名，不打印本机绝对路径
            val storeFileProp = keystoreProps.getProperty("storeFile")
            val storeFileName = storeFileProp?.substringAfterLast('/')?.substringAfterLast('\\')
            val storeFileState = if (storeFileProp.isNullOrBlank()) {
                "未填写"
            } else {
                "已填写（$storeFileName），但该文件不存在"
            }
            """
            release 签名不可用，已中止打包。
              配置文件 : keystore.properties（项目根目录）—— ${if (keystorePropsFile.exists()) "存在" else "不存在"}
              storeFile: $storeFileState
            请参照根目录 keystore.properties.example 补齐 storeFile / storePassword / keyAlias / keyPassword。
            """.trimIndent()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
