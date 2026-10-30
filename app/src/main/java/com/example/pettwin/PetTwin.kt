package com.example.pettwin

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.components.CatMarkings
import com.example.ui.components.CatPalette
import com.example.ui.components.paletteFromPet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** A companion built from the user's own pet. Only this description is stored, never the photo. */
data class PetTwin(
    val enabled: Boolean = false,
    /** "cat" or "dog". Dogs are drawn as a cat with the dog's colours until the dog companion ships. */
    val species: String = "cat",
    val baseColor: Int = Color(0xFFE08A3C).toArgb(),
    val secondaryColor: Int? = null,
    val eyeColor: Int = Color(0xFFD5A22A).toArgb(),
    val stripes: Float = 0.6f,
    val whiteChest: Boolean = false,
    val whiteSocks: Boolean = false,
    val tuxedo: Boolean = false,
    val darkPoints: Boolean = false,
    val patches: Boolean = false,
    val name: String = ""
) {
    val palette: CatPalette
        get() = paletteFromPet(Color(baseColor), secondaryColor?.let { Color(it) }, Color(eyeColor))

    val markings: CatMarkings
        get() = CatMarkings(stripes, whiteChest, whiteSocks, tuxedo, darkPoints, patches && secondaryColor != null)

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("species", species); put("baseColor", baseColor)
        secondaryColor?.let { put("secondaryColor", it) }
        put("eyeColor", eyeColor); put("stripes", stripes.toDouble()); put("whiteChest", whiteChest)
        put("whiteSocks", whiteSocks); put("tuxedo", tuxedo); put("darkPoints", darkPoints)
        put("patches", patches); put("name", name)
    }.toString()

    companion object {
        fun fromJson(json: String?): PetTwin? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                PetTwin(
                    enabled = o.optBoolean("enabled", false),
                    species = o.optString("species", "cat"),
                    baseColor = o.optInt("baseColor", Color(0xFFE08A3C).toArgb()),
                    secondaryColor = if (o.has("secondaryColor")) o.getInt("secondaryColor") else null,
                    eyeColor = o.optInt("eyeColor", Color(0xFFD5A22A).toArgb()),
                    stripes = o.optDouble("stripes", 0.6).toFloat().coerceIn(0f, 1f),
                    whiteChest = o.optBoolean("whiteChest"),
                    whiteSocks = o.optBoolean("whiteSocks"),
                    tuxedo = o.optBoolean("tuxedo"),
                    darkPoints = o.optBoolean("darkPoints"),
                    patches = o.optBoolean("patches"),
                    name = o.optString("name", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

private val Context.petTwinStore by preferencesDataStore(name = "pet_twin")

class PetTwinRepository(private val context: Context) {
    private val key = stringPreferencesKey("pet_twin_json")
    val twinFlow: Flow<PetTwin?> = context.petTwinStore.data.map { PetTwin.fromJson(it[key]) }

    suspend fun save(twin: PetTwin) {
        context.petTwinStore.edit { it[key] = twin.toJson() }
    }
}
