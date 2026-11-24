# Third-party components

The plugin archive (`xls-viewer-<version>.zip`) bundles the following libraries into
`xls-viewer/lib/`. All of them are licensed under the **Apache License 2.0**, except
CurvesAPI, which uses the **BSD 3-Clause** license.

| Component | Version | License | Project |
|---|---|---|---|
| Apache POI | 5.5.1 | Apache-2.0 | https://poi.apache.org/ |
| Apache POI OOXML | 5.5.1 | Apache-2.0 | https://poi.apache.org/ |
| Apache POI OOXML Lite (schemas) | 5.5.1 | Apache-2.0 | https://poi.apache.org/ |
| Apache XMLBeans | 5.3.0 | Apache-2.0 | https://xmlbeans.apache.org/ |
| Apache Commons Codec | 1.20.0 | Apache-2.0 | https://commons.apache.org/proper/commons-codec/ |
| Apache Commons Collections | 4.5.0 | Apache-2.0 | https://commons.apache.org/proper/commons-collections/ |
| Apache Commons Compress | 1.28.0 | Apache-2.0 | https://commons.apache.org/proper/commons-compress/ |
| Apache Commons IO | 2.21.0 | Apache-2.0 | https://commons.apache.org/proper/commons-io/ |
| Apache Commons Lang | 3.18.0 | Apache-2.0 | https://commons.apache.org/proper/commons-lang/ |
| Apache Commons Math | 3.6.1 | Apache-2.0 | https://commons.apache.org/proper/commons-math/ |
| CurvesAPI | 1.08 | BSD-3-Clause | https://github.com/virtuald/curvesapi |
| SparseBitSet | 1.3 | Apache-2.0 | https://github.com/brettwooldridge/SparseBitSet |
| Apache Log4j API | 2.24.3 | Apache-2.0 | https://logging.apache.org/log4j/2.x/ |

The full text of the Apache License 2.0 is available at
https://www.apache.org/licenses/LICENSE-2.0 and is redistributed in `META-INF/LICENSE`
inside each of the Apache jars listed above. Copyright notices are held by the Apache Software
Foundation and the respective project contributors. The CurvesAPI license text is
redistributed in `META-INF/LICENSE` inside its jar.

The plugin itself is licensed under the MIT License, see [LICENSE](LICENSE).

The IntelliJ Platform itself is **not** bundled: the plugin only declares a dependency on
the `com.intellij.modules.platform` module provided by the IDE.
