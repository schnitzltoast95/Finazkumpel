pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val localComposeTestArtifacts = rootDir.resolve("tmp/google-maven")
        if (localComposeTestArtifacts.isDirectory) {
            // Optional local cache populated from the official repositories when a network
            // blocks dl.google.com. Fresh checkouts simply use Maven Central and Google below.
            exclusiveContent {
                forRepository {
                    maven {
                        name = "LocalComposeTestArtifacts"
                        url = uri(localComposeTestArtifacts)
                    }
                }
                filter {
                    includeVersion("androidx.compose.ui", "ui-test", "1.9.2")
                    includeVersion("androidx.compose.ui", "ui-test-android", "1.9.2")
                    includeVersion("androidx.compose.ui", "ui-test-junit4", "1.9.2")
                    includeVersion("androidx.compose.ui", "ui-test-junit4-android", "1.9.2")
                    includeVersion("androidx.concurrent", "concurrent-futures", "1.2.0")
                    includeVersion("androidx.concurrent", "concurrent-futures-ktx", "1.2.0")
                    includeVersion("androidx.tracing", "tracing", "1.1.0")
                    includeVersion("org.jetbrains.kotlinx", "kotlinx-coroutines-test-jvm", "1.9.0")
                    includeVersion("org.jetbrains.kotlinx", "kotlinx-coroutines-bom", "1.9.0")
                    includeVersion("com.google.errorprone", "error_prone_annotations", "2.30.0")
                    includeVersion("com.google.errorprone", "error_prone_parent", "2.30.0")
                    includeVersion("org.hamcrest", "hamcrest-library", "1.3")
                    includeVersion("org.hamcrest", "hamcrest-parent", "1.3")
                }
            }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "Quartalsmappe"
include(":app")

