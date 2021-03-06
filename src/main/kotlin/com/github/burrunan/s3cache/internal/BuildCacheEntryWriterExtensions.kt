package com.github.burrunan.s3cache.internal

import org.gradle.caching.BuildCacheEntryWriter
import java.io.File

fun BuildCacheEntryWriter.file(): File? = try {
    this::class.java.getDeclaredField("file").run {
        isAccessible = true
        get(this@file) as? File
    }
} catch(ignore: Throwable) {
    null
}
