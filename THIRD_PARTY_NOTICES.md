# Third-party software

CodeGaze source is MIT licensed. The browser viewer has no third-party JavaScript dependencies.

- **Godot Engine 4.4.1** powers the native viewer. Godot is MIT licensed; its export binaries include third-party components. Full engine and bundled-library licenses: https://github.com/godotengine/godot/blob/4.4.1-stable/COPYRIGHT.txt and https://godotengine.org/license/.
- **IntelliJ Platform** APIs are provided by the user's IDE and are not redistributed in the plugin ZIP. Community source: https://github.com/JetBrains/intellij-community (Apache 2.0, with third-party notices).
- **Gson** is supplied by IntelliJ (Apache 2.0): https://github.com/google/gson.
- **Gradle wrapper** (Apache 2.0): https://github.com/gradle/gradle/blob/v8.13.0/LICENSE.
- JUnit and the IntelliJ Platform Gradle Plugin are build/test dependencies, not included in the viewer.

Native binaries are not code signed or notarized. Review the source and the matching CI run before installing a research preview.
