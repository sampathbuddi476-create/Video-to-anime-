package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.ColabApiService
import com.example.util.VideoUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Titan Anime", appName)
  }

  @Test
  fun `verify url sanitization`() {
    val apiService = ColabApiService()
    assertEquals(
      "https://tunnel.trycloudflare.com",
      apiService.sanitizeUrl("tunnel.trycloudflare.com")
    )
    assertEquals(
      "https://tunnel.trycloudflare.com",
      apiService.sanitizeUrl("https://tunnel.trycloudflare.com/")
    )
  }

  @Test
  fun `verify video duration formatting`() {
    assertEquals("00:15", VideoUtils.formatDuration(15000))
    assertEquals("01:30", VideoUtils.formatDuration(90000))
  }
}
