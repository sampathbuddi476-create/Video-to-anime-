package com.example.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class StylizeProgress {
    data class Uploading(val progressPercent: Int, val bytesWritten: Long, val totalBytes: Long) : StylizeProgress()
    object RenderingOnGpu : StylizeProgress()
    data class Downloading(val progressPercent: Int, val bytesRead: Long, val totalBytes: Long) : StylizeProgress()
    object Completed : StylizeProgress()
}

data class ServerHealth(
    val isOnline: Boolean,
    val statusCode: Int = 0,
    val message: String = "",
    val latencyMs: Long = 0
)

class ColabApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeCall: Call? = null

    /**
     * Cleans and formats the base URL (ensures http/https and no trailing slashes)
     */
    fun sanitizeUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed.trimEnd('/')
    }

    /**
     * GET {BASE_URL}/ -> Verifies connection status
     */
    suspend fun checkServerHealth(baseUrl: String): ServerHealth = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(baseUrl)
        if (cleanUrl.isBlank()) {
            return@withContext ServerHealth(
                isOnline = false,
                message = "Please enter a valid server URL"
            )
        }

        val request = Request.Builder()
            .url("$cleanUrl/")
            .header("User-Agent", "TitanAnime-Android/1.0")
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val bodyStr = response.body?.string()?.take(200) ?: ""
                val isSuccess = response.isSuccessful || response.code in 200..399

                ServerHealth(
                    isOnline = isSuccess,
                    statusCode = response.code,
                    message = if (isSuccess) {
                        if (bodyStr.isNotBlank()) "Online: $bodyStr" else "Online (HTTP ${response.code})"
                    } else {
                        "Error HTTP ${response.code}: ${response.message}"
                    },
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            ServerHealth(
                isOnline = false,
                statusCode = 0,
                message = e.localizedMessage ?: "Connection failed",
                latencyMs = 0
            )
        }
    }

    /**
     * POST {BASE_URL}/stylize -> Uploads source video as multipart form-data (field name: video).
     * Streams back the completed MP4 binary directly to context.cacheDir.
     */
    suspend fun stylizeVideo(
        context: Context,
        baseUrl: String,
        videoFile: File,
        mimeType: String = "video/mp4",
        onProgress: (StylizeProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(baseUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        if (!videoFile.exists() || videoFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Source video file not found or empty"))
        }

        val totalFileLength = videoFile.length()
        val mediaType = mimeType.toMediaTypeOrNull()

        // Create file request body with progress tracking
        val baseFileBody = object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength() = totalFileLength

            override fun writeTo(sink: BufferedSink) {
                videoFile.inputStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var totalWritten = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        totalWritten += read
                        val percent = if (totalFileLength > 0) {
                            ((totalWritten * 100) / totalFileLength).toInt().coerceIn(0, 100)
                        } else 0

                        onProgress(
                            StylizeProgress.Uploading(
                                progressPercent = percent,
                                bytesWritten = totalWritten,
                                totalBytes = totalFileLength
                            )
                        )
                    }
                }
            }
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "video",
                filename = videoFile.name,
                body = baseFileBody
            )
            .build()

        val request = Request.Builder()
            .url("$cleanUrl/stylize")
            .header("User-Agent", "TitanAnime-Android/1.0")
            .post(multipartBody)
            .build()

        val call = client.newCall(request)
        activeCall = call

        try {
            // Signal transition to GPU rendering once request is dispatched
            val response = call.execute()
            onProgress(StylizeProgress.RenderingOnGpu)

            if (!response.isSuccessful) {
                val errorMsg = response.body?.string()?.take(300) ?: "HTTP ${response.code}"
                return@withContext Result.failure(
                    IOException("Stylization failed: HTTP ${response.code} ($errorMsg)")
                )
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(IOException("Empty response body from server"))

            // Stream MP4 directly to context.cacheDir
            val timestamp = System.currentTimeMillis()
            val outputFile = File(context.cacheDir, "stylized_anime_$timestamp.mp4")
            val totalDownloadLength = responseBody.contentLength()

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val percent = if (totalDownloadLength > 0) {
                            ((totalRead * 100) / totalDownloadLength).toInt().coerceIn(0, 100)
                        } else -1

                        onProgress(
                            StylizeProgress.Downloading(
                                progressPercent = percent,
                                bytesRead = totalRead,
                                totalBytes = totalDownloadLength
                            )
                        )
                    }
                    outputStream.flush()
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext Result.failure(IOException("Downloaded MP4 file is empty or corrupted"))
            }

            onProgress(StylizeProgress.Completed)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeCall = null
        }
    }

    fun cancelActiveRequest() {
        activeCall?.cancel()
        activeCall = null
    }
}
