package me.vishok.locent
import android.util.Base64
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import com.google.android.gms.maps.model.MapStyleOptions
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppwriteClient.init(this)
        setContent { LocentApp(this) }
    }
}

@Composable
fun LocentApp(activity: ComponentActivity) {
    val darkTheme = isSystemInDarkTheme()
    val navController = rememberNavController()
    val view = LocalView.current

    SideEffect {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.windowInsetsController?.setSystemBarsAppearance(
                if (darkTheme) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    val customDarkColor = darkColorScheme(
        background = Color(0xFF101010),
        surface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFF242424),
        primary = Color(0xFF00E5FF)
    )
    val customLightColor = lightColorScheme(
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF0F0F0),
        primary = Color(0xFF00B8D4)
    )

    MaterialTheme(
        colorScheme = if (darkTheme) customDarkColor else customLightColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // CHANGED: startDestination is now "splash"
            NavHost(navController = navController, startDestination = "splash") {

                // NEW: Splash Destination
                composable("splash") {
                    SplashScreen(isDark = darkTheme) {
                        navController.navigate("main") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }

                composable("main") {
                    MainScreen(
                        activity = activity,
                        onNavigateToProfile = { navController.navigate("profile") },
                        onNavigateToResult = { payload ->
                            val safePayload = Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
                            navController.navigate("result/$safePayload")
                        }
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onLogout = { navController.popBackStack() },
                        onNavigateToResult = { payload ->
                            val safePayload = java.net.URLEncoder.encode(payload, "UTF-8")
                            navController.navigate("result/$safePayload")
                        }
                    )
                }
                composable(route = "result/{payload}") { backStackEntry ->
                    val encodedPayload = backStackEntry.arguments?.getString("payload") ?: "{}"
                    val payload = try {
                        val decodedBytes = Base64.decode(encodedPayload, Base64.URL_SAFE or Base64.NO_WRAP)
                        String(decodedBytes)
                    } catch (e: Exception) {
                        try {
                            java.net.URLDecoder.decode(encodedPayload, "UTF-8")
                        } catch (e2: Exception) {
                            encodedPayload
                        }
                    }
                    ResultScreen(payload = payload)
                }
            }
        }
    }
}

