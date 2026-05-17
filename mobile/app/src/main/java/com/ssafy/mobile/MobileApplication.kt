package com.ssafy.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ssafy.mobile.feature.learning.data.repository.LearningQuizAnswerSubmissionQueueSyncer
import com.ssafy.mobile.translation.OnDeviceModelDebugBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobileApplication :
    Application(),
    ImageLoaderFactory {
    @Inject
    lateinit var learningQuizAnswerSubmissionQueueSyncer: LearningQuizAnswerSubmissionQueueSyncer

    override fun onCreate() {
        super.onCreate()
        OnDeviceModelDebugBootstrapper.prepareBundledDebugModel(this)
        learningQuizAnswerSubmissionQueueSyncer.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .memoryCache {
                MemoryCache
                    .Builder(this)
                    .maxSizePercent(IMAGE_MEMORY_CACHE_PERCENT)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve(IMAGE_DISK_CACHE_DIR))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }.crossfade(true)
            .build()

    private companion object {
        const val IMAGE_MEMORY_CACHE_PERCENT = 0.25
        const val IMAGE_DISK_CACHE_BYTES = 80L * 1024L * 1024L
        const val IMAGE_DISK_CACHE_DIR = "image_cache"
    }
}
