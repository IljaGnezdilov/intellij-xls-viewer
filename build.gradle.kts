import org.gradle.api.tasks.JavaExec
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "org.cprime"
version = "0.6.0"

/**
 * IDE used to compile against. The release artifact is built against the oldest supported
 * IDE (2025.2, build 252) so that one jar works on 2025.2 through 2026.3 and newer:
 *
 *   gradlew clean build buildPlugin
 *
 * The download of that distribution is handled by the IntelliJ Platform Gradle Plugin.
 * `-PtargetIde=local` builds against the locally installed IDE selected by `-PidePath`
 * instead, which is handy for day-to-day development against the current release.
 */
val targetIde: String = providers.gradleProperty("targetIde").getOrElse("2025.2")

/**
 * IntelliJ IDEA Community is not published for 2025.3 and newer; those releases ship as the
 * unified "IntelliJ IDEA" product.
 */
fun communityAvailable(version: String): Boolean {
    val major = version.substringBefore('.').toIntOrNull() ?: return false
    val minor = version.substringAfter('.', "0").substringBefore('.').toIntOrNull() ?: 0
    return major < 2025 || (major == 2025 && minor <= 2)
}

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
        // Build against the oldest supported IDE, not the local one: APIs present in that build
        // are guaranteed to exist in every newer build in range. Use -PtargetIde=local to compile
        // against the locally installed IDE instead.
        if (targetIde == "local") {
            local(idePath)
        } else if (communityAvailable(targetIde)) {
            create(IntelliJPlatformType.IntellijIdeaCommunity, targetIde)
        } else {
            // Newer releases ship as the unified "IntelliJ IDEA" product.
            create(IntelliJPlatformType.IntellijIdea, targetIde)
        }
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.apache.poi:poi:5.5.1")
    // OOXML (.xlsx): XSSF workbooks, styles, shared strings and the OPC package.
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

kotlin {
    // JDK 25 compiles the code; the class files must stay at 21. IntelliJ 2025.2-2026.1 run on
    // JBR 21, so a 25 target would fail there with UnsupportedClassVersionError. 2026.2+ is the
    // first platform on Java 25, and 21 bytecode runs there as well.
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        id = "org.cprime.xlsviewer"
        name = "XLS Viewer"
        // `version` defaults to the project version above; keep it defined in one place only.

        // The first sentence is what the Marketplace search card shows (about 40 characters),
        // so it must stay short and start with the value proposition.
        description = """
            <p>Read-only XLS/XLSX viewer with workbook diff.</p>
            <p>Open Excel workbooks (<code>.xls</code>, Excel 97-2003, and <code>.xlsx</code>) right in the IDE and see
            them the way Excel draws them: fonts, fills, borders, alignment, text wrapping, merged cells, frozen panes,
            row heights, column widths, number formats, theme colors and colored sheet tabs.</p>
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
              <li>an <code>.xls</code> revision can be compared with an <code>.xlsx</code> one</li>
            </ul>
            <p>Requires IntelliJ IDEA 2025.2 or newer. The plugin only reads workbooks and never modifies them.
            Source code and issue tracker: <a href="https://github.com/IljaGnezdilov/intellij-xls-viewer">GitHub</a>.
            Licensed under MIT.</p>
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>Support for modern <b>.xlsx</b> workbooks in the viewer and in the diff viewer, including
              theme colors, tinted fills and colored sheet tabs.</li>
              <li>An <code>.xls</code> revision can be compared with an <code>.xlsx</code> one: both are rendered
              in the same grid with the same change markers, "Only changes" mode and navigation.</li>
              <li>Large <code>.xlsx</code> sheets are protected by a cell budget so that a badly formatted file
              cannot exhaust the IDE heap.</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "c-prime"
            url = "https://c-prime.org"
            email = "ilja_gnezdilov@mail.ru"
        }

        ideaVersion {
            sinceBuild = "252"
        }
    }

    publishing {
        // Token comes from -PintellijPlatformPublishingToken=... or an environment variable and is
        // never stored in the repository.
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

    pluginVerification {
        // Check both ends of the supported range plus the current release. The compile target is
        // 2025.2, so verifying it proves the artifact is binary compatible with the minimum.
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
            create(IntelliJPlatformType.IntellijIdea, "2026.2")
            create(IntelliJPlatformType.IntellijIdea, "2026.3")
        }

        // Fail on internal and deprecated API usages as well: the Marketplace review rejects the
        // former, so a regression must break the build instead of silently passing.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
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
        description = "Generates demo .xls and .xlsx workbooks in build/samples"
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
            jvmTarget.set(JvmTarget.JVM_21)
            // 2025.2 bundles Kotlin stdlib 2.1.20 and the stdlib is not packaged with the plugin.
            // Pinning the language/API level keeps the compiler from emitting calls to newer stdlib
            // declarations that would only fail at runtime on the oldest supported IDE.
            languageVersion.set(KotlinVersion.KOTLIN_2_1)
            apiVersion.set(KotlinVersion.KOTLIN_2_1)
        }
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            languageVersion.set(KotlinVersion.KOTLIN_2_1)
            apiVersion.set(KotlinVersion.KOTLIN_2_1)
        }
    }
}

