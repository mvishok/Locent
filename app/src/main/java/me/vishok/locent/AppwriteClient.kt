package me.vishok.locent

import android.content.Context
import androidx.activity.ComponentActivity
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Functions
import io.appwrite.models.User
import io.appwrite.models.DocumentList
import io.appwrite.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object AppwriteClient {
    private lateinit var client: Client
    private lateinit var account: Account
    private lateinit var functions: Functions
    private lateinit var databases: Databases
    
    private const val ENDPOINT = "https://sgp.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "69cd4d84000f35754d49"
    private const val FUNCTION_ID = "69d3bcab0001e690af54"

    private val _currentUser = MutableStateFlow<User<Map<String, Any>>?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _history = MutableStateFlow<List<io.appwrite.models.Document<Map<String, Any>>>>(emptyList())
    val history = _history.asStateFlow()

    fun init(context: Context) {
        client = Client(context)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
            .setSelfSigned(true)
        
        account = Account(client)
        functions = Functions(client)
        databases = Databases(client)
    }

    suspend fun checkSession(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val user = account.get()
                _currentUser.value = user
                true
            } catch (e: Exception) {
                _currentUser.value = null
                false
            }
        }
    }

    suspend fun loginWithGoogle(activity: ComponentActivity): String {
        return withContext(Dispatchers.Main) {
            try {
                account.createOAuth2Session(
                    activity = activity,
                    provider = io.appwrite.enums.OAuthProvider.GOOGLE
                )
                ""
            } catch (e: Exception) {
                android.util.Log.e("Locent", "OAuth Error: ${e.message}")
                e.message ?: "Unknown Error"
            }
        }
    }

    suspend fun logout(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                account.deleteSession("current")
                _currentUser.value = null
                true
            } catch (e: Exception) {
                android.util.Log.e("Locent", "Logout Error: ${e.message}")
                false
            }
        }
    }

    suspend fun getHistory(): DocumentList<Map<String, Any>>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = databases.listDocuments(
                    databaseId = "69d121b80034f5b3c4b6", // Your DB ID
                    collectionId = "history",           // Your Collection ID
                    queries = listOf(
                        Query.equal("userId", currentUser.value?.id ?: ""), 
                        Query.orderDesc("timestamp")
                    )
                )
                
                _history.value = response.documents
                response
            } catch (e: Exception) {
                android.util.Log.e("Locent", "SDK History Error: ${e.message}")
                null
            }
        }
    }

    suspend fun deleteHistoryItems(documentIds: List<String>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // We use async/awaitAll to delete multiple items in parallel directly via SDK
                documentIds.map { id ->
                    async {
                        databases.deleteDocument(
                            databaseId = "69d121b80034f5b3c4b6",
                            collectionId = "history",
                            documentId = id
                        )
                    }
                }.awaitAll()

                // Update local state flow to remove deleted items from UI
                _history.update { currentList -> 
                    currentList.filter { !documentIds.contains(it.id) } 
                }
                true
            } catch (e: Exception) {
                android.util.Log.e("Locent", "Direct SDK Delete Error: ${e.message}")
                false
            }
        }
    }

    /**
     * PURE SYNC SEARCH (SDK-BYPASS)
     * Using raw OkHttp to ensure 60s timeout.
     */
    suspend fun executeSearch(lat: Double, lng: Double, query: String, radius: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("query", query)
                    put("radius", radius)
                }.toString()

                android.util.Log.d("Locent", "[Search] jsonBody Content: $jsonBody")
                android.util.Log.d("Locent", "[Search] Sync Request: $lat, $lng ($radius)")

                // 1. Get JWT for Auth with Logging
                val jwt = try {
                    val token = account.createJWT().jwt
                    android.util.Log.d("Locent", "[AUTH] JWT Generated Successfully: ${token.take(10)}...")
                    token
                } catch (e: Exception) {
                    android.util.Log.e("Locent", "[AUTH] Failed to generate JWT: ${e.message}")
                    null 
                }

                // 2. High-Timeout OkHttp Client
                val okHttpClient = OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .build()

                // 3. POST to Appwrite Functions Endpoint (Correctly Wrapped)
                val executionWrapper = JSONObject().apply {
                    put("body", jsonBody)
                    put("async", false)
                }.toString()

                val requestBody = executionWrapper.toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("$ENDPOINT/functions/$FUNCTION_ID/executions")
                    .post(requestBody)
                    .addHeader("X-Appwrite-Project", PROJECT_ID)
                    .addHeader("Content-Type", "application/json")
                
                if (jwt != null) {
                    android.util.Log.d("Locent", "[NET] Attaching X-Appwrite-JWT to request")
                    requestBuilder.addHeader("X-Appwrite-JWT", jwt)
                } else {
                    android.util.Log.w("Locent", "[NET] Proceeding without JWT (User may appear as null in backend)")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                val responseString = response.body?.string() ?: ""
                
                // TOTAL RAW LOGGING AS DEMANDED
                android.util.Log.d("Locent", "[RAW_PAYLOAD] $responseString")

                if (response.code == 429) {
                    return@withContext "ERROR: RATE_LIMIT_EXCEEDED"
                }

                if (!response.isSuccessful) {
                    return@withContext "ERROR: HTTP ${response.code} ($responseString)"
                }

                // 4. Parse Appwrite Execution Result Wrapper
                val executionJson = JSONObject(responseString)
                val status = executionJson.optString("status")
                val responseBody = executionJson.optString("responseBody")

                if (status == "completed") {
                    responseBody
                } else {
                    "ERROR: Appwrite status $status"
                }

            } catch (e: Exception) {
                android.util.Log.e("Locent", "Search Error: ${e.message}")
                "ERROR: ${e.message}"
            }
        }
    }
}
