plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // iosArm64 = iPad الحقيقي (للتثبيت عبر Sideloadly)، والمحاكي للتجربة على Mac
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            // إطار ديناميكي: يربط Kotlin كل مكتبات النظام بنفسه، ويُضمَّن داخل التطبيق
            isStatic = false
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-opt-in=kotlinx.cinterop.BetaInteropApi",
            "-Xexpect-actual-classes"
        )
    }
}
