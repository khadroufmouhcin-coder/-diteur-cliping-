package com.example

import com.example.data.YouTubeService
import com.example.model.CaptionStyle
import com.example.model.StudioToolMode
import com.example.model.TimeRangeSec
import com.example.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    private val service = YouTubeService()

    @Test
    fun extractVideoId_handlesAllYouTubeFormats() {
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("jfKfPfyJRdk", service.extractVideoId("https://www.youtube.com/live/jfKfPfyJRdk?feature=share"))
        assertEquals("abc123XYZ_-", service.extractVideoId("https://youtube.com/shorts/abc123XYZ_-"))
        assertEquals("", service.extractVideoId("https://example.com/not-youtube"))
    }

    @Test
    fun v2StudioModels_exposeAllModesStylesAndTransitions() {
        assertEquals(4, StudioToolMode.entries.size)
        assertEquals(CaptionStyle.KARAOKE, CaptionStyle.fromName("KARAOKE"))
        assertEquals(6, TransitionType.entries.size)
        val range = TimeRangeSec(2.5, 6.0)
        assertEquals(3.5, range.lengthSec, 0.001)
        assertTrue(TimeRangeSec(5.0, 2.0).lengthSec == 0.0)
    }
}
