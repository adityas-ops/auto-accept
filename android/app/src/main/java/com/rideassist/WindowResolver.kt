package com.rideassist

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object WindowResolver {
    data class Candidate(
        val root: AccessibilityNodeInfo,
        val pkg: String,
        val layer: Int = 0,
        val isActive: Boolean = false,
        val isFocused: Boolean = false
    )

    fun resolve(
        service: AccessibilityService,
        event: AccessibilityEvent?,
        targetPackages: Set<String>
    ): Candidate? {
        val candidates = ArrayList<Candidate>()

        // 1. Scan interactive windows from AccessibilityService
        try {
            val windows = service.windows
            if (!windows.isNullOrEmpty()) {
                for (win in windows) {
                    val root = try { win.root } catch (_: Throwable) { null } ?: continue
                    val pkg = root.packageName?.toString() ?: continue
                    if (pkg in targetPackages) {
                        candidates.add(
                            Candidate(
                                root = root,
                                pkg = pkg,
                                layer = win.layer,
                                isActive = win.isActive,
                                isFocused = win.isFocused
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        if (candidates.isNotEmpty()) {
            // Pick candidate with focused or active window, then highest layer
            return candidates.maxWithOrNull(
                compareBy<Candidate> { it.isFocused }
                    .thenBy { it.isActive }
                    .thenBy { it.layer }
            )
        }

        // 2. Fallback: check rootInActiveWindow
        try {
            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()
                if (pkg != null && pkg in targetPackages) {
                    return Candidate(root = root, pkg = pkg, isActive = true, isFocused = true)
                }
            }
        } catch (_: Throwable) {}

        // 3. Fallback: check event source
        try {
            val src = event?.source
            if (src != null) {
                var top: AccessibilityNodeInfo = src
                while (top.parent != null) {
                    val p = top.parent ?: break
                    top = p
                }
                val pkg = top.packageName?.toString() ?: event?.packageName?.toString()
                if (pkg != null && pkg in targetPackages) {
                    return Candidate(root = top, pkg = pkg, isActive = true)
                }
            }
        } catch (_: Throwable) {}

        return null
    }
}

