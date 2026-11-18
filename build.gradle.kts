import org.gradle.api.tasks.JavaExec

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "org.cprime"
version = "0.3.8"

/**
 * IDE used to compile against. The release artifact is built against the oldest supported
 * IDE (2026.2) so that one jar works on 2026.2 and 2026.3:
 *
 *   gradlew -PidePath="D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA" clean build buildPlugin
 *
 * The default points at the newer installation.
 */
val idePath: String = providers.gradleProperty("idePath").getOrElse(
    "D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA Community Edition",
)

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against the IDE selected by -PidePath (defaults to the local 2026.3 install).
        local(idePath)
    }

    implementation("org.apache.poi:poi:5.5.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

intellijPlatform {
    pluginConfiguration {
        id = "org.cprime.xlsviewer"
        name = "XLS Viewer"
        version = "0.3.8"

        description = """
            <h3>XLS Viewer</h3>
            <p>Read-only viewer for legacy Excel 97-2003 workbooks (<code>.xls</code>) with faithful rendering of
            cell, row and column formatting: fonts, fills, borders, alignment, merged cells, frozen panes,
            row heights, column widths and number formats.</p>
            <ul>
              <li>Sheet switching with Excel-like tabs and <code>Ctrl+PgUp</code> / <code>Ctrl+PgDn</code></li>
              <li>Selection and <code>Ctrl+C</code> as TSV, ready to paste into Excel</li>
              <li>Zoom, gridlines toggle, IDE theme aware colors</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "c-prime"
            url = "https://c-prime.org"
        }

        ideaVersion {
            sinceBuild = "262"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    matching { it.name == "buildSearchableOptions" }.configureEach {
        enabled = false
    }

    register<JavaExec>("generateSamples") {
        group = "verification"
        description = "Generates demo .xls workbooks in build/samples"
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("org.cprime.xlsviewer.tools.SampleWorkbookKt")
        args("build/samples")
    }

    matching { it.name == "runIde" }.configureEach {
        val sample = providers.gradleProperty("sample").orNull
        if (this is JavaExec && !sample.isNullOrBlank()) {
            args(File(projectDir, sample).absolutePath)
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    compileKotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

