package io.github.mpridal.thermocamera.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, withSha: Boolean = true): Release {
        val v = tag.removePrefix("v")
        val assets = mutableListOf(ReleaseAsset("thermocamera-$v.apk", "https://x/apk", 10))
        if (withSha) assets += ReleaseAsset("thermocamera-$v.apk.sha256", "https://x/sha", 1)
        return Release(tag, draft, pre, assets)
    }

    @Test fun semverParsingAndOrder() {
        assertEquals(SemVer(1, 3, 0), SemVer.parse("v1.3.0"))
        assertEquals(SemVer(1, 3, 0), SemVer.parse("1.3.0"))
        assertNull(SemVer.parse("1.3"))
        assertNull(SemVer.parse("v1.3.0-beta"))
        assertTrue(SemVer(1, 10, 0) > SemVer(1, 9, 9))
        assertEquals(10300, SemVer(1, 3, 0).versionCode)
    }

    @Test fun newerReleaseIsDownloaded() {
        val d = UpdatePolicy.decide("1.0.0", release("v1.0.1"))
        assertTrue(d is UpdateDecision.Download)
        d as UpdateDecision.Download
        assertEquals("thermocamera-1.0.1.apk", d.apk.name)
        assertEquals("thermocamera-1.0.1.apk.sha256", d.sha256.name)
    }

    @Test fun sameOrOlderIsUpToDate() {
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide("1.0.1", release("v1.0.1")))
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide("1.2.0", release("v1.0.9")))
    }

    @Test fun draftsPreReleasesAndIncompleteReleasesAreIgnored() {
        assertTrue(UpdatePolicy.decide("1.0.0", release("v2.0.0", draft = true)) is UpdateDecision.Ignore)
        assertTrue(UpdatePolicy.decide("1.0.0", release("v2.0.0", pre = true)) is UpdateDecision.Ignore)
        assertTrue(UpdatePolicy.decide("1.0.0", release("v2.0.0", withSha = false)) is UpdateDecision.Ignore)
        assertTrue(UpdatePolicy.decide("1.0.0", release("nightly")) is UpdateDecision.Ignore)
        assertTrue(UpdatePolicy.decide("1.0.0", null) is UpdateDecision.Ignore)
    }

    @Test fun sha256FileParsing() {
        val h = "a".repeat(64)
        assertEquals(h, UpdatePolicy.parseSha256("$h  thermocamera-1.0.1.apk\n"))
        assertEquals(h, UpdatePolicy.parseSha256(h.uppercase()))
        assertNull(UpdatePolicy.parseSha256("not a hash"))
    }

    @Test fun sha256OfKnownInput() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdatePolicy.sha256("abc".byteInputStream()),
        )
    }

    @Test fun signerComparison() {
        val a = byteArrayOf(1, 2, 3); val b = byteArrayOf(4, 5, 6)
        assertTrue(UpdatePolicy.sameSigner(listOf(a), listOf(a.copyOf())))
        assertFalse(UpdatePolicy.sameSigner(listOf(a), listOf(b)))
        assertFalse(UpdatePolicy.sameSigner(emptyList(), listOf(a)))
        assertTrue(UpdatePolicy.sameSigner(listOf(a, b), listOf(b, a)))
    }
}
