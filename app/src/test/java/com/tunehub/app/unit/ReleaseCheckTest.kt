package com.tunehub.app.unit

import com.tunehub.app.data.ReleaseCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReleaseCheckTest {
    @Test
    fun comparesUpstreamBuildTagsNumerically() {
        assertTrue(ReleaseCheck.isNewer("""{"tag_name":"build-100","id":12}""", 99))
        assertFalse(ReleaseCheck.isNewer("""{"tag_name":"build-51"}""", 51))
        assertFalse(ReleaseCheck.isNewer("""{"tag_name":"build-9"}""", 51))
    }

    @Test
    fun supportsLegacyLatestTag() {
        assertTrue(ReleaseCheck.isNewer("""{"tag_name":"latest","name":"TuneHub build 27"}""", 26))
    }

    @Test(expected = IOException::class)
    fun unrecognizedReleaseIsAnError() {
        ReleaseCheck.isNewer("""{"tag_name":"unknown","name":"TuneHub build 52"}""", 51)
    }
}
