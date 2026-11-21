package io.github.mpridal.thermocamera.core.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Semantic version MAJOR.MINOR.PATCH (zadání kap. 9), e.g. from tag `v1.3.0`. */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    /** versionCode = major·10000 + minor·100 + patch (zadání kap. 9). */
    val versionCode: Int get() = major * 10000 + minor * 100 + patch

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val RE = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun parse(text: String): SemVer? = RE.matchEntire(text.trim())?.destructured?.let { (a, b, c) ->
            runCatching { SemVer(a.toInt(), b.toInt(), c.toInt()) }.getOrNull()
        }
    }
}

data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** The fields of a GitHub release the updater needs. */
data class Release(
    val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data class Ignore(val reason: String) : UpdateDecision
    data class Download(val version: SemVer, val apk: ReleaseAsset, val sha256: ReleaseAsset) : UpdateDecision
}

/** Pure decision logic of the updater (zadání kap. 9), unit-tested without network. */
object UpdatePolicy {

    fun apkName(v: SemVer) = "thermocamera-$v.apk"

    fun decide(installed: String, release: Release?): UpdateDecision {
        if (release == null) return UpdateDecision.Ignore("no release")
        if (release.draft || release.prerelease) return UpdateDecision.Ignore("draft or pre-release")
        val latest = SemVer.parse(release.tag) ?: return UpdateDecision.Ignore("tag ${release.tag} is not a version")
        val current = SemVer.parse(installed) ?: return UpdateDecision.Ignore("installed version $installed is not a version")
        if (latest <= current) return UpdateDecision.UpToDate
        val apk = release.assets.firstOrNull { it.name == apkName(latest) }
            ?: return UpdateDecision.Ignore("release has no ${apkName(latest)}")
        val sha = release.assets.firstOrNull { it.name == apkName(latest) + ".sha256" }
            ?: return UpdateDecision.Ignore("release has no checksum")
        return UpdateDecision.Download(latest, apk, sha)
    }

    /** First hex token of a `.sha256` file (sha256sum format "hash  filename" or just the hash). */
    fun parseSha256(text: String): String? =
        text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    /**
     * True when the downloaded APK is signed by exactly the same certificates as the installed app
     * (SHA-256 of each certificate, order ignored).
     */
    fun sameSigner(installed: List<ByteArray>, downloaded: List<ByteArray>): Boolean {
        if (installed.isEmpty() || downloaded.isEmpty()) return false
        fun digests(l: List<ByteArray>) = l.map { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }.toSet()
        return digests(installed) == digests(downloaded)
    }
}
