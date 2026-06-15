plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

// Baseline-profile producer for :apps:watchcal (specs/03-roadmap.md § P0).
// Runs a startup + agenda-scroll journey on a connected device/emulator to
// capture the hot classes/methods; the result is packaged into the app's
// release build so ART pre-compiles them (JIT-free startup + scroll).
android {
    namespace = "com.popemkt.watchcal.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        minSdk = 30
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":apps:watchcal"
}

baselineProfile {
    // No managed (emulator) device is wired here — generate against whatever is connected
    // (the Xiaomi Watch 5 over wireless adb, or a Wear emulator).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
