package sh.lsx.untisx.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodebergReleaseTest {

    @Test
    fun `selects phone APK regardless of asset order`() {
        val phone = CodebergAsset("app-release.apk", "https://codeberg.org/phone.apk")
        val watch = CodebergAsset("wear-release.apk", "https://codeberg.org/watch.apk")

        for (assets in listOf(listOf(watch, phone), listOf(phone, watch))) {
            assertEquals(phone, CodebergRelease("v0.7.0", assets = assets).phoneApk())
        }
    }

    @Test
    fun `does not use another APK when phone APK is missing`() {
        val release = CodebergRelease(
            "v0.7.0",
            assets = listOf(CodebergAsset("wear-release.apk", "https://codeberg.org/watch.apk")),
        )

        assertNull(release.phoneApk())
        assertNull(CodebergRelease("v0.7.0").phoneApk())
    }
}
