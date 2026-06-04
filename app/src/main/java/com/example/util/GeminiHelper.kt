package com.example.util

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiHelper {
    private const val TAG = "GeminiHelper"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Converts a Bitmap into a Base64-encoded string for the Gemini API inlineData block.
     */
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Compress slightly to prevent payload-size issues on network calls while preserving details
        compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Performs direct, multimodal Gemini OCR on an image bitmap, extracting and formatting text.
     */
    suspend fun performOcr(bitmap: Bitmap, prompt: String = "Extract all text from this scanned document clearly. Preserve formatting and paragraphs. Do not add conversational comments."): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or placeholder!")
            return@withContext "Error: Gemini API Key is missing. Please configure GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        try {
            val base64Image = bitmap.toBase64()

            // Construct requests with org.json for absolute robustness
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            // Part 1: Text Prompt
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Part 2: Inline Base64 image
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorMsg = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini API failed: $code - $errorMsg")
                    return@withContext "Error: Gemini API returned status code $code"
                }

                val responseString = response.body?.string() ?: return@withContext "Error: Empty response body"
                val jsonResponse = JSONObject(responseString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "No text found in candidate parts.")
                        }
                    }
                }
                return@withContext "Error: Could not parse text chunks from Gemini response."
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR exception: ", e)
            return@withContext "Error processing OCR: ${e.localizedMessage}"
        }
    }

    /**
     * Scans specifically an ID card (CNIC, Passport, Driver License, Student ID) 
     * and extracts formatted profiles.
     */
    suspend fun performIdCardOcr(bitmap: Bitmap, cardType: String): String {
        val prompt = when (cardType) {
            "CNIC" -> "This is a Pakistan National Identity Card (CNIC). Extract key info: Full Name, Father/Husband Name, Gender, CNIC Number, Date of Birth, Issue Date, Expiry Date. Return them as clean, well-formatted markdown bullet points."
            "Passport" -> "This is a Passport document page. Extract key info: Country Code, Passport Number, Surname, Given Names, Nationality, Date of Birth, Gender, Place of Birth, Date of Issue, Date of Expiry, Issuing Authority. Return them as clean, well-formatted markdown bullet points."
            "Driving License" -> "This is a Driving License. Extract key info: Complete Name, Father's Name, License Number, Allowed Vehicles, Date of Issue, Date of Expiry. Return them as clean, well-formatted markdown bullet points."
            else -> "This is an Identification/Student Card. Extract key info: Organization/School Name, Name, Card/ID Number, Designation/Class, Issue/Expiry dates. Return them as clean, well-formatted markdown bullet points."
        }
        return performOcr(bitmap, prompt)
    }
}
