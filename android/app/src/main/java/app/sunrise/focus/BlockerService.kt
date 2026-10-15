package app.sunrise.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Le service qui ferme vraiment les apps : à chaque changement d'app au premier plan,
 * il regarde les règles de Sunrise. Si l'app est fermée, il revient à l'accueil et ouvre Sunrise.
 * Il ne lit aucun contenu : seulement le nom du paquet de l'app ouverte.
 */
class BlockerService : AccessibilityService() {

    private var lastPkg = ""
    private var lastTime = 0L

    // Jamais bloqués, même en "tout le téléphone" : pour ne jamais se retrouver coincé·e.
    private val alwaysAllowed = setOf(
        "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer",
        "com.android.emergency", "com.google.android.apps.safetyhub", "com.android.incallui",
        "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in alwaysAllowed || isLauncher(pkg)) return

        val verdict = Rules.check(this, pkg) ?: return
        if (!verdict.blocked) return

        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastTime < 1200) return // évite les doubles événements
        lastPkg = pkg; lastTime = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_BLOCKED, verdict.name)
        )
    }

    private val launchers: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun isLauncher(pkg: String) = pkg in launchers

    override fun onInterrupt() {}
}
