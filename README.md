# AWS S3 Gradle build cache

[![Apache License 2.0](https://img.shields.io/badge/License-Apache%20License%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub version](https://badge.fury.io/gh/myniva%2Fgradle-s3-build-cache.svg)](https://badge.fury.io/gh/myniva%2Fgradle-s3-build-cache)
[![Build status](https://api.travis-ci.org/myniva/gradle-s3-build-cache.svg?branch=develop)](https://travis-ci.org/myniva/gradle-s3-build-cache)

This is a custom Gradle [build cache](https://docs.gradle.org/current/userguide/build_cache.html)
implementation which uses [AWS S3](https://aws.amazon.com/s3/) to store the cache objects.


## Compatibility

* Version >= 0.3.0 - Gradle 4.x
* Version < 0.3.0 - Gradle 3.5

## Use in your project

Please note that this plugin is not yet ready for production. Feedback though is very welcome.
Please open an [issue](https://github.com/myniva/gradle-s3-build-cache/issues) if you find a bug or 
have an idea for an improvement.


### Apply plugin

The Gradle build cache needs to be configured on the Settings level. As a first step, add a
dependency to the plugin to your `settings.gradle` file. Get the latest version from [Gradle plugin portal](https://plugins.gradle.org/plugin/ch.myniva.s3-build-cache).

```
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.ch.myniva.gradle:s3-build-cache:<version>"
  }
}
```

### Configuration

The AWS S3 build cache implementation has a few configuration options:

| Configuration Key | Description | Mandatory | Default Value |
| ----------------- | ----------- | --------- | ------------- |
| `region` | The AWS region the S3 bucket is located in. | yes | |
| `bucket` | The name of the AWS S3 bucket where cache objects should be stored. | yes | |
| `path` | The path under which all cache objects should be stored. | no | |
| `maximumCachedObjectLength` | Maximum object size that can be stored and retrieved from the cache | no | 50'000'000 |
| `reducedRedundancy` | Whether or not to use [reduced redundancy](https://aws.amazon.com/s3/reduced-redundancy/). | no | true |
| `endpoint` | Alternative S3 compatible endpoint | no | |
| `headers` | A map with HTTP headers to be added to each request (nulls are ignored). e.g. `[ 'x-header-name': 'header-value' ]` | no | |
| `awsAccessKeyId` | The AWS access key id | no | `getenv("S3_BUILD_CACHE_ACCESS_KEY_ID")` |
| `awsSecretKey` | The AWS secret key | no | `getenv("S3_BUILD_CACHE_SECRET_KEY")` |
| `sessionToken` | The AWS sessionToken when you use temporal credentials | no | `getenv("S3_BUILD_CACHE_SESSION_TOKEN")` |
| `lookupDefaultAwsCredentials` | Configures if `DefaultAWSCredentialsProviderChain` could be used to lookup credentials | yes | false | 
| `showStatistics` | Displays statistics on the remote cache performance | Yes | `true` |

Note: if both `awsAccessKeyId` and `awsSecretKey` are `nullOrBlank` (`null` or whitespace only), then anonymous credentials are used.

The `buildCache` configuration block might look like this:

Groovy DSL

```gradle
// This goes to settings.gradle

apply plugin: 'ch.myniva.s3-build-cache'

ext.isCiServer = System.getenv().containsKey("CI")

buildCache {
    local {
        enabled = !isCiServer
    }
    remote(ch.myniva.gradle.caching.s3.AwsS3BuildCache) {
        region = 'eu-west-1'
        bucket = 'your-bucket'
        path = 'build-cache'
        push = isCiServer
    }
}
```

Kotlin DSL:

```kotlin
// This goes to settings.gradle.kts

plugins {
    id("ch.myniva.s3-build-cache") version "0.10.0"
}

val isCiServer = System.getenv().containsKey("CI")

buildCache {
    local {
        enabled = !isCiServer
    }
    remote<ch.myniva.gradle.caching.s3.AwsS3BuildCache> {
        region = "eu-west-1"
        bucket = "your-bucket"
        path = "build-cache"
        push = isCiServer
    }
}
```

More details about configuring the Gradle build cache can be found in the
[official Gradle documentation](https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_configure).


### S3 credentials

It is recommended you specify credentials that have limited access to S3 resources, that is why
plugin retrieves credentials from `S3_BUILD_CACHE_ACCESS_KEY_ID`, `S3_BUILD_CACHE_SECRET_KEY`, and `S3_BUILD_CACHE_SESSION_TOKEN`
environment variables.

If you want to use AWS default credentials [`DefaultAWSCredentialsProviderChain`](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html),
then configure `lookupDefaultAwsCredentials=true`.
Note: it will still try `S3_BUILD_CACHE_` variables first.

### S3 Bucket Permissions for cache population

Note: if you use a path prefix (e.g. `build-cache`), you might want to configure the permission to that subfolder only. 
 
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
          "s3:PutObject"
      ],
      "Resource": [
          "arn:aws:s3:::your-bucket/*"
      ]
    }
  ]
}
```

### S3 Bucket Permissions for reading data from the cache

If you use a path prefix (e.g. `build-cache`), you might want to configure the permission to that subfolder only.

Note: if you don't have enough permissions to access the item, it will be treated as "cache miss".  
 
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
          "s3:GetObject"
      ],
      "Resource": [
          "arn:aws:s3:::your-bucket/*"
      ]
    }
  ]
}
```

### Measuring cache efficiency

It is important to measure the efficiency of the caching, otherwise it might happen the caching increases
build time (e.g. it downloads too large artifacts over a slow network or cache misses are too frequent).

Luckily many cache items include the time it took to build the task, so when the item is loaded
from the cache, the time saved can be estimated as `original_task_elapsed_time - from_cache_task_elapsed_time`.

The plugin prints cache statistics at the end of the build (you can disable it with `showStatistics=false`):

```
BUILD SUCCESSFUL in 6s
1 actionable task: 1 executed
S3 cache saved: 0ms, wasted: 233ms, reads: 1, hits: 0, elapsed: 233ms, processed: 0 B
S3 cache writes: 1, elapsed: 121ms, sent to cache: 472 B
```

S3 reads:
* `saved: 0ms` – the estimated time saved by the remote build cache (`original_task_elapsed_time - from_cache_task_elapsed_time`).
Note: this estimation does not account for parallel task execution. Negative estimation means it is
probably faster to build task from scratch rather than download the cached results.
* `wasted: 233ms` – the amount of time spent for `cache misses`
* `reads: 1` – number load requests to the remote cache
* `hits: 0` – number items loaded from the remote cache
* `elapsed: 233ms` – total time spent on loading items from remote cache
* `processed: 0 B` – number of bytes loaded from the remote cache

S3 writes:
* `cache writes: 1` – number of store requests to the remote cache
* `elapsed: 115ms` – time spent uploading items to the remote cache
* `sent to cache: 472 B` – number of bytes sent to the remote cache

### S3 metadata

The stored cache entries might include metadata. The metadata helps to estimate cache efficiency,
and it might be useful to analyze space consumption.

| Key               | Type        |  Sample                    | Description |
| ----------------- | ----------- | -------------------------- | ----------- |
| buildInvocationId | String      | rpha3qmrzvbnxhmdlvukwcx7ru | Build Id |
| identity          | String      | :example:test              | Task identifier |
| executionTime     | Long        | 189871                     | Task execution time, milliseconds |
| operatingSystem   | String      | Linux                      | Operating system |
| gradleVersion     | String      | 6.3                        | Gradle version |

### Expiring cache entries

This plugin does not deal with expiring cache entries directly but relies on S3 object lifecycle management to do so.
Cache entry expiration rules can be set on S3 buckets using [AWS API](https://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketPUTlifecycle.html) or via [AWS Management Console](https://docs.aws.amazon.com/AmazonS3/latest/user-guide/create-lifecycle.html).

## Contributing

Contributions are always welcome! If you'd like to contribute (and we hope you do) please open a pull request.


## License

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