@Composable
fun SplashScreen(isDark: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Ensure these match your filenames in res/drawable
    val gifRes = if (isDark) R.drawable.dark_splash else R.drawable.splash

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(gifRes)
                .decoderFactory(GifDecoder.Factory())
                .build(),
            contentDescription = null,
            modifier = Modifier.size(300.dp),
            onSuccess = {
                scope.launch {
                    delay(2500) 
                    onFinish()
                }
            },
            onError = {
                onFinish()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    activity: ComponentActivity,
    onNavigateToProfile: () -> Unit,
    onNavigateToResult: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<LatLng, String>>>(emptyList()) }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var currentAddress by remember { mutableStateOf("Locating...") }
    var isSearching by remember { mutableStateOf(false) }
    var isAuthed by remember { mutableStateOf(false) }
    var selectedRadius by remember { mutableStateOf(1000f) }
    var showOptions by remember { mutableStateOf(false) }
    
    // Rate Limit State
    var showRateLimitSheet by remember { mutableStateOf(false) }
    var isRateLimitGuest by remember { mutableStateOf(false) }

    val cameraState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(13.0827, 80.2707), 16f) }

    LaunchedEffect(selectedLocation) {
        selectedLocation?.let {
            delay(1000)
            val addr = GeocodingService.reverseGeocode(it.latitude, it.longitude)
            currentAddress = addr?.split(",")?.take(3)?.joinToString(",") ?: "Selected Location"
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(500)
            searchResults = GeocodingService.searchLocation(searchQuery)
        } else {
            searchResults = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        isAuthed = AppwriteClient.checkSession()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapViewPremium(cameraState = cameraState, onLocationChanged = { selectedLocation = it })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("locent", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            
            IconButton(
                onClick = {
                    if (isAuthed) onNavigateToProfile()
                    else {
                        scope.launch {
                            val err = AppwriteClient.loginWithGoogle(activity)
                            if (err.isEmpty()) {
                                isAuthed = AppwriteClient.checkSession() // REFRESH SESSION TO FIX NAME/EMAIL
                            } else Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Icon(
                    imageVector = if (isAuthed) Icons.Default.AccountCircle else Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = if (isAuthed) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }

        // 1. TOP SEARCH PILL (Stays at top)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 80.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NominatimSearchPill(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                results = searchResults,
                onResultSelected = { latLng, name ->
                    searchQuery = ""
                    searchResults = emptyList()
                    scope.launch {
                        cameraState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 17f), 1000)
                    }
                }
            )
        }

        // 2. BOTTOM GROUP (Address + Textbox): Pushes up with keyboard, but leaves gap
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding() 
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Address Card
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = currentAddress,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(12.dp)) 

            FloatingActionPill(
                query = query,
                onQueryChange = { query = it },
                onSend = { showOptions = true }
            )
        }

        // Floating action button for Locate Me
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp, bottom = 40.dp)
        ) {
            val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        try {
                            val location = locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                            location?.let {
                                cameraState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f))
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
            }
        }

        if (showOptions) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { showOptions = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.85f).clickable(enabled = false) { },
                    shape = RoundedCornerOf(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Search Radius", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = selectedRadius,
                            onValueChange = { selectedRadius = it },
                            valueRange = 1000f..5000f,
                            steps = 7 // 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000
                        )
                        Text("${selectedRadius.toInt()}m", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { 
                                showOptions = false
                                if (query.isNotBlank() && selectedLocation != null) {
                                    isSearching = true
                                    scope.launch {
                                        val res = AppwriteClient.executeSearch(
                                            selectedLocation!!.latitude, 
                                            selectedLocation!!.longitude, 
                                            query, 
                                            selectedRadius.toInt()
                                        )
                                        isSearching = false
                                        if (res == "ERROR: RATE_LIMIT_EXCEEDED") {
                                            isRateLimitGuest = !isAuthed
                                            showRateLimitSheet = true
                                        } else if (!res.isNullOrEmpty() && !res.startsWith("ERROR:")) {
                                            try {
                                                val resObj = JSONObject(res)
                                                resObj.put("inputLat", selectedLocation!!.latitude)
                                                resObj.put("inputLng", selectedLocation!!.longitude)
                                                resObj.put("inputQuery", query)
                                                resObj.put("inputRadius", selectedRadius.toInt())
                                                onNavigateToResult(resObj.toString())
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Malformed Result: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, res ?: "Search Timeout", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black)
                        ) {
                            Text("Analyze Locent")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isSearching, enter = fadeIn(), exit = fadeOut()) {
            LoadingScreen(query = query)
        }

        // RATE LIMIT MODAL (PREMIUM)
        if (showRateLimitSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRateLimitSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LocationOn, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Search Limit Reached", 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (isRateLimitGuest) 
                            "You've used your 2 free searches for today. Sign in to get 10 searches per day!" 
                            else "Today's search limit (10/10) has been reached. Check back tomorrow!",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                    
                    Spacer(Modifier.height(32.dp))
                    if (isRateLimitGuest) {
                        Button(
                            onClick = {
                                showRateLimitSheet = false
                                scope.launch {
                                    val err = AppwriteClient.loginWithGoogle(activity)
                                    if (err.isEmpty()) isAuthed = AppwriteClient.checkSession()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Sign In with Google", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { showRateLimitSheet = false },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("Got it", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NominatimSearchPill(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    results: List<Pair<LatLng, String>>,
    onResultSelected: (LatLng, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search for a place...", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true
            )
        }
        
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results) { (latLng, name) ->
                        Text(
                            text = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(latLng, name) }
                                .padding(16.dp),
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingActionPill(query: String, onQueryChange: (String) -> Unit, onSend: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 18.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("What are you looking for here?", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, 
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSend() })
            )
            IconButton(
                onClick = { keyboard?.hide(); onSend() },
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                enabled = query.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black)
            }
        }
    }
}

@Composable
fun MapViewPremium(cameraState: CameraPositionState, onLocationChanged: (LatLng) -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val mapStyleOptions = remember(isDark) {
        if (isDark) MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) else null
    }

    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    // FIX: Box background set to dark color to prevent flash
    Box(Modifier.fillMaxSize().background(if (isDark) Color(0xFF101010) else Color(0xFFFAFAFA))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(
                isMyLocationEnabled = hasPermission,
                mapStyleOptions = mapStyleOptions
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        )
        // ... rest of your existing center pin code
        Box(modifier = Modifier.align(Alignment.Center).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
    LaunchedEffect(cameraState.isMoving) {
        if (!cameraState.isMoving) onLocationChanged(cameraState.position.target)
    }
}

@Composable
fun RoundedCornerOf(size: Dp) = RoundedCornerShape(size)