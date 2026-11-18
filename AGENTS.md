# AGENTS.md

Working notes for AI coding agents. User-facing documentation lives in [README.md](README.md)
(English, canonical) and [README.ru.md](README.ru.md) (translation); this file covers how to
build, change and ship the plugin without breaking it.

## Project

IntelliJ IDEA plugin **XLS Viewer** (`org.cprime.xlsviewer`, group `org.cprime`): a read-only
viewer for legacy Excel 97–2003 workbooks (`.xls`, BIFF8) plus a two-workbook diff that is
picked up by the platform diff view for Git and file comparisons. One artifact serves
IntelliJ IDEA 2026.2+ (`since-build=262`) and 2026.3.

## Non-negotiable constraints

- The plugin is **read-only**. Never add code that writes or modifies workbooks.
- **No internal platform API.** `verifyPlugin` fails on `INTERNAL_API_USAGES`, and the
  Marketplace review rejects such builds. The diff tool is registered programmatically by
  `XlsDiffToolRegistrar`; do not reintroduce `com.intellij.diff.impl.DiffToolSubstitutor`
  or `ApplicationInitializedListener`.
- Do not rename the plugin id, the `org.cprime.xlsviewer` package or the vendor metadata.
- Releases must stay compatible with build 262: build them against the 2026.2 IDE.
- UI invariants that were fixed deliberately:
  - the selection frame is a clean rectangle drawn only inside the selected block;
  - the `▲▼◀▶` navigation pad is one 2×2 block without gaps (50×50, four 25×25 cells);
  - diff markers: yellow = changed, green = added, red = deleted;
  - outlines only on changed cells; added/deleted rows and columns get hatched fill, not outlines;
  - "Only changes" is on by default with context 1 and is persisted between sessions.

## Environment

- Windows, PowerShell; invoke `.\gradlew.bat`.
- JDK 25 (`jvmToolchain(25)`); Gradle wrapper 9.4.1; IntelliJ Platform Gradle Plugin 2.20.1;
  Kotlin 2.4.21; Apache POI 5.5.1 (HSSF).
- Two local IDEs are used:
  - default `-PidePath`: `D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA Community Edition` (2026.3);
  - release builds: `D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA` (2026.2).

## Commands

| Task | Command |
| --- | --- |
| Compile | `.\gradlew.bat compileKotlin` |
| All tests | `.\gradlew.bat test` |
| One test | `.\gradlew.bat test --tests "org.cprime.xlsviewer.diff.SheetDifferTest"` |
| Default build (2026.3) | `.\gradlew.bat buildPlugin` |
| Release build (2026.2) | `.\gradlew.bat "-PidePath=D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA" clean build buildPlugin` |
| Plugin Verifier | `.\gradlew.bat "-PidePath=D:/Program Files/JetBrains/Toolbox2/IntelliJ IDEA" verifyPlugin` |
| Metadata/structure checks | `.\gradlew.bat verifyPluginStructure verifyPluginProjectConfiguration` |
| Sandbox with a demo file | `.\gradlew.bat runIde -Psample=build/samples/formatting.xls` |
| Regenerate demo workbooks | `.\gradlew.bat generateSamples` |

Quote the `-PidePath` argument in PowerShell: the path contains spaces. Artifact:
`build/distributions/xls-viewer-<version>.zip`.

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

`version = "0.4.0"` in `build.gradle.kts` is the **only** place with the version. Everything
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

- 33 tests. Plain logic tests use JUnit 5; platform tests extend `BasePlatformTestCase`
  (JUnit 4 + `testFramework(TestFrameworkType.Platform)`).
- After any rendering or diff-mark change, run `test` and inspect
  `build/reports/render/*.png` (`formatting`, `large`, `diff`, `diff-selection`,
  `diff-collapsed`, `diff-collapsed-gaps`, `diff-150`). These are generated offscreen and are
  the fastest way to check visuals without launching the IDE.
- Public screenshots for the README are `docs/viewer.png` and `docs/diff.png` and are tracked.
  `docs/marketplace/` (raw shots and normalized 1600×800 Marketplace images) is gitignored and
  lives only locally.

## Release checklist

Only when explicitly asked to release:

1. Bump `version`, write `changeNotes` in `build.gradle.kts`.
2. Release build against 2026.2 (command above); tests must be green.
3. `verifyPlugin` against 2026.2 and, for the 2026.3 report, the default `idePath`.
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
- `idea-version since-build="262"` is set explicitly. Removing it would default to the major of
  the build IDE (263 when building against 2026.3) and silently drop 2026.2 support.
- The configuration cache is disabled for the platform plugin; do not enable it blindly.
- `Log4j API could not find a logging provider` on the first parse is harmless.
- `install-plugin.ps1` needs the target IDE closed: it fails fast when plugin jars are locked.
- Git normalizes line endings via `.gitattributes`; the working tree may show CRLF. Do not
  reformat files just to change line endings.
