import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    sourceSets["main"].kotlin.srcDir("../common/src")
    sourceSets["test"].kotlin.srcDir("../common/test")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.aese.ipcalc.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "IPCalcRed"
            packageVersion = "1.0.0"
            description = "Calculadora IP y escáner de red"
            vendor = "Augusto"
            includeAllModules = true
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menu = true
                menuGroup = "IPCalc Red"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6b0f7a8e-3c1d-4f5a-9e2b-7d4c1a2b3c4d"
            }
            linux { iconFile.set(project.file("icons/icon.png")) }
        }
    }
}
