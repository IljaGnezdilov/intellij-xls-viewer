# AGENTS.md

Working notes for AI coding agents. User-facing documentation lives in [README.md](README.md)
(English, canonical) and [README.ru.md](README.ru.md) (translation); this file covers how to
build, change and ship the plugin without breaking it.

## Project

IntelliJ IDEA plugin **XLS Viewer** (`org.cprime.xlsviewer`, group `org.cprime`): a read-only
viewer for legacy Excel 97–2003 workbooks (`.xls`, BIFF8) plus a two-workbook diff that is
picked up by the platform diff view for Git and file comparisons. One artifact serves
IntelliJ IDEA 2025.2+ (`since-build=252`), i.e. 2025.2 through 2026.3 and newer.

## Non-negotiable constraints

- The plugin is **read-only**. Never add code that writes or modifies workbooks.
- **No internal platform API.** `verifyPlugin` fails on `INTERNAL_API_USAGES`, and the
  Marketplace review rejects such builds. The diff tool is registered programmatically by
  `XlsDiffToolRegistrar`; do not reintroduce `com.intellij.diff.impl.DiffToolSubstitutor`
  or `ApplicationInitializedListener`.
- Do not rename the plugin id, the `org.cprime.xlsviewer` package or the vendor metadata.
- Releases must stay compatible with **build 252**: compile against the 2025.2 IDE.
- **Bytecode target is Java 21, never 25.** 2025.2–2026.1 run on JBR 21; Java 25 classes fail
  there with `UnsupportedClassVersionError` before any plugin code runs. JDK 25 is only the
  compiler (`jvmToolchain(25)` + `jvmTarget = JVM_21`). The Verifier catches this only because
  2025.2 is in its matrix — do not drop it.
- **Kotlin API level is pinned to 2.1** (`apiVersion`/`languageVersion` on `compileKotlin`):
  2025.2 bundles stdlib 2.1.20 and the stdlib is not packaged with the plugin. Raising it
  without raising the supported IDE range breaks 2025.2 at runtime with `NoSuchMethodError`.
- UI invariants that were fixed deliberately:
  - the selection frame is a clean rectangle drawn only inside the selected block;
  - the `▲▼◀▶` navigation pad is one 2×2 block without gaps (50×50, four 25×25 cells);
  - diff markers: yellow = changed, green = added, red = deleted;
  - outlines only on changed cells; added/deleted rows and columns get hatched fill, not outlines;
  - "Only changes" is on by default with context 1 and is persisted between sessions;
  - empty changes (both cells empty: formatting-only, empty formula result, whitespace-only
    text) are hidden by default - not marked, not counted, not kept visible in "Only
    changes" - and the status bar reports how many were hidden. The "Show empty changes"
    checkbox (off by default, persisted) brings the old behaviour back. Added/deleted rows
    and columns are always shown, even when empty (`ignoreEmptyChanges` in `SheetDiffer`).

## Environment

- Windows, PowerShell; invoke `.\gradlew.bat`.
- JDK 25 (`jvmToolchain(25)`) compiling to Java 21 bytecode; Gradle wrapper 9.4.1;
  IntelliJ Platform Gradle Plugin 2.20.1; Kotlin 2.4.21 (language/API 2.1); Apache POI 5.5.1
  (HSSF).
- By default the build targets the **downloaded 2025.2** (build 252) distribution, so the
  artifact is binary compatible with every supported IDE. `-PtargetIde=local` switches to the
  locally installed IDE behind `-PidePath` (default
  `D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA Community Edition`) for the dev loop.
- IntelliJ IDEA Community is not published after 2025.2: for newer releases the build and the
  Verifier use the unified `IntelliJPlatformType.IntellijIdea` product
  (`communityAvailable(...)` in `build.gradle.kts` picks the right one).

## Commands

| Task | Command |
| --- | --- |
| Compile | `.\gradlew.bat compileKotlin` |
| All tests | `.\gradlew.bat test` |
| One test | `.\gradlew.bat test --tests "org.cprime.xlsviewer.diff.SheetDifferTest"` |
| Release build (2025.2, default) | `.\gradlew.bat clean build buildPlugin` |
| Dev build against the local IDE | `.\gradlew.bat -PtargetIde=local buildPlugin` |
| Plugin Verifier (2025.2, 2026.2, 2026.3) | `.\gradlew.bat verifyPlugin` |
| Metadata/structure checks | `.\gradlew.bat verifyPluginStructure verifyPluginProjectConfiguration` |
| Sandbox with a demo file | `.\gradlew.bat runIde -Psample=build/samples/formatting.xls` |
| Regenerate demo workbooks | `.\gradlew.bat generateSamples` |

Quote the `-PidePath` argument in PowerShell: the path contains spaces. The first build against
2025.2 downloads that distribution (~1.3 GB); the Verifier downloads 2026.2 and 2026.3 as well.
Artifact: `build/distributions/xls-viewer-<version>.zip`.

## Source map

