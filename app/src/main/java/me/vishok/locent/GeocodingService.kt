package me.vishok.locent

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeocodingService {

    suspend fun searchLocation(query: String): List<Pair<LatLng, String>> =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")

                val url =
                    URL("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1")

                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "LocentApp/1.0 (me.vishok.locent)")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    return@withContext emptyList()
                }

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONArray(json)
                val locations = mutableListOf<Pair<LatLng, String>>()

                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    val lat = obj.getString("lat").toDouble()
                    val lon = obj.getString("lon").toDouble()
                    val displayName = obj.getString("display_name")
                    locations.add(LatLng(lat, lon) to displayName)
                }
                locations
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "LocentApp/1.0 (me.vishok.locent)")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode != 200) return@withContext null
                
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                val address = obj.optString("display_name")
                if (address.isEmpty()) null else address
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}