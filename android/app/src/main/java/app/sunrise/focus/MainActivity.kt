package app.sunrise.focus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * L'écran de Sunrise : l'interface (chambre, petit soleil, pauses…) est la même que sur le web,
 * affichée dans une WebView. Le pont "SunriseAndroid" transmet les règles de blocage au service natif.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#f6eadb")
        window.navigationBarColor = Color.parseColor("#fffaf2")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        web = WebView(this)
        setContentView(web)

        // Les fichiers de l'interface sont servis depuis les assets, sur une adresse https locale.
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setGeolocationEnabled(true)
        web.setBackgroundColor(Color.parseColor("#f6eadb"))

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                showBlockedMessage(intent)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false)
                } else {
                    geoOrigin = origin
                    geoCallback = callback
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
                }
            }
        }

        web.addJavascriptInterface(Bridge(), "SunriseAndroid")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            geoCallback?.invoke(geoOrigin, ok, false)
            geoCallback = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showBlockedMessage(intent)
    }

    override fun onResume() {
        super.onResume()
        // Au retour des réglages d'accessibilité, l'encart "blocage actif" se met à jour.
        web.evaluateJavascript("window.sunriseResume && sunriseResume()", null)
    }

    /** Quand le service vient de fermer une app, on l'explique dans Sunrise. */
    private fun showBlockedMessage(i: Intent?) {
        val name = i?.getStringExtra(EXTRA_BLOCKED) ?: return
        i.removeExtra(EXTRA_BLOCKED)
        web.evaluateJavascript("window.sunriseBlocked && sunriseBlocked(${JSONObject.quote(name)})", null)
    }

    @Deprecated("Navigation retour classique")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    inner class Bridge {
        @JavascriptInterface
        fun sync(json: String) = Rules.save(this@MainActivity, json)

        /** Toutes les apps qu'on peut ouvrir depuis l'écran d'accueil, avec leur nom et une petite icône. */
        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val out = JSONArray()
            val seen = HashSet<String>()
            for (ri in pm.queryIntentActivities(launcher, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val o = JSONObject().put("pkg", pkg).put("name", ri.loadLabel(pm).toString())
                try {
                    val d = ri.loadIcon(pm)
                    val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    d.setBounds(0, 0, 48, 48)
                    d.draw(c)
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    o.put("icon", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                } catch (e: Exception) { }
                out.put(o)
            }
            return out.toString()
        }

        @JavascriptInterface
        fun isEnabled(): Boolean = Rules.serviceEnabled(this@MainActivity)

        @JavascriptInterface
        fun openSettings() {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        @JavascriptInterface
        fun setFocusMode(on: Boolean) = runOnUiThread {
            requestedOrientation =
                if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        const val EXTRA_BLOCKED = "blocked"
        private const val REQ_LOCATION = 7
    }
}
