package com.rideassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class RideAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var lastSignature = ""
    private var lastHandledAt = 0L

    data class Candidate(
        val button: AccessibilityNodeInfo? = null,
        val sliderRect: Rect? = null,
        val card: RideCard,
    )

    private val countdownRe = Regex(
        """(?i)(?:enabled\s+in|(?:match|confirm|accept|take)\s+in)\s*(\d{1,2})\s*(?:s|sec|secs|seconds)?\b"""
    )
    private val acceptRe = Regex(
        """(?i)^[^\p{L}\p{N}]*(?:match|confirm|accept|take|स्वीकार)[^\p{L}\p{N}]*${'$'}"""
    )

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val cfg = RideConfig.load(this)
        if (!cfg.enabled) return

        val watchedPackages = cfg.packages + (if (cfg.testMode) setOf(packageName) else emptySet())

        // 1. Handle notification events (Rapido / Porter / Uber head-up notifications)
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val notePkg = event.packageName?.toString() ?: return
            if (notePkg in watchedPackages) {
                handleNotification(event, notePkg, cfg)
            }
            return
        }

        // 2. Resolve target driver app window using WindowResolver (handles overlays, video calls, floating bubbles)
        val resolved = WindowResolver.resolve(this, event, watchedPackages) ?: return
        val root = resolved.root
        val pkg = resolved.pkg

        val screenTexts = ArrayList<String>()
        collect(root, screenTexts)
        if (screenTexts.isEmpty()) return

        // 3. Discover action nodes (Accept buttons or sliders)
        val acceptButtons = findAllAcceptButtons(root, cfg.acceptTexts)
        val candidates = ArrayList<Candidate>()

        if (acceptButtons.isNotEmpty()) {
            for (btn in acceptButtons) {
                val container = findCardContainer(btn, acceptButtons)
                val cardTexts = ArrayList<String>()
                collect(container, cardTexts)
                val card = RideParser.parse(if (cardTexts.isNotEmpty()) cardTexts else screenTexts)
                candidates.add(Candidate(button = btn, card = card))
            }
        } else {
            val slider = findSlider(root, cfg.sliderTexts)
            if (slider != null) {
                val anchor = sliderAnchor(root, cfg.sliderTexts)
                val cardTexts = ArrayList<String>()
                if (anchor != null) {
                    val container = findCardContainer(anchor, emptyList())
                    collect(container, cardTexts)
                }
                val card = RideParser.parse(if (cardTexts.isNotEmpty()) cardTexts else screenTexts)
                candidates.add(Candidate(sliderRect = slider, card = card))
            }
        }

        // If no candidate action button or slider found: log diagnostic visibility
        if (candidates.isEmpty()) {
            val now = System.currentTimeMillis()
            val sig = "$pkg|${screenTexts.take(3).joinToString()}"
            if (sig != lastSignature && now - lastHandledAt >= 10000) {
                lastSignature = sig
                lastHandledAt = now
                log(
                    pkg,
                    RideCard(null, null, null, null, false, screenTexts.joinToString(" | ")),
                    false,
                    "watching screen (no order card active)"
                )
            }
            return
        }

        // 4. Evaluate all candidate cards against configured rules
        val passing = candidates.filter { reject(cfg, it.card) == null }

        if (passing.isEmpty()) {
            val first = candidates.first()
            val reason = reject(cfg, first.card) ?: "rejected"
            val sig = "${first.card.fare}|${first.card.pickupKm}|${first.card.tripKm}|${first.card.type}"
            val now = System.currentTimeMillis()
            if (sig != lastSignature || now - lastHandledAt >= 6000) {
                lastSignature = sig
                lastHandledAt = now
                log(pkg, first.card, false, reason)
            }
            return
        }

        // 5. Choose best candidate (closest pickup)
        val winner = passing.minByOrNull { it.card.pickupKm ?: Double.MAX_VALUE } ?: passing.first()
        val signature = "${winner.card.fare}|${winner.card.pickupKm}|${winner.card.tripKm}|${winner.card.type}|${winner.card.isBoost}"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastHandledAt < 6000) return
        lastSignature = signature
        lastHandledAt = now

        // 6. Execute Accept action
        main.postDelayed({
            if (winner.button != null) {
                var ok = clickable(winner.button)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                if (!ok) {
                    // Fallback to gesture tap on center coordinates of the button
                    val bounds = Rect()
                    winner.button.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty) {
                        tap(bounds.exactCenterX(), bounds.exactCenterY()) { tapOk ->
                            log(pkg, winner.card, tapOk, if (tapOk) "accepted (tap)" else "click failed")
                            if (tapOk) RideFeedback.accepted(this, cfg, winner.card)
                        }
                        return@postDelayed
                    }
                }
                log(pkg, winner.card, ok, if (ok) "accepted" else "click failed")
                if (ok) RideFeedback.accepted(this, cfg, winner.card)
            } else if (winner.sliderRect != null) {
                swipe(winner.sliderRect) { ok ->
                    log(pkg, winner.card, ok, if (ok) "accepted (slider)" else "swipe failed")
                    if (ok) RideFeedback.accepted(this, cfg, winner.card)
                }
            }
        }, cfg.delayMs)
    }

    private fun handleNotification(event: AccessibilityEvent, pkg: String, cfg: RideConfig) {
        val parcelable = event.parcelableData
        if (parcelable !is Notification) return

        val extras = parcelable.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val combined = "$title | $text | $bigText".trim()
        val card = RideParser.parse(listOf(combined))

        // Check notification action buttons
        val actions = parcelable.actions
        if (!actions.isNullOrEmpty()) {
            for (action in actions) {
                val actionTitle = action.title?.toString() ?: ""
                if (isAcceptText(actionTitle, cfg.acceptTexts)) {
                    val rej = reject(cfg, card)
                    if (rej == null) {
                        try {
                            action.actionIntent.send()
                            log(pkg, card, true, "accepted via notification")
                            RideFeedback.accepted(this, cfg, card)
                        } catch (e: Throwable) {
                            log(pkg, card, false, "notification action failed: ${e.message}")
                        }
                    } else {
                        log(pkg, card, false, rej)
                    }
                    return
                }
            }
        }

        // If no action button, opening the notification contentIntent brings the app foreground
        val rej = reject(cfg, card)
        if (rej == null && parcelable.contentIntent != null) {
            try {
                parcelable.contentIntent.send()
                log(pkg, card, true, "opened from notification to accept")
            } catch (_: Throwable) {}
        }
    }

    private fun isAcceptText(text: String, customLabels: List<String>): Boolean {
        val t = text.trim()
        if (t.isBlank()) return false
        val lower = t.lowercase()
        if (lower.contains("decline") || lower.contains("reject") || lower.contains("cancel") || lower.contains("skip")) {
            return false
        }
        if (countdownRe.find(t) != null) return true
        if (acceptRe.find(t) != null) return true
        if (lower.startsWith("accept") || lower.startsWith("confirm") || lower.startsWith("take") || lower.startsWith("स्वीकार")) return true
        if (lower.contains("accept") || lower.contains("स्वीकार")) return true
        for (l in customLabels) {
            if (lower.contains(l.lowercase())) return true
        }
        return false
    }

    private fun reject(c: RideConfig, card: RideCard): String? {
        if (card.isBoost && !c.acceptBoost) return "boost ride off"
        card.type?.let { if (c.bookingTypes[it] == false) return "type $it off" }
        val lower = card.text.lowercase()
        c.blockedKeywords.firstOrNull { it.isNotBlank() && lower.contains(it.lowercase()) }
            ?.let { return "blocked: $it" }
        if (c.allowedKeywords.isNotEmpty() && c.allowedKeywords.none { it.isNotBlank() && lower.contains(it.lowercase()) })
            return "outside Go-To area"

        // Fare evaluation: handle unknown fare
        if (card.fare != null) {
            if (c.minFare > 0 && card.fare < c.minFare) return "fare below min (₹${card.fare.toInt()} < ₹${c.minFare.toInt()})"
        } else {
            if (!c.acceptUnknownFare) return "fare hidden (rejected)"
        }

        if (c.maxPickupKm > 0 && (card.pickupKm ?: 0.0) > c.maxPickupKm) {
            return "pickup too far (${card.pickupKm} km > ${c.maxPickupKm} km)"
        }
        card.tripKm?.let {
            if (c.minTripKm > 0 && it < c.minTripKm) return "trip too short ($it km < ${c.minTripKm} km)"
            if (c.maxTripKm > 0 && it > c.maxTripKm) return "trip too long ($it km > ${c.maxTripKm} km)"
            if (card.fare != null && c.minPerKm > 0 && it > 0 && card.fare / it < c.minPerKm) {
                return "₹/km too low (₹${(card.fare / it).toInt()}/km < ₹${c.minPerKm.toInt()}/km)"
            }
        }
        return null
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (n == null) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun findAllAcceptButtons(root: AccessibilityNodeInfo, labels: List<String>): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val seen = HashSet<AccessibilityNodeInfo>()
        val queries = listOf("Accept", "Confirm", "Take", "स्वीकार") + labels

        // 1. Fast indexed search
        for (q in queries) {
            val hits = root.findAccessibilityNodeInfosByText(q) ?: continue
            for (hit in hits) {
                val label = (hit.text ?: hit.contentDescription)?.toString() ?: ""
                if (isAcceptText(label, labels)) {
                    val clickNode = clickable(hit) ?: hit
                    if (seen.add(clickNode)) {
                        result.add(clickNode)
                    }
                }
            }
        }

        // 2. Full tree traversal fallback (essential for Chrome, WebViews, and custom Compose views)
        if (result.isEmpty()) {
            collectButtonsRecursive(root, labels, result, seen)
        }

        return result
    }

    private fun collectButtonsRecursive(
        n: AccessibilityNodeInfo?,
        labels: List<String>,
        result: MutableList<AccessibilityNodeInfo>,
        seen: MutableSet<AccessibilityNodeInfo>
    ) {
        if (n == null) return
        val label = (n.text ?: n.contentDescription)?.toString() ?: ""
        if (isAcceptText(label, labels)) {
            val clickNode = clickable(n) ?: n
            if (seen.add(clickNode)) {
                result.add(clickNode)
            }
        }
        for (i in 0 until n.childCount) {
            collectButtonsRecursive(n.getChild(i), labels, result, seen)
        }
    }

    private fun findCardContainer(anchor: AccessibilityNodeInfo, allAcceptNodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo {
        var cur = anchor.parent
        var best = anchor
        repeat(7) {
            if (cur == null) return best
            val texts = ArrayList<String>()
            collect(cur, texts)
            val joined = texts.joinToString(" ")
            val hasDistance = joined.contains("km", ignoreCase = true) || joined.contains(" m", ignoreCase = true)
            val hasOtherButtons = allAcceptNodes.any { other -> other !== anchor && isDescendantOf(other, cur) }
            if (hasDistance && !hasOtherButtons) {
                best = cur!!
            }
            if (hasOtherButtons) {
                return best
            }
            cur = cur?.parent
        }
        return best
    }

    private fun isDescendantOf(child: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): Boolean {
        var c = child.parent
        while (c != null) {
            if (c == parent) return true
            c = c.parent
        }
        return false
    }

    private fun labelNode(root: AccessibilityNodeInfo, l: String): AccessibilityNodeInfo? {
        val hits = root.findAccessibilityNodeInfosByText(l) ?: return null
        return hits.firstOrNull { (it.text ?: it.contentDescription)?.toString()?.trim().equals(l, true) }
            ?: hits.firstOrNull { it.className?.toString()?.contains("Switch") != true && !it.isCheckable }
    }

    private fun sliderAnchor(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (l in labels) labelNode(root, l)?.let { return it }
        return null
    }

    private fun findSlider(root: AccessibilityNodeInfo, labels: List<String>): Rect? {
        val screenW = resources.displayMetrics.widthPixels
        for (l in labels) {
            val hit = labelNode(root, l) ?: continue
            var best = Rect().also { hit.getBoundsInScreen(it) }
            var cur = hit.parent
            repeat(3) {
                val b = Rect()
                cur?.getBoundsInScreen(b)
                if (cur != null && b.width() < screenW * 0.98 && b.height() < 400 && b.width() >= best.width()) best = b
                cur = cur?.parent
            }
            if (!best.isEmpty) return best
        }
        return null
    }

    private fun tap(x: Float, y: Float, done: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = done(false)
        }, null)
        if (!ok) done(false)
    }

    @Volatile private var isSwiping = false

    private fun swipe(r: Rect, done: (Boolean) -> Unit) {
        if (isSwiping) {
            done(false)
            return
        }
        isSwiping = true
        val y = r.exactCenterY()
        val path = Path().apply {
            moveTo(r.left + r.width() * 0.08f, y)
            lineTo(r.right - r.width() * 0.02f, y)
        }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 320)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) {
                isSwiping = false
                done(true)
            }
            override fun onCancelled(d: GestureDescription?) {
                isSwiping = false
                done(false)
            }
        }, null)
        if (!ok) {
            isSwiping = false
            done(false)
        }
    }

    private fun clickable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = n
        while (cur != null) {
            if (cur.isClickable || cur.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                return cur
            }
            cur = cur.parent
        }
        return null
    }

    private fun log(pkg: String, c: RideCard, accepted: Boolean, note: String) {
        val appName = when {
            pkg.contains("rapido", true) -> "Rapido"
            pkg.contains("porter", true) -> "Porter"
            pkg.contains("uber", true) -> "Uber"
            pkg.contains("ola", true) -> "Ola"
            else -> pkg.substringAfterLast('.')
        }
        val e = JSONObject()
            .put("id", System.currentTimeMillis())
            .put("time", System.currentTimeMillis())
            .put("pkg", pkg)
            .put("appName", appName)
            .put("fare", c.fare ?: JSONObject.NULL)
            .put("pickupKm", c.pickupKm ?: JSONObject.NULL)
            .put("tripKm", c.tripKm ?: JSONObject.NULL)
            .put("type", c.type ?: JSONObject.NULL)
            .put("accepted", accepted)
            .put("note", note)
            .put("rawText", c.text.take(300))
        RideHistory.add(this, e)
        RideBridge.emit("ride", e.toString())
    }

    companion object {
        @Volatile var instance: RideAccessibilityService? = null
    }
}
