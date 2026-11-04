plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    namespace = "com.chuckerteam.chucker.macrobenchmark"

    defaultConfig {
        minSdk = 28
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Must match the build types declared in :sample.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
        create("nonMinifiedBenchmark") {
            initWith(getByName("benchmark"))
        }
    }

    targetProjectPath = ":sample"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

androidComponents {
    // Only the release-like variants are meaningful to measure.
    beforeVariants(selector().withBuildType("debug")) { it.enable = false }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Copies the Chucker part of the generated Baseline Profile into :library, which ships it in its AAR.
// Run after `connectedNonMinifiedBenchmarkAndroidTest` with the BaselineProfile rule enabled.
tasks.register("copyChuckerBaselineProfile") {
    val outputDir = layout.buildDirectory.dir("outputs/connected_android_test_additional_output")
    val target = rootProject.file("library/src/main/baseline-prof.txt")
    doLast {
        val generated =
            outputDir
                .get()
                .asFile
                .walkTopDown()
                .filter { it.name.endsWith("-baseline-prof.txt") && it.name.startsWith("ChuckerBaselineProfile") }
                .maxByOrNull { it.lastModified() }
                ?: error("No generated profile found. Run the BaselineProfile generator first.")
        val chuckerRules =
            generated.readLines().filter { it.contains("Lcom/chuckerteam/chucker/") && !it.contains("/sample/") }
        target.writeText(chuckerRules.joinToString(separator = "\n", postfix = "\n"))
        logger.lifecycle("Wrote ${chuckerRules.size} rules from ${generated.name} to $target")
    }
}

apply(from = rootProject.file("gradle/kotlin-static-analysis.gradle"))
