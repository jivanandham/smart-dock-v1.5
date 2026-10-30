package com.example.pettwin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns a pet photo into a [PetTwin].
 *
 * 1. If a Gemini API key is available (AI Studio injects GEMINI_API_KEY into BuildConfig when it's
 *    enabled in .env), the photo is sent once to Gemini for a structured description.
 * 2. Otherwise (or if that fails) an on-device fallback samples the photo's colours.
 *
 * The photo is downscaled, used once, and never stored.
 */
class PetTwinAnalyzer(private val context: Context) {

    data class Result(val twin: PetTwin, val usedAi: Boolean, val note: String? = null)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(uri: Uri, name: String): Result = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri, 768) ?: return@withContext Result(PetTwin(enabled = true, name = name), false, "Couldn't read that photo.")
        val key = geminiKey()
        if (key != null) {
            try {
                val twin = analyzeWithGemini(bitmap, key, name)
                if (twin != null) {
                    val note = if (twin.species == "dog") {
                        "Looks like a dog! Dog companions are coming soon, so for now your buddy is a cat in your dog's colours."
                    } else {
                        null
                    }
                    return@withContext Result(twin, true, note)
                }
            } catch (e: Exception) {
                Log.w("PetTwin", "Gemini analysis failed, using on-device colours", e)
            }
        }
        Result(analyzeLocally(bitmap, name), false, "Colours picked on your phone. Fine-tune the markings below.")
    }

    private fun geminiKey(): String? = try {
        // BuildConfig lives in the namespace package (com.example). The field only exists when
        // GEMINI_API_KEY is enabled in .env, so read it reflectively.
        val cls = Class.forName("com.example.BuildConfig")
        (cls.getField("GEMINI_API_KEY").get(null) as? String)?.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
    } catch (_: Throwable) {
        null
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
    }

    private fun analyzeWithGemini(bitmap: Bitmap, key: String, name: String): PetTwin? {
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val prompt = """
            Describe the main pet in this photo for drawing a cute cartoon version. Reply with JSON only:
            {"species":"cat"|"dog"|"other","base_color_hex":"#RRGGBB","secondary_color_hex":"#RRGGBB" or null,
             "eye_color_hex":"#RRGGBB","stripes":0.0-1.0,"white_chest":bool,"white_socks":bool,
             "tuxedo":bool,"dark_points":bool,"patches":bool}
            base = dominant fur colour, secondary = patch colour for calico/tortoiseshell/bicolour,
            stripes = how visible tabby stripes are, dark_points = darker ears/face/tail like a Siamese,
            tuxedo = mostly dark with a white face/chest. If eyes aren't visible, guess a typical colour.
        """.trimIndent()
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
            put("generationConfig", JSONObject().put("response_mime_type", "application/json").put("temperature", 0.2))
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Gemini HTTP ${response.code}")
            val text = JSONObject(response.body?.string().orEmpty())
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                .replace("```json", "").replace("```", "").trim()
            val o = JSONObject(text)
            fun hex(field: String): Int? = o.optString(field, "").takeIf { it.startsWith("#") && it.length == 7 }
                ?.let { android.graphics.Color.parseColor(it) }
            return PetTwin(
                enabled = true,
                species = o.optString("species", "cat").lowercase(),
                baseColor = hex("base_color_hex") ?: return null,
                secondaryColor = hex("secondary_color_hex"),
                eyeColor = hex("eye_color_hex") ?: android.graphics.Color.parseColor("#D5A22A"),
                stripes = o.optDouble("stripes", 0.5).toFloat().coerceIn(0f, 1f),
                whiteChest = o.optBoolean("white_chest"),
                whiteSocks = o.optBoolean("white_socks"),
                tuxedo = o.optBoolean("tuxedo"),
                darkPoints = o.optBoolean("dark_points"),
                patches = o.optBoolean("patches"),
                name = name
            )
        }
    }

    /** On-device fallback: median colour of the photo's centre (where the pet usually is). */
    private fun analyzeLocally(bitmap: Bitmap, name: String): PetTwin {
        val small = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
        val rs = mutableListOf<Int>(); val gs = mutableListOf<Int>(); val bs = mutableListOf<Int>()
        for (y in 10 until 30) for (x in 10 until 30) {
            val p = small.getPixel(x, y)
            rs += android.graphics.Color.red(p); gs += android.graphics.Color.green(p); bs += android.graphics.Color.blue(p)
        }
        fun median(l: MutableList<Int>) = l.sorted()[l.size / 2]
        val base = android.graphics.Color.rgb(median(rs), median(gs), median(bs))
        return PetTwin(enabled = true, baseColor = base, stripes = 0.4f, name = name)
    }

    companion object {
        /** Change here if you prefer another Gemini model. */
        const val MODEL = "gemini-2.5-flash"
    }
}
