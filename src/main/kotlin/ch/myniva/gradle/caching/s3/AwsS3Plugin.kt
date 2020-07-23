/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.myniva.gradle.caching.s3

import ch.myniva.gradle.caching.s3.internal.AwsS3BuildCacheServiceFactory
import ch.myniva.gradle.caching.s3.internal.CURRENT_TASK
import ch.myniva.gradle.caching.s3.internal.TaskPerformanceInfo
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskState

private val logger = Logging.getLogger(AwsS3Plugin::class.java)

class AwsS3Plugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        logger.info("Registering S3 build cache")
        settings.buildCache.registerBuildCacheService(
            AwsS3BuildCache::class.java,
            AwsS3BuildCacheServiceFactory::class.java
        )
        settings.gradle.taskGraph.addTaskExecutionListener(object : TaskExecutionListener {
            override fun beforeExecute(task: Task) {
                CURRENT_TASK.set(TaskPerformanceInfo(task.path, System.currentTimeMillis()))
            }

            override fun afterExecute(task: Task, state: TaskState) {
                CURRENT_TASK.get()?.run {
                    val executionFinished = System.currentTimeMillis()
                    val taskDuration = executionFinished - executionStarted
                    when (state.skipMessage) {
                        "FROM-CACHE" ->
                            cacheLoadSavingsStopwatch?.increment(
                                (metadata?.executionTime ?: 0) - taskDuration
                            )
                        null ->
                            cacheLoadWasteStopwatch?.increment(
                                cacheLoadDuration ?: 0
                            )
                    }
                    Unit
                }
                // Avoid memory leaks
                CURRENT_TASK.remove()
            }
        })
    }
}
