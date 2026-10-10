package com.example.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

enum class VideoQuality(val targetHeight: Int, val targetBitrateMbps: Float) {
    LOW(480, 1.0f),
    MEDIUM(720, 2.5f),
    HIGH(1080, 5.0f)
}

sealed class CompressionProgress {
    data class Progress(val percentage: Int) : CompressionProgress()
    data class Success(val outputFile: File) : CompressionProgress()
    data class Failure(val error: Throwable) : CompressionProgress()
}

class VideoCompressor(private val context: Context) {

    fun compressVideo(
        inputUri: Uri,
        quality: VideoQuality = VideoQuality.MEDIUM
    ): Flow<CompressionProgress> = callbackFlow {
        val outputDir = File(context.cacheDir, "compressed_videos").apply { mkdirs() }
        val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")

        val scaleFactor = quality.targetHeight.toFloat() / 1080f
        val scaleAndRotateTransformation = ScaleAndRotateTransformation.Builder()
            .setScale(scaleFactor, scaleFactor)
            .build()
        val videoEffects = listOf(scaleAndRotateTransformation)

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(CompressionProgress.Success(outputFile))
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    trySend(CompressionProgress.Failure(exportException))
                    close(exportException)
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)

        awaitClose {
            transformer.cancel()
        }
    }
}
