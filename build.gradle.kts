plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "life.irony"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 编译基线 = since-build 对应的最老平台，避免误用新版 API
        intellijIdeaCommunity("2026.1")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "263.*"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}
