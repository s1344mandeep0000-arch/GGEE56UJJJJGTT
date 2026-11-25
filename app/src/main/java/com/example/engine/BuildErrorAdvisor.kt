package com.example.engine

import com.example.data.model.ErrorSolution

object BuildErrorAdvisor {

    fun diagnoseLogs(logText: String): List<ErrorSolution> {
        val solutions = mutableListOf<ErrorSolution>()
        val lower = logText.lowercase()

        if (lower.contains("sdk location not found") || lower.contains("android_home")) {
            solutions.add(
                ErrorSolution(
                    title = "Android SDK Path Missing",
                    explanation = "The build runner could not locate the Android SDK directory (ANDROID_HOME).",
                    suggestedFix = "Ensure the CI runner runs android-actions/setup-android or configure ANDROID_HOME on your Remote Gradle Server."
                )
            )
        }

        if (lower.contains("manifest merger failed")) {
            solutions.add(
                ErrorSolution(
                    title = "AndroidManifest Merger Conflict",
                    explanation = "Two dependencies or manifest entries declare conflicting attributes (e.g., minSdk, allowBackup, or exported).",
                    suggestedFix = "Open AndroidManifest.xml Editor and ensure android:exported is set on all activities with intent-filters, and align minSdk in build.gradle."
                )
            )
        }

        if (lower.contains("duplicate class")) {
            solutions.add(
                ErrorSolution(
                    title = "Duplicate Class Dependency Collision",
                    explanation = "Multiple Gradle dependencies bundle the same transitive library class.",
                    suggestedFix = "Open Dependencies Manager in your project and remove conflicting older support libraries or align BOM versions."
                )
            )
        }

        if (lower.contains("unresolved reference") || lower.contains("cannot find symbol")) {
            solutions.add(
                ErrorSolution(
                    title = "Unresolved Symbol or Missing Dependency",
                    explanation = "Kotlin/Java code references a class or method that is missing an import or Gradle library.",
                    suggestedFix = "Check imports in the failing source file or search Maven Central in the project Dependencies tab."
                )
            )
        }

        if (lower.contains("could not resolve") || lower.contains("failed to resolve")) {
            solutions.add(
                ErrorSolution(
                    title = "Gradle Dependency Resolution Failed",
                    explanation = "Gradle could not download a requested artifact from google() or mavenCentral().",
                    suggestedFix = "Verify the group:artifact:version coordinates in app/build.gradle(.kts) and ensure google() & mavenCentral() are declared in settings.gradle."
                )
            )
        }

        if (lower.contains("aapt") && lower.contains("resource")) {
            solutions.add(
                ErrorSolution(
                    title = "AAPT2 Resource Linking Failure",
                    explanation = "An XML layout, drawable, or string reference (like @string/app_name) does not exist or has invalid XML.",
                    suggestedFix = "Run Project Validation to check missing strings.xml or invalid resource names (must be lowercase a-z, 0-9, _)."
                )
            )
        }

        if (lower.contains("outofmemoryerror") || lower.contains("metaspace")) {
            solutions.add(
                ErrorSolution(
                    title = "Gradle Daemon Out of Memory",
                    explanation = "The JVM heap allocated to Gradle was exhausted during DEX/R8 compilation.",
                    suggestedFix = "Increase Gradle JVM Args in Settings (e.g., -Xmx3072m) or update gradle.properties."
                )
            )
        }

        if (lower.contains("no build runner configured") || lower.contains("runner needed")) {
            solutions.add(
                ErrorSolution(
                    title = "Configure a Real APK Build Runner",
                    explanation = "Android devices cannot run full JDK + Android SDK + Gradle toolchains locally without a connected build environment.",
                    suggestedFix = "Open Settings and connect either a GitHub Actions Runner (Personal Access Token + repo) or your Remote Gradle Build Server URL."
                )
            )
        }

        return solutions.distinctBy { it.title }
    }
}
