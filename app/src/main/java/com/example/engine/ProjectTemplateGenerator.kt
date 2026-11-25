package com.example.engine

import com.example.data.model.ProjectType
import java.io.File

object ProjectTemplateGenerator {

    fun generateProject(
        rootDir: File,
        appName: String,
        packageName: String,
        projectType: ProjectType,
        compileSdk: Int = 35,
        minSdk: Int = 24,
        targetSdk: Int = 35,
        jvmArgs: String = "-Xmx2048m"
    ) {
        if (!rootDir.exists()) rootDir.mkdirs()
        val packagePath = packageName.replace('.', '/')

        // Common gradle wrapper & properties
        File(rootDir, "gradle/wrapper").mkdirs()
        File(rootDir, "gradle/wrapper/gradle-wrapper.properties").writeText(
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent()
        )

        File(rootDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=$jvmArgs -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """.trimIndent()
        )

        val safeRootName = appName.replace("\"", "").ifBlank { "MyApp" }

        when (projectType) {
            ProjectType.KOTLIN_COMPOSE, ProjectType.IMPORTED -> {
                generateKotlinComposeTemplate(
                    rootDir, safeRootName, packageName, packagePath, compileSdk, minSdk, targetSdk
                )
            }
            ProjectType.JAVA_VIEWS -> {
                generateJavaViewsTemplate(
                    rootDir, safeRootName, packageName, packagePath, compileSdk, minSdk, targetSdk
                )
            }
            ProjectType.WEB_HYBRID -> {
                generateWebHybridTemplate(
                    rootDir, safeRootName, packageName, packagePath, compileSdk, minSdk, targetSdk
                )
            }
        }
    }

    private fun generateKotlinComposeTemplate(
        rootDir: File,
        appName: String,
        packageName: String,
        packagePath: String,
        compileSdk: Int,
        minSdk: Int,
        targetSdk: Int
    ) {
        File(rootDir, "settings.gradle.kts").writeText(
            """
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
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "$appName"
            include(":app")
            """.trimIndent()
        )

        File(rootDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.1.0" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
            }
            """.trimIndent()
        )

        val appDir = File(rootDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "$packageName"
                compileSdk = $compileSdk

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = $minSdk
                    targetSdk = $targetSdk
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions {
                    jvmTarget = "17"
                }
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.activity:activity-compose:1.10.0")
                implementation(platform("androidx.compose:compose-bom:2024.12.01"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.material3:material3")
            }
            """.trimIndent()
        )

        val mainDir = File(appDir, "src/main").apply { mkdirs() }
        File(mainDir, "AndroidManifest.xml").writeText(defaultManifestXml(appName))

        val javaDir = File(mainDir, "java/$packagePath").apply { mkdirs() }
        File(javaDir, "MainActivity.kt").writeText(
            """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Scaffold { padding ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(padding)
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Welcome to $appName",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        )

        writeStandardResources(mainDir, appName)
    }

    private fun generateJavaViewsTemplate(
        rootDir: File,
        appName: String,
        packageName: String,
        packagePath: String,
        compileSdk: Int,
        minSdk: Int,
        targetSdk: Int
    ) {
        File(rootDir, "settings.gradle").writeText(
            """
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
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "$appName"
            include ':app'
            """.trimIndent()
        )

        File(rootDir, "build.gradle").writeText(
            """
            plugins {
                id 'com.android.application' version '8.7.3' apply false
            }
            """.trimIndent()
        )

        val appDir = File(rootDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle").writeText(
            """
            plugins {
                id 'com.android.application'
            }

            android {
                namespace '$packageName'
                compileSdk $compileSdk

                defaultConfig {
                    applicationId "$packageName"
                    minSdk $minSdk
                    targetSdk $targetSdk
                    versionCode 1
                    versionName "1.0"
                }

                buildTypes {
                    release {
                        minifyEnabled false
                    }
                }
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_17
                    targetCompatibility JavaVersion.VERSION_17
                }
            }

            dependencies {
                implementation 'androidx.appcompat:appcompat:1.7.0'
                implementation 'com.google.android.material:material:1.12.0'
                implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
            }
            """.trimIndent()
        )

        val mainDir = File(appDir, "src/main").apply { mkdirs() }
        File(mainDir, "AndroidManifest.xml").writeText(defaultManifestXml(appName, useAppCompat = true))

        val javaDir = File(mainDir, "java/$packagePath").apply { mkdirs() }
        File(javaDir, "MainActivity.java").writeText(
            """
            package $packageName;

            import android.os.Bundle;
            import android.widget.TextView;
            import androidx.appcompat.app.AppCompatActivity;

            public class MainActivity extends AppCompatActivity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.activity_main);
                    TextView title = findViewById(R.id.tvTitle);
                    title.setText("Hello from $appName");
                }
            }
            """.trimIndent()
        )

        val layoutDir = File(mainDir, "res/layout").apply { mkdirs() }
        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="24dp">

                <TextView
                    android:id="@+id/tvTitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/app_name"
                    android:textSize="22sp"
                    android:textStyle="bold" />

            </LinearLayout>
            """.trimIndent()
        )

        writeStandardResources(mainDir, appName, useAppCompat = true)
    }

    private fun generateWebHybridTemplate(
        rootDir: File,
        appName: String,
        packageName: String,
        packagePath: String,
        compileSdk: Int,
        minSdk: Int,
        targetSdk: Int
    ) {
        generateKotlinComposeTemplate(
            rootDir, appName, packageName, packagePath, compileSdk, minSdk, targetSdk
        )
        val mainDir = File(rootDir, "app/src/main")
        File(mainDir, "AndroidManifest.xml").writeText(
            defaultManifestXml(appName, includeInternet = true)
        )
        File(mainDir, "java/$packagePath/MainActivity.kt").writeText(
            """
            package $packageName

            import android.annotation.SuppressLint
            import android.os.Bundle
            import android.webkit.WebView
            import android.webkit.WebViewClient
            import androidx.activity.ComponentActivity

            class MainActivity : ComponentActivity() {
                @SuppressLint("SetJavaScriptEnabled")
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val webView = WebView(this).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl("file:///android_asset/www/index.html")
                    }
                    setContentView(webView)
                }
            }
            """.trimIndent()
        )

        val wwwDir = File(mainDir, "assets/www").apply { mkdirs() }
        File(wwwDir, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$appName</title>
              <link rel="stylesheet" href="styles.css" />
            </head>
            <body>
              <main class="card">
                <h1>$appName</h1>
                <p>Built with HTML, CSS &amp; JavaScript inside an Android APK wrapper.</p>
                <button id="actionBtn">Test Interaction</button>
                <p id="status"></p>
              </main>
              <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent()
        )
        File(wwwDir, "styles.css").writeText(
            """
            body {
              margin: 0;
              min-height: 100vh;
              display: grid;
              place-items: center;
              background: #0b0e14;
              color: #f1f5f9;
              font-family: system-ui, sans-serif;
            }
            .card {
              padding: 24px;
              border-radius: 16px;
              background: #121722;
              border: 1px solid #283347;
              text-align: center;
              max-width: 320px;
            }
            button {
              margin-top: 12px;
              padding: 10px 18px;
              border-radius: 10px;
              border: none;
              background: #3ddc84;
              color: #042916;
              font-weight: 700;
            }
            """.trimIndent()
        )
        File(wwwDir, "app.js").writeText(
            """
            document.getElementById("actionBtn").addEventListener("click", () => {
              const now = new Date().toLocaleTimeString();
              document.getElementById("status").textContent = "JS active at " + now;
            });
            """.trimIndent()
        )
    }

    fun defaultManifestXml(
        appName: String,
        useAppCompat: Boolean = false,
        includeInternet: Boolean = false
    ): String {
        val internetLine = if (includeInternet) {
            "    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n"
        } else ""
        val themeAttr = if (useAppCompat) {
            "android:theme=\"@style/Theme.AppTheme\""
        } else {
            "android:theme=\"@style/Theme.AppTheme\""
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

$internetLine    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        $themeAttr>
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""".trimStart()
    }

    private fun writeStandardResources(mainDir: File, appName: String, useAppCompat: Boolean = false) {
        val valuesDir = File(mainDir, "res/values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText(
            """
            <resources>
                <string name="app_name">$appName</string>
            </resources>
            """.trimIndent()
        )
        val parentTheme = if (useAppCompat) {
            "Theme.Material3.DayNight.NoActionBar"
        } else {
            "android:Theme.Material.Light.NoActionBar"
        }
        File(valuesDir, "themes.xml").writeText(
            """
            <resources>
                <style name="Theme.AppTheme" parent="$parentTheme" />
            </resources>
            """.trimIndent()
        )
    }
}
