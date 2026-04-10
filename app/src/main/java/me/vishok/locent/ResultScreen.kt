package me.vishok.locent

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONArray

data class FeaturedPoint(val name: String, val category: String, val distance: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(payload: String) {
    
    val context = LocalContext.current

    val root = remember(payload) {
        try {
            // 1. Clean the payload of any extra surrounding quotes from Navigation
            val cleanPayload = payload.trim().removeSurrounding("\"")

            // 2. Check if it's already JSON. If it starts with '{', just parse it!
            if (cleanPayload.startsWith("{")) {
                android.util.Log.d("Locent", "[SUCCESS] Direct JSON detected")
                return@remember JSONObject(cleanPayload)
            }

            // 3. If not JSON, try decoding as Base64 (for History items)
            android.util.Log.d("Locent", "[INFO] Attempting Base64 decode for History...")
            val urlDecoded = java.net.URLDecoder.decode(cleanPayload, "UTF-8")
            val decodedBytes = android.util.Base64.decode(urlDecoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            val decodedString = String(decodedBytes, Charsets.UTF_8).trim().removeSurrounding("\"")
            
            android.util.Log.d("Locent", "[SUCCESS] Base64 Parsed: ${decodedString.take(50)}...")
            JSONObject(decodedString)

        } catch (e: Exception) {
            android.util.Log.e("Locent", "[CRITICAL] All parsing failed: ${e.message}")
            // Fallback: If everything fails, try to parse the original payload directly
            try { JSONObject(payload) } catch (e2: Exception) { JSONObject() }
        }
    }

    // 2. Extract ALL basic fields (including Lat/Lng) so they are available to the Scaffold
    val score = root.optInt("overall", 0)
    val inputQuery = root.optString("inputQuery", root.optString("query", "Nearby Analysis"))
    val aiSummary = root.optString("summary", "No AI analysis available for this record.")
    val inputLat = root.optDouble("inputLat", 0.0)
    val inputLng = root.optDouble("inputLng", 0.0)

    // 3. Extract lists using 'remember' so they don't reset
    val categories = remember(root) {
        val list = mutableListOf<CategoryData>()
        val catScores = root.optJSONObject("categoryScores")
        catScores?.keys()?.forEach { key ->
            val catObj = catScores.optJSONObject(key)
            if (catObj != null) {
                val count = catObj.optInt("count", 0)
                val catScoreRaw = catObj.optDouble("score", 0.0)
                if (count > 0 || catScoreRaw > 0) {
                    list.add(CategoryData(key.replaceFirstChar { it.uppercase() }, count, catScoreRaw.toInt()))
                }
            }
        }
        list
    }

    val featuredList = remember(root) {
        val list = mutableListOf<FeaturedPoint>()
        val featuredArray = root.optJSONArray("featured")
        featuredArray?.let {
            for (i in 0 until it.length()) {
                val obj = it.optJSONObject(i) ?: continue
                list.add(FeaturedPoint(
                    obj.optString("name", "Nearby Point"),
                    obj.optString("amenity", "Point"),
                    0
                ))
            }
        }
        list
    }

    val aiWeights = remember(root) {
        val list = mutableListOf<Pair<String, Double>>()
        val weightsNode = root.optJSONObject("weights")
        weightsNode?.keys()?.forEach { key ->
            list.add(key.replaceFirstChar { it.uppercase() } to weightsNode.optDouble(key, 0.0))
        }
        list
    }

    val scoreColor = when {
        score >= 80 -> Color(0xFF10B981) // Emerald Green
        score >= 50 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444) // Red
    }

    var animateScore by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateScore = true }
    
    val animatedScale by animateFloatAsState(
        targetValue = if (animateScore) 1f else 0.8f,
        animationSpec = tween(durationMillis = 800)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Result Analysis", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val shareText = "My setup scored $score/100!\nI plan to: '$inputQuery'\nhttps://maps.google.com/?q=$inputLat,$inputLng"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Analysis"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Share Report", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .scale(animatedScale)
                                .clip(CircleShape)
                                .background(scoreColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$score",
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Black,
                                color = scoreColor,
                                letterSpacing = (-2).sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Suitability Score", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(inputQuery, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (aiSummary.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Locent AI Verdict", 
                                    fontSize = 16.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                aiSummary, 
                                fontSize = 15.sp, 
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            if (featuredList.isNotEmpty()) {
                item {
                    Text("Top Nearby Highlights", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            featuredList.forEach { point ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${point.name}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text("${point.category.replace("_", " ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Text("Suitability Metrics", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (categories.isEmpty()) {
                item {
                    Text(
                        "Nothing to show", 
                        fontSize = 14.sp, 
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            } else {
                items(categories.size) { index ->
                    val it = categories[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(scoreColor.copy(alpha = 0.7f)))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(it.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${it.count} places", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("${it.score}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            item {
                Text("Locent AI Optimization", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                if (aiWeights.isEmpty()) {
                    Text(
                        "Nothing to show", 
                        fontSize = 14.sp, 
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            aiWeights.sortedByDescending { it.second }.take(6).forEach { (cat, w) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(cat, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    LinearProgressIndicator(
                                        progress = (w / 3.0).toFloat(),
                                        modifier = Modifier.width(100.dp).height(6.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

data class CategoryData(val name: String, val count: Int, val score: Int)