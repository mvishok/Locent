package me.vishok.locent

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, onNavigateToResult: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val user by AppwriteClient.currentUser.collectAsState()
    val history by AppwriteClient.history.collectAsState()
    var isLoadingHistory by remember { mutableStateOf(false) }
    
    // Selection State
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun refreshHistory() {
        scope.launch {
            isLoadingHistory = true
            AppwriteClient.getHistory()
            isLoadingHistory = false
        }
    }

    suspend fun performDeletion() {
        val idsToDelete = selectedIds.toList()
        val count = idsToDelete.size
        
        // Optimistic UI: clear selection and show snackbar early
        selectedIds = emptySet()
        
        // Execute deletions in parallel for maximum speed
        scope.launch {
            val success = AppwriteClient.deleteHistoryItems(idsToDelete)
            if (success) {
                snackbarHostState.showSnackbar("Deleted $count items")
            } else {
                snackbarHostState.showSnackbar("Some items could not be deleted")
            }
            refreshHistory()
        }
    }

    LaunchedEffect(Unit) { refreshHistory() }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete History?") },
            text = { Text("Are you sure you want to delete ${selectedIds.size} selected items? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch { performDeletion() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) Text("${selectedIds.size} Selected", fontWeight = FontWeight.Bold)
                    else Text("Account Dashboard", fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = if (isSelectionMode) { { selectedIds = emptySet() } } else onBack) {
                        Icon(if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val allIds = history.map { it.id }.toSet()
                            selectedIds = if (selectedIds.size == allIds.size) emptySet() else allIds
                        }) {
                            Icon(if (selectedIds.size == history.size) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, contentDescription = "Select All")
                        }
                        IconButton(onClick = {
                            if (selectedIds.size > 1) {
                                showDeleteConfirm = true
                            } else {
                                scope.launch { performDeletion() }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            // STICKY LOGOUT
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                color = Color.Transparent
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val success = AppwriteClient.logout()
                            if (success) onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = user?.name ?: "User Name",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = user?.email ?: "email@example.com",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Analysis", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    if (isLoadingHistory) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(16.dp))
            }

            if (history.isEmpty() && !isLoadingHistory) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No previous searches yet.",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                items(history) { doc ->
                    val isSelected = selectedIds.contains(doc.id)
                    HistoryCard(
                        doc = doc, 
                        isSelected = isSelected,
                        onToggleSelection = {
                            selectedIds = if (selectedIds.contains(doc.id)) selectedIds - doc.id
                            else selectedIds + doc.id
                        },
                        onNavigate = {
                            val results = doc.data["results"] as? String
                            if (!results.isNullOrEmpty()) {
                                // Base64 is safer for JSON strings containing quotes and braces
                                val safeB64 = android.util.Base64.encodeToString(
                                    results.toByteArray(Charsets.UTF_8),
                                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                                )
                                // Wrap in extra URL encoding to prevent the NavHost from "auto-decoding" characters
                                val finalPayload = java.net.URLEncoder.encode(safeB64, "UTF-8")
                                onNavigateToResult(finalPayload)
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun HistoryCard(
    doc: io.appwrite.models.Document<Map<String, Any>>, 
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onNavigate: () -> Unit
) {
    val query = doc.data["query"] as? String ?: "Nearby Amenities"
    val timestamp = doc.data["timestamp"] as? String ?: ""
    val radius = (doc.data["radius"] as? Number)?.toInt() ?: 2000
    
    val prettyDate = remember(timestamp) { formatPremiumDate(timestamp) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onToggleSelection() },
                    onTap = { onNavigate() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            1.dp, 
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = query,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${radius}m",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(prettyDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

fun formatPremiumDate(isoDate: String): String {
    if (isoDate.isEmpty()) return ""
    return try {
        val instant = Instant.parse(isoDate)
        val ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val now = LocalDateTime.now()
        
        when {
            ldt.toLocalDate() == now.toLocalDate() -> "Today at " + ldt.format(DateTimeFormatter.ofPattern("h:mm a"))
            ldt.toLocalDate() == now.minusDays(1).toLocalDate() -> "Yesterday at " + ldt.format(DateTimeFormatter.ofPattern("h:mm a"))
            else -> ldt.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
        }
    } catch (e: Exception) {
        isoDate.take(10)
    }
}
