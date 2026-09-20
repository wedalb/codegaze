import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "io.codegaze"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

intellijPlatform {
    pluginConfiguration {
        id = "io.codegaze.intellij"
        name = "CodeGaze"
        version = project.version.toString()
        ideaVersion { sinceBuild = "243" }
        vendor { name = "CodeGaze contributors"; url = "https://github.com/wedalb/codegaze" }
    }
    buildSearchableOptions = false
}

tasks.processResources {
    from(rootProject.file("web")) { into("web"); exclude("*.test.mjs") }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "2g"
    testLogging { events("passed", "skipped", "failed") }
}
