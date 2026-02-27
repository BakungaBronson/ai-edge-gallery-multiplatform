/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.network

import com.google.ai.edge.gallery.common.JsonObjAndTextContent
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@PublishedApi
internal const val TAG = "AGHttpClient"

/** Shared JSON configuration for deserialization. */
val appJson = Json { ignoreUnknownKeys = true }

/** Creates a configured Ktor HttpClient. */
fun createHttpClient(): HttpClient {
  return HttpClient {
    install(ContentNegotiation) {
      json(appJson)
    }
  }
}

/** Fetches JSON from a URL and deserializes it. */
suspend inline fun <reified T> getJsonResponse(url: String): JsonObjAndTextContent<T>? {
  return try {
    val client = createHttpClient()
    val response = client.get(url)
    client.close()
    if (response.status == HttpStatusCode.OK) {
      val text = response.bodyAsText()
      val jsonObj = appJson.decodeFromString<T>(text)
      JsonObjAndTextContent(jsonObj = jsonObj, textContent = text)
    } else {
      Napier.e(tag = TAG) { "HTTP error: ${response.status}" }
      null
    }
  } catch (e: Exception) {
    Napier.e(tag = TAG) { "Error when getting json response: ${e.message}" }
    null
  }
}

/** Checks the HTTP response code for a URL, optionally with an authorization token. */
suspend fun getUrlResponseCode(url: String, accessToken: String? = null): Int {
  return try {
    val client = createHttpClient()
    val response = client.get(url) {
      if (accessToken != null) {
        header("Authorization", "Bearer $accessToken")
      }
    }
    client.close()
    response.status.value
  } catch (e: Exception) {
    Napier.e(tag = TAG) { "Error checking URL response: ${e.message}" }
    -1
  }
}
