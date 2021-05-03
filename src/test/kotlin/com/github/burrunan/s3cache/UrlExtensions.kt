/*
 * Copyright 2020-2021 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.github.burrunan.s3cache

import java.io.File
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Paths

val URL.asFile: File
    get() {
        if ("file" != protocol) {
            throw IllegalArgumentException("URL protocol must be file, got $protocol")
        }
        val uri = try {
            toURI()
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("Unable to convert URL $this to URI", e)
        }
        return if (uri.isOpaque) {
            // It is like file:test%20file.c++
            // getSchemeSpecificPart would return "test file.c++"
            File(uri.schemeSpecificPart)
        } else {
            // See https://stackoverflow.com/a/17870390/1261287
            Paths.get(uri).toFile()
        }
    }
