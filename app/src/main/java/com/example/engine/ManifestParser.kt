package com.example.engine

import com.example.data.model.ManifestConfig
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object ManifestParser {

    val COMMON_PERMISSIONS = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.VIBRATE",
        "android.permission.BLUETOOTH_CONNECT"
    )

    fun parseManifest(xmlContent: String): ManifestConfig {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlContent)))
            doc.documentElement.normalize()

            val manifestEl = doc.documentElement
            if (manifestEl.tagName != "manifest") {
                return ManifestConfig(
                    isValidXml = false,
                    parseError = "Root element must be <manifest>, found <${manifestEl.tagName}>"
                )
            }

            val pkgName = manifestEl.getAttribute("package").orEmpty()
            val permissions = mutableListOf<String>()
            val permNodes = doc.getElementsByTagName("uses-permission")
            for (i in 0 until permNodes.length) {
                val el = permNodes.item(i) as? Element ?: continue
                val perm = attr(el, "name")
                if (perm.isNotBlank() && !permissions.contains(perm)) {
                    permissions.add(perm)
                }
            }

            val appNodes = doc.getElementsByTagName("application")
            val appEl = if (appNodes.length > 0) appNodes.item(0) as? Element else null
            val appLabel = appEl?.let { attr(it, "label") }.orEmpty()
            val cleartext = appEl?.let { attr(it, "usesCleartextTraffic") }.equals("true", ignoreCase = true)

            var launcherActivity = ""
            val activityNodes = doc.getElementsByTagName("activity")
            for (i in 0 until activityNodes.length) {
                val actEl = activityNodes.item(i) as? Element ?: continue
                val actName = attr(actEl, "name")
                val filters = actEl.getElementsByTagName("intent-filter")
                for (f in 0 until filters.length) {
                    val filterEl = filters.item(f) as? Element ?: continue
                    val actions = filterEl.getElementsByTagName("action")
                    val cats = filterEl.getElementsByTagName("category")
                    var hasMain = false
                    var hasLauncher = false
                    for (a in 0 until actions.length) {
                        val aEl = actions.item(a) as? Element ?: continue
                        if (attr(aEl, "name") == "android.intent.action.MAIN") hasMain = true
                    }
                    for (c in 0 until cats.length) {
                        val cEl = cats.item(c) as? Element ?: continue
                        if (attr(cEl, "name") == "android.intent.category.LAUNCHER") hasLauncher = true
                    }
                    if (hasMain && hasLauncher && actName.isNotBlank()) {
                        launcherActivity = actName
                        break
                    }
                }
                if (launcherActivity.isNotBlank()) break
            }

            ManifestConfig(
                packageName = pkgName,
                appLabel = appLabel,
                usesCleartextTraffic = cleartext,
                launcherActivity = launcherActivity.ifBlank { ".MainActivity" },
                permissions = permissions,
                isValidXml = true,
                parseError = null
            )
        } catch (e: Exception) {
            ManifestConfig(
                isValidXml = false,
                parseError = e.message ?: "Malformed AndroidManifest.xml"
            )
        }
    }

    fun applyConfigToXml(originalXml: String, config: ManifestConfig): String {
        var updated = originalXml

        // Update permissions cleanly
        val permRegex = Regex("""\s*<uses-permission\s+android:name=["'][^"']+["']\s*/>\s*\n?""")
        updated = permRegex.replace(updated, "\n")

        val permBlock = if (config.permissions.isNotEmpty()) {
            config.permissions.joinToString("\n") {
                "    <uses-permission android:name=\"$it\" />"
            } + "\n\n"
        } else {
            ""
        }

        val appIndex = updated.indexOf("<application")
        if (appIndex != -1) {
            val beforeApp = updated.substring(0, appIndex).trimEnd()
            val fromApp = updated.substring(appIndex)
            updated = "$beforeApp\n\n$permBlock    $fromApp"
        }

        // Update android:label
        if (config.appLabel.isNotBlank()) {
            val labelRegex = Regex("""android:label=["'][^"']*["']""")
            updated = if (labelRegex.containsMatchIn(updated)) {
                labelRegex.replaceFirst(updated, "android:label=\"${config.appLabel}\"")
            } else {
                updated.replaceFirst("<application", "<application\n        android:label=\"${config.appLabel}\"")
            }
        }

        // Update android:usesCleartextTraffic
        val cleartextRegex = Regex("""android:usesCleartextTraffic=["'][^"']*["']""")
        updated = if (cleartextRegex.containsMatchIn(updated)) {
            cleartextRegex.replaceFirst(updated, "android:usesCleartextTraffic=\"${config.usesCleartextTraffic}\"")
        } else if (config.usesCleartextTraffic) {
            updated.replaceFirst("<application", "<application\n        android:usesCleartextTraffic=\"true\"")
        } else {
            updated
        }

        return updated
    }

    private fun attr(element: Element, localName: String): String {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val node = attrs.item(i)
            val nodeLocal = node.localName ?: node.nodeName.substringAfter(':')
            if (nodeLocal == localName) {
                return node.nodeValue.orEmpty()
            }
        }
        return ""
    }
}
