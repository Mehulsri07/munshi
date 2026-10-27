package com.opuscube.munshi.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpMunshiApi(private val baseUrl: String, private val key: String, private val client: OkHttpClient) : MunshiApi {
    private val jsonType = "application/json".toMediaType()

    private suspend inline fun <reified T> call(method: String, path: String, body: String? = null): T = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path).header("x-demo-key", key)
            .method(method, body?.toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { MunshiJson.parseToJsonElement(text).jsonObject["error"]!!.jsonPrimitive.content }.getOrNull()
                throw ApiError(res.code, msg ?: res.message)
            }
            MunshiJson.decodeFromString(serializer<T>(), text)
        }
    }

    override suspend fun brief(): Brief = call("GET", "/api/brief")
    override suspend fun next(scope: String, interrupt: Boolean): Next =
        call("POST", "/api/next", buildJsonObject { put("scope", JsonPrimitive(scope)); put("interrupt", JsonPrimitive(interrupt)) }.toString())
    override suspend fun confirm(id: Long): ActionResult = call("POST", "/api/opportunities/$id/confirm", "{}")
    override suspend fun decline(id: Long): ActionResult = call("POST", "/api/opportunities/$id/decline", "{}")
    override suspend fun converse(text: String): ActionResult =
        call("POST", "/api/converse", buildJsonObject { put("text", JsonPrimitive(text)) }.toString())
    override suspend fun facts(): Facts = call("GET", "/api/facts")
    override suspend fun propose(khataId: Long): Proposed =
        call("POST", "/api/propose", buildJsonObject { put("kind", JsonPrimitive("udhaar_reminder")); put("khataId", JsonPrimitive(khataId)) }.toString())
    override suspend fun shelfReadings(readings: List<Reading>): ShelfResult =
        call("POST", "/api/shelf/readings", buildJsonObject { put("readings", MunshiJson.encodeToJsonElement(readings)) }.toString())
    override suspend fun ledger(): Ledger = call("GET", "/api/ledger")
    override suspend fun lookup(phone: String): Profile =
        call<ProfileReply>("POST", "/api/onboard/lookup", buildJsonObject { put("phone", JsonPrimitive(phone)) }.toString()).profile
    override suspend fun merchant(): Profile = call<ProfileReply>("GET", "/api/merchant").profile
    override suspend fun saveMerchant(p: Profile): Profile =
        call<ProfileReply>("POST", "/api/merchant", MunshiJson.encodeToString(Profile.serializer(), p)).profile
}
