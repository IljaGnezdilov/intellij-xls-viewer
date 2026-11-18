# Third-party components

The plugin archive (`xls-viewer-<version>.zip`) bundles the following libraries into
`xls-viewer/lib/`. All of them are licensed under the **Apache License 2.0**.

| Component | Version | License | Project |
|---|---|---|---|
| Apache POI | 5.5.1 | Apache-2.0 | https://poi.apache.org/ |
| Apache Commons Codec | 1.20.0 | Apache-2.0 | https://commons.apache.org/proper/commons-codec/ |
| Apache Commons Collections | 4.5.0 | Apache-2.0 | https://commons.apache.org/proper/commons-collections/ |
| Apache Commons IO | 2.21.0 | Apache-2.0 | https://commons.apache.org/proper/commons-io/ |
| Apache Commons Math | 3.6.1 | Apache-2.0 | https://commons.apache.org/proper/commons-math/ |
| SparseBitSet | 1.3 | Apache-2.0 | https://github.com/brettwooldridge/SparseBitSet |
| Apache Log4j API | 2.24.3 | Apache-2.0 | https://logging.apache.org/log4j/2.x/ |

The full text of the Apache License 2.0 is available at
https://www.apache.org/licenses/LICENSE-2.0 and is redistributed in `META-INF/LICENSE`
inside each of the jars listed above. Copyright notices are held by the Apache Software
Foundation and the respective project contributors.

The plugin itself is licensed under the MIT License, see [LICENSE](LICENSE).

The IntelliJ Platform itself is **not** bundled: the plugin only declares a dependency on
the `com.intellij.modules.platform` module provided by the IDE.
