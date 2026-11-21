package io.github.mpridal.thermocamera.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.BuildConfig
import io.github.mpridal.thermocamera.core.update.Release
import io.github.mpridal.thermocamera.core.update.ReleaseAsset
import io.github.mpridal.thermocamera.core.update.SemVer
import io.github.mpridal.thermocamera.core.update.UpdateDecision
import io.github.mpridal.thermocamera.core.update.UpdatePolicy
import io.github.mpridal.thermocamera.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloaded(val version: String, val file: File) : UpdateState
    data object Installing : UpdateState
}

/**
 * Update from GitHub Releases (zadání kap. 9). Runs once per app start in the background, never
 * blocks the UI and is silent without a validated network. The only network code in the app.
 */
@Singleton
class Updater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var started = false

    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private val logFile get() = File(context.filesDir, "update-log.txt")

    fun log(msg: String) {
        Log.i(TAG, msg)
        runCatching {
            val f = logFile
            if (f.length() > 64_000) f.writeText(f.readLines().takeLast(200).joinToString("\n") + "\n")
            f.appendText("${LocalDateTime.now()} $msg\n")
        }
    }

    fun readLog(): String = runCatching { logFile.readText() }.getOrDefault("")

    /** Called at app start; does nothing when disabled, offline or (if set) not on Wi-Fi. */
    fun checkOnStart() {
        if (started) return
        started = true
        scope.launch {
            val s = settings.settings.first()
            if (!s.checkUpdates) return@launch
            if (!online(wifiRequired = s.updatesWifiOnly)) return@launch
            runCatching { check() }.onFailure { log("check failed: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun online(wifiRequired: Boolean): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false
        return !wifiRequired || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun check() {
        val release = fetchLatest()
        when (val d = UpdatePolicy.decide(BuildConfig.VERSION_NAME, release)) {
            UpdateDecision.UpToDate -> cleanup(keep = null)
            is UpdateDecision.Ignore -> log("ignored: ${d.reason}")
            is UpdateDecision.Download -> download(d.version, d.apk, d.sha256)
        }
    }

    private fun fetchLatest(): Release? {
        val c = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000; readTimeout = 5_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ThermoCamera-updater")
        }
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) { log("releases/latest HTTP ${c.responseCode}"); return null }
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.optJSONArray("assets")
            return Release(
                tag = o.optString("tag_name"),
                draft = o.optBoolean("draft"),
                prerelease = o.optBoolean("prerelease"),
                assets = (0 until (assets?.length() ?: 0)).map { i ->
                    val a = assets!!.getJSONObject(i)
                    ReleaseAsset(a.optString("name"), a.optString("browser_download_url"), a.optLong("size"))
                },
            )
        } finally {
            c.disconnect()
        }
    }

    private fun download(version: SemVer, apk: ReleaseAsset, shaAsset: ReleaseAsset) {
        val target = File(dir, UpdatePolicy.apkName(version))
        val expected = UpdatePolicy.parseSha256(httpText(shaAsset.url)) ?: run { log("bad checksum file"); return }
        cleanup(keep = target)
        // Do not download again when already complete; resume when interrupted (Range).
        if (!(target.exists() && target.length() == apk.size && UpdatePolicy.sha256(target) == expected)) {
            httpDownload(apk.url, target)
        }
        val actual = UpdatePolicy.sha256(target)
        if (actual != expected) {
            log("SHA-256 mismatch for ${target.name}: $actual != $expected – deleted")
            target.delete()
            return
        }
        if (!sameSignerAsInstalled(target)) {
            log("signing certificate differs for ${target.name} – deleted")
            target.delete()
            return
        }
        log("downloaded and verified ${target.name}")
        _state.value = UpdateState.Downloaded(version.toString(), target)
    }

    private fun httpText(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 5_000; readTimeout = 10_000; instanceFollowRedirects = true }
        try { return c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    private fun httpDownload(url: String, target: File) {
        val have = if (target.exists()) target.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 30_000; instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val resume = have > 0 && c.responseCode == 206
            RandomAccessFile(target, "rw").use { raf ->
                if (resume) raf.seek(have) else raf.setLength(0)
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    /** Removes old or partial downloads except [keep]. */
    private fun cleanup(keep: File?) {
        dir.listFiles()?.filter { it != keep }?.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo?): List<ByteArray> {
        info ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { si -> (if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory)?.map { it.toByteArray() } }.orEmpty()
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }
    }

    @Suppress("DEPRECATION")
    private fun sameSignerAsInstalled(apk: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = signatures(pm.getPackageInfo(context.packageName, flags))
        val downloaded = signatures(pm.getPackageArchiveInfo(apk.path, flags))
        val pkgOk = pm.getPackageArchiveInfo(apk.path, 0)?.packageName == context.packageName
        // Current signer must match (history may contain older keys of the installed app).
        return pkgOk && downloaded.isNotEmpty() && downloaded.all { d -> installed.any { it.contentEquals(d) } }
    }

    /** True when Android lets this app install packages ("Install unknown apps"). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Installs the downloaded APK through a PackageInstaller session (zadání kap. 9 bod 5). */
    fun install() {
        val s = _state.value as? UpdateState.Downloaded ?: return
        _state.value = UpdateState.Installing
        scope.launch {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, s.file.length()).use { out -> s.file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
                    session.commit(pi.intentSender)
                }
                log("install session committed for ${s.file.name}")
            }.onFailure {
                log("install failed: ${it.message}")
                _state.value = s
            }
        }
    }

    fun onInstallFinished(ok: Boolean, message: String?) {
        log("install result ok=$ok ${message.orEmpty()}")
        if (!ok) (_state.value as? UpdateState.Installing)?.let { _state.value = UpdateState.Idle }
    }

    companion object {
        private const val TAG = "Updater"
    }
}

/** Receives the PackageInstaller result; starts the system confirmation when the user must approve. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, UpdaterEntryPoint::class.java)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> entry.updater().onInstallFinished(true, null)
            else -> entry.updater().onInstallFinished(false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface UpdaterEntryPoint {
    fun updater(): Updater
}
