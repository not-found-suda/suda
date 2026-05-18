@file:Suppress("TooGenericExceptionCaught")

package com.ssafy.mobile.feature.sign.presentation

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class AnalysisFrameVideoRecorder(
    private val context: Context,
    private val fps: Int = DEFAULT_FPS,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val pendingFrames = AtomicInteger(0)
    private val lock = Any()
    private var session: EncoderSession? = null
    private var completedPath: String? = null
    private var failure: Throwable? = null
    private var cancelled = false

    val isRecording: Boolean
        get() = synchronized(lock) { !cancelled && session != null && failure == null }

    fun record(bitmap: Bitmap) {
        val shouldSkip =
            synchronized(lock) {
                cancelled
            } ||
                failure != null ||
                pendingFrames.get() >= MAX_PENDING_FRAMES
        if (shouldSkip) {
            return
        }
        val frame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        pendingFrames.incrementAndGet()
        executor.execute {
            try {
                getOrCreateSession(frame).encode(frame)
            } catch (exception: Exception) {
                synchronized(lock) {
                    failure = exception
                    session?.releaseQuietly()
                    session = null
                }
            } finally {
                frame.recycle()
                pendingFrames.decrementAndGet()
            }
        }
    }

    fun stop(): String {
        executor.shutdown()
        executor.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        synchronized(lock) {
            val activeSession = session
            session = null
            activeSession?.finish()
            failure?.let { throw it }
            return completedPath ?: activeSession?.outputPath ?: "Movies/S14P31A404-debug"
        }
    }

    fun cancel() {
        executor.shutdown()
        executor.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        synchronized(lock) {
            cancelled = true
            val activeSession = session
            session = null
            activeSession?.discard()
        }
    }

    private fun getOrCreateSession(bitmap: Bitmap): EncoderSession =
        synchronized(lock) {
            failure?.let { throw it }
            check(!cancelled) { "Recording has been cancelled." }
            session
                ?: EncoderSession
                    .create(
                        context = context,
                        width = bitmap.width.toEvenDimension(),
                        height = bitmap.height.toEvenDimension(),
                        fps = fps,
                    ).also {
                        session = it
                        completedPath = it.outputPath
                    }
        }

    private class EncoderSession private constructor(
        private val codec: MediaCodec,
        private val muxer: MediaMuxer,
        private val inputSurface: Surface,
        private val output: MuxerOutput,
        private val width: Int,
        private val height: Int,
        private val fps: Int,
    ) {
        private val bufferInfo = MediaCodec.BufferInfo()
        private var muxerStarted = false
        private var trackIndex = -1
        private var frameIndex = 0L

        val outputPath: String
            get() = output.displayPath

        fun encode(bitmap: Bitmap) {
            drain(endOfStream = false)
            draw(bitmap)
            frameIndex += 1L
        }

        fun finish() {
            runCatching {
                drain(endOfStream = false)
                codec.signalEndOfInputStream()
                drain(endOfStream = true)
            }
            releaseQuietly()
            output.markComplete?.invoke()
        }

        fun discard() {
            releaseQuietly()
            output.discard?.invoke()
        }

        fun releaseQuietly() {
            runCatching { inputSurface.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }

        private fun draw(bitmap: Bitmap) {
            val canvas = inputSurface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                val target = Rect(0, 0, width, height)
                canvas.drawBitmap(bitmap, null, target, null)
            } finally {
                inputSurface.unlockCanvasAndPost(canvas)
            }
        }

        private fun drain(endOfStream: Boolean) {
            while (true) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                    }

                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Output format changed after muxer started." }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    outputBufferIndex >= 0 -> {
                        val encodedData =
                            codec.getOutputBuffer(outputBufferIndex)
                                ?: error("Encoder output buffer is null.")
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0) {
                            check(muxerStarted) { "Muxer has not started." }
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            bufferInfo.presentationTimeUs = frameIndex * MICROS_PER_SECOND / fps
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        val reachedEnd =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if (reachedEnd) return
                    }
                }
            }
        }

        companion object {
            fun create(
                context: Context,
                width: Int,
                height: Int,
                fps: Int,
            ): EncoderSession {
                val output = createMuxerOutput(context)
                val format =
                    MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                        setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, width * height * BITRATE_PER_PIXEL)
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
                    }
                val codec = MediaCodec.createEncoderByType(MIME_TYPE)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surface = codec.createInputSurface()
                codec.start()
                return EncoderSession(
                    codec = codec,
                    muxer = output.muxer,
                    inputSurface = surface,
                    output = output,
                    width = width,
                    height = height,
                    fps = fps,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_FPS = 15
        const val MAX_PENDING_FRAMES = 3
        const val STOP_TIMEOUT_SECONDS = 5L
        const val MIME_TYPE = "video/avc"
        const val BITRATE_PER_PIXEL = 6
        const val I_FRAME_INTERVAL_SECONDS = 1
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MICROS_PER_SECOND = 1_000_000L
        const val RELATIVE_DIRECTORY = "Movies/S14P31A404-debug"
    }
}

private data class MuxerOutput(
    val muxer: MediaMuxer,
    val displayPath: String,
    val markComplete: (() -> Unit)?,
    val discard: (() -> Unit)?,
)

private fun createMuxerOutput(context: Context): MuxerOutput {
    val fileName = "sign_debug_analysis_${System.currentTimeMillis()}.mp4"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/S14P31A404-debug")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
        val descriptor =
            resolver.openFileDescriptor(uri, "w")
                ?: error("Could not open video output descriptor")
        val muxer =
            MediaMuxer(
                descriptor.fileDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
        return MuxerOutput(
            muxer = muxer,
            displayPath = "Movies/S14P31A404-debug/$fileName",
            markComplete = {
                descriptor.close()
                val completeValues =
                    ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                resolver.update(uri, completeValues, null, null)
            },
            discard = {
                runCatching { descriptor.close() }
                resolver.delete(uri, null, null)
            },
        )
    }

    val directory =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "S14P31A404-debug",
        ).apply { mkdirs() }
    val file = File(directory, fileName)
    return MuxerOutput(
        muxer =
            MediaMuxer(
                file.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            ),
        displayPath = file.absolutePath,
        markComplete = null,
        discard = {
            file.delete()
        },
    )
}

private fun Int.toEvenDimension(): Int = if (this % 2 == 0) this else this - 1
