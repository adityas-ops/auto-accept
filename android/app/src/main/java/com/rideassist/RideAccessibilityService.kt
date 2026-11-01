package com.rideassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val cfg = RideConfig.load(this)
        val watched = pkg in cfg.packages || (cfg.testMode && pkg == packageName)
        if (!cfg.enabled || !watched) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val screenTexts = ArrayList<String>()
        collect(root, screenTexts)
        if (screenTexts.isEmpty()) return

        // 1. Discover all action nodes (Accept buttons or sliders)
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

        if (candidates.isEmpty()) return

        // 2. Evaluate all candidate cards against rules
        val passing = candidates.filter { reject(cfg, it.card) == null }

        if (passing.isEmpty()) {
            // Log rejection reason for the primary/first candidate
            val first = candidates.first()
            val reason = reject(cfg, first.card) ?: "rejected"
            val sig = "${first.card.fare}|${first.card.pickupKm}|${first.card.tripKm}|${first.card.type}"
            val now = System.currentTimeMillis()
            if (sig != lastSignature || now - lastHandledAt >= 8000) {
                lastSignature = sig
                lastHandledAt = now
                log(pkg, first.card, false, reason)
            }
            return
        }

        // 3. Choose the best matching candidate (prefer closest pickup distance)
        val winner = passing.minByOrNull { it.card.pickupKm ?: Double.MAX_VALUE } ?: passing.first()
        val signature = "${winner.card.fare}|${winner.card.pickupKm}|${winner.card.tripKm}|${winner.card.type}|${winner.card.isBoost}"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastHandledAt < 8000) return
        lastSignature = signature
        lastHandledAt = now

        // 4. Trigger accept action
        main.postDelayed({
            val r = rootInActiveWindow
            if (winner.button != null) {
                var ok = clickable(winner.button)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                if (!ok && r != null) {
                    val fallback = findAccept(r, cfg.acceptTexts)
                    ok = clickable(fallback)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }
                log(pkg, winner.card, ok, if (ok) "accepted" else "click failed")
                if (ok) RideFeedback.accepted(this, cfg, winner.card)
            } else if (winner.sliderRect != null) {
                val rect = r?.let { findSlider(it, cfg.sliderTexts) } ?: winner.sliderRect
                swipe(rect) { ok ->
                    log(pkg, winner.card, ok, if (ok) "accepted (slider)" else "swipe failed")
                    if (ok) RideFeedback.accepted(this, cfg, winner.card)
                }
            }
        }, cfg.delayMs)
    }

    private fun reject(c: RideConfig, card: RideCard): String? {
        if (card.isBoost && !c.acceptBoost) return "boost ride off"
        card.type?.let { if (c.bookingTypes[it] == false) return "type $it off" }
        val lower = card.text.lowercase()
        c.blockedKeywords.firstOrNull { it.isNotBlank() && lower.contains(it.lowercase()) }
            ?.let { return "blocked: $it" }
        if (c.allowedKeywords.isNotEmpty() && c.allowedKeywords.none { it.isNotBlank() && lower.contains(it.lowercase()) })
            return "outside Go-To area"

        // Fare evaluation: handle unknown fare (e.g. Rapido Bike)
        if (card.fare != null) {
            if (c.minFare > 0 && card.fare < c.minFare) return "fare below min"
        } else {
            if (!c.acceptUnknownFare) return "fare hidden (rejected)"
        }

        if (c.maxPickupKm > 0 && (card.pickupKm ?: 0.0) > c.maxPickupKm) return "pickup too far"
        card.tripKm?.let {
            if (c.minTripKm > 0 && it < c.minTripKm) return "trip too short"
            if (c.maxTripKm > 0 && it > c.maxTripKm) return "trip too long"
            if (card.fare != null && c.minPerKm > 0 && it > 0 && card.fare / it < c.minPerKm) return "₹/km too low"
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
        for (l in labels) {
            val hits = root.findAccessibilityNodeInfosByText(l) ?: continue
            for (hit in hits) {
                val label = (hit.text ?: hit.contentDescription)?.toString()?.trim()
                if (label.equals(l, true)) {
                    clickable(hit)?.let { clickNode ->
                        if (!result.contains(clickNode)) {
                            result.add(clickNode)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun findAccept(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (l in labels) {
            val hit = root.findAccessibilityNodeInfosByText(l)
                ?.firstOrNull {
                    val label = (it.text ?: it.contentDescription)?.toString()?.trim()
                    label.equals(l, true) && clickable(it) != null
                }
            if (hit != null) return hit
        }
        return null
    }

    /**
     * Finds the card ancestor around [anchor] that contains distance info (Km/km/m)
     * without including any sibling accept buttons (for multi-order screens).
     */
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

    /** Bounds of the slider track: the widest ancestor (up to 3 levels) of the "swipe to accept" label that stays inside the screen. */
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
            if (cur.isClickable) return cur
            cur = cur.parent
        }
        return null
    }

    private fun log(pkg: String, c: RideCard, accepted: Boolean, note: String) {
        val e = JSONObject()
            .put("time", System.currentTimeMillis()).put("pkg", pkg)
            .put("fare", c.fare ?: JSONObject.NULL).put("pickupKm", c.pickupKm ?: JSONObject.NULL)
            .put("tripKm", c.tripKm ?: JSONObject.NULL).put("type", c.type ?: JSONObject.NULL)
            .put("accepted", accepted).put("note", note)
        RideHistory.add(this, e)
        RideBridge.emit("ride", e.toString())
    }

    companion object {
        @Volatile var instance: RideAccessibilityService? = null
    }
}
