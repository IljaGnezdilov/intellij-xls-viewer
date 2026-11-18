import org.gradle.api.tasks.JavaExec
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "org.cprime"
version = "0.4.0"

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
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.apache.poi:poi:5.5.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
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
        // `version` defaults to the project version above; keep it defined in one place only.

        // The first sentence is what the Marketplace search card shows (about 40 characters),
        // so it must stay short and start with the value proposition.
        description = """
            <p>Read-only XLS viewer with workbook diff.</p>
            <p>Open legacy Excel 97-2003 workbooks (<code>.xls</code>) right in the IDE and see them the way
            Excel draws them: fonts, fills, borders, alignment, text wrapping, merged cells, frozen panes,
            row heights, column widths and number formats.</p>
            <ul>
              <li>Sheet switching with Excel-like tabs, <code>Ctrl+PgUp</code> / <code>Ctrl+PgDn</code> and hidden sheets</li>
              <li>Range selection and <code>Ctrl+C</code> as TSV, ready to paste into Excel</li>
              <li>Zoom 50-300%, gridlines toggle, IDE theme aware colors</li>
              <li>Formula results are shown, the formula itself is available in a tooltip</li>
            </ul>
            <p><b>Workbook diff</b> is picked up automatically for Show Diff, Compare with Local and Diff in Commit:</p>
            <ul>
              <li>side-by-side before/after panes with synchronized scrolling</li>
              <li>change markers on sheets, rows and columns; changed cells are outlined; added and deleted
              rows and columns are shown as hatched placeholders</li>
              <li>"Only changes" mode collapses unchanged rows and columns and marks skipped ranges with dashed gaps</li>
              <li><code>Alt+Arrow</code> and <code>F7</code> / <code>Shift+F7</code> navigation between changes</li>
            </ul>
            <p>Requires IntelliJ IDEA 2026.2 or newer. The plugin only reads workbooks and never modifies them.
            Source code and issue tracker: <a href="https://github.com/IljaGnezdilov/intellij-xls-viewer">GitHub</a>.
            Licensed under MIT.</p>
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>Removed the internal platform API usage: the workbook diff tool is now registered
              through the public extension API with <code>LoadingOrder.FIRST</code> instead of
              <code>DiffToolSubstitutor</code>.</li>
              <li>First release on JetBrains Marketplace (0.3.9).</li>
              <li>Selection outline is drawn as a clean rectangle around the selected range.</li>
              <li>Navigation pad with up/down/left/right arrows is a single 2x2 block without gaps.</li>
              <li>One artifact for IntelliJ IDEA 2026.2 and 2026.3.</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "c-prime"
            url = "https://c-prime.org"
            email = "ilja_gnezdilov@mail.ru"
        }

        ideaVersion {
            sinceBuild = "262"
        }
    }

    publishing {
        // Token comes from -PintellijPlatformPublishingToken=... or an environment variable and is
        // never stored in the repository.
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

    pluginVerification {
        // Verify against the local IDE selected by -PidePath instead of downloading distributions.
        ides {
            local(idePath)
        }

        // Fail on internal API usages as well: the Marketplace review rejects them, so a regression
        // must break the build instead of silently passing.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
        )
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

