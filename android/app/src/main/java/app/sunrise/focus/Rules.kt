package app.sunrise.focus

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Les règles de blocage, envoyées par l'interface à chaque changement :
 *  - unlocks : app ouverte jusqu'à telle heure grâce à une pause ("phone" = tout le téléphone)
 *  - hard    : blocages sans fin ("phone", une app, ou "custom" pour la sélection)
 *  - apps    : les apps suivies et leurs noms de paquet Android
 */
object Rules {

    data class Verdict(val blocked: Boolean, val name: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sunrise", Context.MODE_PRIVATE)

    fun save(ctx: Context, json: String) {
        prefs(ctx).edit().putString("rules", json).apply()
    }

    fun serviceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.startsWith(ctx.packageName + "/") }
    }

    private fun JSONArray?.containsString(v: String): Boolean {
        if (this == null) return false
        for (i in 0 until length()) if (optString(i) == v) return true
        return false
    }

    /** null = Sunrise ne s'occupe pas de cette app. */
    fun check(ctx: Context, pkg: String): Verdict? {
        val raw = prefs(ctx).getString("rules", null) ?: return null
        val o = try { JSONObject(raw) } catch (e: Exception) { return null }
        val now = System.currentTimeMillis()
        val unlocks = o.optJSONObject("unlocks") ?: JSONObject()
        val hard = o.optJSONObject("hard") ?: JSONObject()
        val custom = o.optJSONArray("custom")
        val apps = o.optJSONArray("apps") ?: JSONArray()
        val phoneOpen = unlocks.optDouble("phone", 0.0) > now && !hard.has("phone")

        for (i in 0 until apps.length()) {
            val a = apps.optJSONObject(i) ?: continue
            if (!a.optJSONArray("pkgs").containsString(pkg)) continue
            val id = a.optString("id")
            val name = a.optString("name", "Cette app")
            val hardBlocked = hard.has(id) || hard.has("phone") || (hard.has("custom") && custom.containsString(id))
            if (hardBlocked) return Verdict(true, name)
            if (phoneOpen || unlocks.optDouble(id, 0.0) > now) return Verdict(false, name)
            return Verdict(true, name) // fermée par défaut, tant qu'on ne prend pas de pause
        }
        // Tout le téléphone bloqué sans fin : toutes les autres apps se ferment aussi.
        if (hard.has("phone")) return Verdict(true, "Ton téléphone")
        return null
    }
}
