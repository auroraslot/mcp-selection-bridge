plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "life.irony"
version = "1.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 编译基线 = since-build 对应的最老平台，避免误用新版 API
        // 2025.3 起 IDEA 为统一发行版，用 intellijIdea() 而非已废弃的 intellijIdeaCommunity()
        intellijIdea("2026.1.4")
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
