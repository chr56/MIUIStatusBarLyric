import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
}

val isSigningFileExist: Boolean = rootProject.file("signing.properties").exists()
var signingProperties = Properties()
if (isSigningFileExist) {
    signingProperties.load(FileInputStream(rootProject.file("signing.properties")))
}
android {
    compileSdk = 33
    val buildTime=System.currentTimeMillis()
    defaultConfig {
        applicationId = "statusbar.lyric"
        minSdk = 26
        targetSdk = 33
        versionCode = 165
        versionName = "5.4.2"
        aaptOptions.cruncherEnabled = false
        aaptOptions.useNewCruncher = false
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    signingConfigs {
        create("release") {
            if (isSigningFileExist) {
                storeFile = File(signingProperties["storeFile"] as String)
                storePassword = signingProperties["storePassword"] as String
                keyAlias = signingProperties["keyAlias"] as String
                keyPassword = signingProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-log.pro"))
        }
        create("custom") {
            if (isSigningFileExist) signingConfig = signingConfigs.getByName("release")

            // package name
            applicationIdSuffix = ".custom"

            matchingFallbacks.add("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/**"
            excludes += "/kotlin/**"
            excludes += "/*.txt"
            excludes += "/*.bin"
        }
        dex {
            useLegacyPackaging = true
        }
    }
    buildFeatures {
        viewBinding = true
    }
    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "StatusBarLyric-$versionName($versionCode)-$name-$buildTime.apk"
        }
    }
}


dependencies { //API
    compileOnly("de.robv.android.xposed:api:82") //带源码Api
//    compileOnly("de.robv.android.xposed:api:82:sources") // Use Hide Api
    compileOnly(project(":hidden-api")) //MIUI 通知栏
    implementation(files("libs/miui_sdk.jar")) // microsoft app center
    val appCenterSdkVersion = "4.4.3"
    implementation("com.microsoft.appcenter:appcenter-analytics:${appCenterSdkVersion}")
    implementation("com.microsoft.appcenter:appcenter-crashes:${appCenterSdkVersion}")
    implementation(project(":blockmiui"))
    implementation(project(":xtoast"))
}