```
parse/   XlsParser             BIFF8 -> immutable model, workbook closed immediately
         StyleTableBuilder     XF/font deduplication, colors from the workbook palette
         CellTextFormatter     DataFormatter + cached formula results
         ColorPalette          palette handling
         LfsPointer            Git LFS pointer detection for diff sides
model/   XlsDocument, SheetModel, CellModel, CellStyleModel, CellRange,
         SheetDiffMarks, SheetTab
ui/      GridGeometry          prefix sums + binary search for hit testing
         XlsGridComponent      owner-drawn virtualized grid (merges, freeze, borders,
                               diff tint/outlines, filler hatching, selection)
         XlsHeaders            row/column headers, marker dots
         XlsSheetTabs          sheet tabs with dots
         XlsNavPad             hand-drawn 2x2 navigation pad
         XlsTheme              IDE-theme colors, "Excel colors", diff colors
editor/  XlsFileEditorProvider, XlsFileEditor, XlsEditorPanel, XlsEditorState
diff/    XlsDiffTool, XlsDiffToolRegistrar, XlsDiffViewer, XlsDiffPanel
         SheetDiffer           sheet/row/column alignment, patience diff
         DiffSides             synthetic SheetModel for each pane
         XlsDiffContents       reading diff sides, OLE2/LFS validation
         DiffNavigation        next/previous change navigation
XlsViewerSettings, XlsViewerBundle (messages + _ru bundle)
```

The grid is owner-drawn on purpose: merged cells, frozen panes and per-side borders cannot be
expressed with `JTable` without hacks. BIFF8 is limited to 65536×256, so geometry is stored in
arrays and painting only touches the visible area.

## How the diff integration works

`diff.DiffTool` has no `order` attribute, and the built-in binary viewer is registered by the
platform before plugins, so it would always win for `.xls`. `XlsDiffToolRegistrar` registers
`XlsDiffTool` at application startup with `DiffTool.EP_NAME.point.registerExtension(...,
LoadingOrder.FIRST, ...)` through the public extension API. `XlsDiffToolOrderTest` asserts our
tool precedes `BinaryDiffTool`; if it fails, the diff tool is effectively disabled — treat that
as a release blocker.

Diff flow: `XlsDiffContents` reads both sides (handling LFS pointers and OLE2 checks) →
`XlsParser` → `SheetDiffer` aligns sheets/rows/columns → `DiffSides` builds synthetic models →
`XlsDiffPanel` renders them.

## Version and metadata

`version = "0.5.1"` in `build.gradle.kts` is the **only** place with the version. Everything
else derives from it:

- `patchPluginXml` injects `<version>`, `<description>`, `<change-notes>`, vendor and
  `since-build` into the packaged `plugin.xml`; `src/main/resources/META-INF/plugin.xml` has no
  `<version>` element on purpose;
- the artifact name comes from the Gradle project version;
- `install-plugin.ps1` picks the newest `build/distributions/xls-viewer-*.zip` when `-Zip` is
  not given;
- both READMEs use the `<version>` placeholder.

When releasing: bump the one line, update `changeNotes` in `build.gradle.kts` (Marketplace shows
it), and leave `plugin.xml` alone. `pluginConfiguration.id/name` are still required there.

## Tests and visual artifacts

- 38 tests. Plain logic tests use JUnit 5; platform tests extend `BasePlatformTestCase`
  (JUnit 4 + `testFramework(TestFrameworkType.Platform)`).
- After any rendering or diff-mark change, run `test` and inspect
  `build/reports/render/*.png` (`formatting`, `large`, `diff`, `diff-selection`,
  `diff-collapsed`, `diff-collapsed-gaps`, `diff-150`, `diff-empty-hidden`,
  `diff-empty-shown`). These are generated offscreen and are
  the fastest way to check visuals without launching the IDE.
- Public screenshots for the README are `docs/viewer.png` and `docs/diff.png` and are tracked.
  `docs/marketplace/` (raw shots and normalized 1600×800 Marketplace images) is gitignored and
  lives only locally.

## Release checklist

Only when explicitly asked to release:

1. Bump `version`, write `changeNotes` in `build.gradle.kts`.
2. Release build against 2025.2 (the default command); tests must be green.
3. `verifyPlugin` against 2025.2, 2026.2 and 2026.3 (configured in `pluginVerification`).
   Requirements: **Compatible, 0 internal API usages, 0 deprecated API usages**.
4. `git tag -a v<version>` and `gh release create v<version> "build/distributions/xls-viewer-<version>.zip" --title ... --notes-file ...`.
5. Marketplace upload is a browser action on the plugin page (artifact is >5 MiB, so the upload
   needs the OS file dialog). Automated publishing uses
   `.\gradlew.bat publishPlugin -PintellijPlatformPublishingToken=...`; the token comes from a
   Gradle property or `PUBLISH_TOKEN` and must never be committed.

## Pitfalls

- `buildSearchableOptions` is disabled on purpose; do not re-enable it.
- `kotlin.stdlib.default.dependency=false` — the Kotlin stdlib ships with the platform; never
  bundle a second copy.
- `idea-version since-build="252"` is set explicitly. Removing it would default to the major of
  the build IDE (263 when building against 2026.3) and silently drop every older IDE.
- Java target 21 and Kotlin API 2.1 are the floor set by 2025.2 (JBR 21, stdlib 2.1.20). Kotlin
  warns that language version 2.1 is deprecated; that warning is expected here and can only be
  removed by raising the minimum IDE. Do not silence it by bumping the version.
- Compiling against 2025.2 is what guarantees binary compatibility; `-PtargetIde=local` builds
  are for the dev loop only and must never be published.
- The configuration cache is disabled for the platform plugin; do not enable it blindly.
- `Log4j API could not find a logging provider` on the first parse is harmless.
- `install-plugin.ps1` needs the target IDE closed: it fails fast when plugin jars are locked.
- Git normalizes line endings via `.gitattributes`; the working tree may show CRLF. Do not
  reformat files just to change line endings.
