package com.vyapaar.quickstatus

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// Initialize DataStore extension
private val Context.dataStore by preferencesDataStore(name = "shop_profile")

// ========================================================
// 1. CLOUD TEMPLATE DATA MODEL
// ========================================================

data class StatusTemplate(
    val id: String = "",
    val category: String = "",
    val themeName: String = "",
    val primaryTextColor: String = "#FFFFFF",
    val secondaryTextColor: String = "#BDBDBD",
    val accentColor: String = "#E23744",
    val layoutStyle: String = "CARD_BOTTOM_FOOTER",
    val bgTextureUrl: String? = null
)

object QuickStatusAI {
    private const val API_KEY = "YOUR_GEMINI_API_KEY" 
    
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = API_KEY
    )

    suspend fun generateSlogan(businessName: String, sector: String, language: String): String? {
        if (API_KEY == "YOUR_GEMINI_API_KEY") return null
        val prompt = "Generate a short, catchy one-line marketing slogan for a $sector business named '$businessName'. The language should be $language. Keep it under 10 words. Don't use quotes."
        return try {
            val response = model.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            null
        }
    }
}

object QuickStatusCloud {
    private val db: FirebaseFirestore? by lazy {
        try {
            Firebase.firestore
        } catch (e: Exception) {
            null
        }
    }

    fun backupProfile(profile: ShopProfile) {
        if (profile.phone.length >= 10) {
            db?.collection("shop_profiles")?.document(profile.phone)?.set(profile)
        }
    }

    suspend fun fetchTemplates(category: String): List<StatusTemplate> {
        val database = db
        return if (database != null) {
            try {
                val snapshot = database.collection("templates")
                    .whereEqualTo("category", category)
                    .get()
                    .await()
                snapshot.toObjects(StatusTemplate::class.java)
            } catch (e: Exception) {
                TemplateGenerator.generateMockCloudTemplates(category)
            }
        } else {
            TemplateGenerator.generateMockCloudTemplates(category)
        }
    }
}

// ========================================================
// PHASE 3 MODELS: DATA-DRIVEN ARCHITECTURE
// ========================================================

enum class BusinessSector(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: androidx.compose.ui.graphics.Color, val tag: String) {
    KIRANA("Grocery Store", Icons.Outlined.Storefront, androidx.compose.ui.graphics.Color(0xFFE23744), "FRESH"),
    FRUITS("Fruits & Veg", Icons.Outlined.LocalMall, androidx.compose.ui.graphics.Color(0xFF388E3C), "ORGANIC"),
    SWEETS("Sweets & Snacks", Icons.Outlined.BakeryDining, androidx.compose.ui.graphics.Color(0xFFEF6C00), "PREMIUM"),
    FASHION("Clothing & Style", Icons.Outlined.Checkroom, androidx.compose.ui.graphics.Color(0xFF673AB7), "TRENDY"),
    ELECTRONICS("Mobiles & Gadgets", Icons.Outlined.Smartphone, androidx.compose.ui.graphics.Color(0xFF2874F0), "LATEST"),
    PHARMACY("Medical Store", Icons.Outlined.MedicalServices, androidx.compose.ui.graphics.Color(0xFFD32F2F), "TRUSTED"),
    SALON("Beauty & Salon", Icons.Outlined.ContentCut, androidx.compose.ui.graphics.Color(0xFFFF3F6C), "EXPERT"),
    RESTAURANT("Food & Cafe", Icons.Outlined.Restaurant, androidx.compose.ui.graphics.Color(0xFFFF5722), "GOURMET"),
    RETAIL("Retail Shop", Icons.Outlined.ShoppingBag, androidx.compose.ui.graphics.Color(0xFF455A64), "SELECT"),
    SERVICES("Home Services", Icons.Outlined.HomeRepairService, androidx.compose.ui.graphics.Color(0xFF795548), "RELIABLE")
}

object TemplateGenerator {
    private const val CLOUD_BASE = "https://firebasestorage.googleapis.com/v0/b/quickstatus-premium/o/templates%2F"

    fun generateMockCloudTemplates(category: String): List<StatusTemplate> {
        val templates = mutableListOf<StatusTemplate>()
        val styles = listOf("CARD_BOTTOM_FOOTER", "SIDE_BY_SIDE_GRID", "MINIMALIST_CENTER")
        val palettes = listOf(
            Triple("#FFFFFF", "#E0E0E0", "#E23744"), // Light
            Triple("#121212", "#757575", "#FFFFFF"), // Dark
            Triple("#000000", "#FFD700", "#FFD700"), // Gold Accent
            Triple("#F5F5F5", "#424242", "#2874F0")  // Blue Accent
        )

        val folder = category.lowercase().replace(" ", "_")
        for (i in 0 until 6) {
            val palette = palettes[i % palettes.size]
            templates.add(
                StatusTemplate(
                    id = "${category}_$i",
                    category = category,
                    themeName = "Artisan Theme $i",
                    primaryTextColor = palette.first,
                    secondaryTextColor = palette.second,
                    accentColor = palette.third,
                    layoutStyle = styles[i % styles.size],
                    bgTextureUrl = "$CLOUD_BASE$folder%2Fbg_${i % 5}.jpg?alt=media"
                )
            )
        }
        return templates
    }
}

data class ShopProfile(
    val businessName: String = "",
    val phone: String = "",
    val category: String = "",
    val address: String = "",
    val upiId: String = ""
)

class MainActivity : ComponentActivity() {

    private val KEY_BUSINESS_NAME = stringPreferencesKey("business_name")
    private val KEY_PHONE = stringPreferencesKey("phone")
    private val KEY_CATEGORY = stringPreferencesKey("category")
    private val KEY_ADDRESS = stringPreferencesKey("address")
    private val KEY_UPI_ID = stringPreferencesKey("upi_id")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color.White,
                    background = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
                    onSurface = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppDashboard()
                }
            }
        }
    }

    suspend fun loadProfile(context: Context): ShopProfile {
        val prefs = context.dataStore.data.first()
        return ShopProfile(
            businessName = prefs[KEY_BUSINESS_NAME] ?: "",
            phone = prefs[KEY_PHONE] ?: "",
            category = prefs[KEY_CATEGORY] ?: BusinessSector.KIRANA.label,
            address = prefs[KEY_ADDRESS] ?: "",
            upiId = prefs[KEY_UPI_ID] ?: ""
        )
    }

    suspend fun saveProfile(context: Context, profile: ShopProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUSINESS_NAME] = profile.businessName
            prefs[KEY_PHONE] = profile.phone
            prefs[KEY_CATEGORY] = profile.category
            prefs[KEY_ADDRESS] = profile.address
            prefs[KEY_UPI_ID] = profile.upiId
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolishedInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.LightGray,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.DarkGray) },
            leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = trailingIcon,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Gray,
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = androidx.compose.ui.graphics.Color.White)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppDashboard() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var selectedSector by remember { mutableStateOf<BusinessSector?>(null) }
    var activeTemplate by remember { mutableStateOf<StatusTemplate?>(null) }

    var businessName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }

    var greetingText by remember { mutableStateOf("Welcome") }
    var mainOfferText by remember { mutableStateOf("Limited Time Offer") }
    var promoSubtext by remember { mutableStateOf("Visit us today!") }
    var selectedLanguage by remember { mutableStateOf("en") }

    LaunchedEffect(Unit) {
        activity?.let { act ->
            val profile = act.loadProfile(context)
            businessName = profile.businessName
            phone = profile.phone
            address = profile.address
            upiId = profile.upiId
            if (businessName.isNotEmpty()) greetingText = "Welcome to $businessName"
        }
    }

    val currentProfile = ShopProfile(businessName, phone, selectedSector?.label ?: "", address, upiId)

    Scaffold(
        topBar = {
            if (activeTemplate == null) {
                Surface(color = androidx.compose.ui.graphics.Color.Black) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), CircleShape)
                                        .padding(6.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color.White)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = if (selectedSector == null) "QUICKSTATUS" else selectedSector!!.label.uppercase(),
                                        fontWeight = FontWeight.Light,
                                        fontSize = 15.sp,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        letterSpacing = 4.sp
                                    )
                                    Text("PROFESSIONAL STUDIO", fontSize = 8.sp, color = androidx.compose.ui.graphics.Color.Gray, letterSpacing = 2.sp)
                                }
                            }
                        },
                        navigationIcon = {
                            if (selectedSector != null) {
                                IconButton(onClick = { selectedSector = null }) {
                                    Icon(Icons.Default.KeyboardArrowLeft, null, tint = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Black),
                        actions = {
                            IconButton(onClick = { /* Search */ }) {
                                Icon(Icons.Outlined.Tune, null, tint = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (activeTemplate == null) {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Black, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; selectedSector = null },
                        icon = { Icon(if (selectedTab == 0) Icons.Outlined.Person else Icons.Outlined.PersonOutline, null) },
                        label = { Text("ME", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.ui.graphics.Color.White,
                            selectedTextColor = androidx.compose.ui.graphics.Color.White,
                            unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                            unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Outlined.Dashboard, null) },
                        label = { Text("STUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.ui.graphics.Color.White,
                            selectedTextColor = androidx.compose.ui.graphics.Color.White,
                            unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                            unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (selectedTab == 0) {
                ProfileForm(businessName, { businessName = it }, phone, { phone = it }, address, { address = it }, upiId, { upiId = it }, onSave = {
                    scope.launch { activity?.saveProfile(context, currentProfile); Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show(); selectedTab = 1 }
                })
            } else {
                if (selectedSector == null) {
                    CategorySelectionGrid { selectedSector = it }
                } else {
                    TemplateMarketplace(selectedSector!!, currentProfile, onTemplateClick = { activeTemplate = it })
                }
            }

            AnimatedVisibility(visible = activeTemplate != null, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()) {
                activeTemplate?.let { template ->
                    DesignSandbox(template, currentProfile, greetingText, { greetingText = it }, mainOfferText, { mainOfferText = it }, promoSubtext, { promoSubtext = it }, selectedLanguage, { code ->
                        selectedLanguage = code
                    }, onBack = { activeTemplate = null })
                }
            }
        }
    }
}

@Composable
fun CategorySelectionGrid(onSectorSelected: (BusinessSector) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0A0A0A), androidx.compose.ui.graphics.Color.Black))
            )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (i in 0 until 10) {
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.03f),
                        start = androidx.compose.ui.geometry.Offset(i * 150f, 0f),
                        end = androidx.compose.ui.geometry.Offset(i * 150f - 200f, size.height),
                        strokeWidth = 1f
                    )
                }
            }
            Column(modifier = Modifier.padding(32.dp).align(Alignment.BottomStart)) {
                Surface(color = androidx.compose.ui.graphics.Color(0xFFE23744), shape = RoundedCornerShape(2.dp)) {
                    Text("PREMIUM UTILITY", color = androidx.compose.ui.graphics.Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Artisan\nMarketplace", color = androidx.compose.ui.graphics.Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose a trade sector to browse curated designs", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 11.sp, letterSpacing = 0.5.sp)
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            BusinessSector.values().forEach { sector ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onSectorSelected(sector) },
                    color = androidx.compose.ui.graphics.Color(0xFF0F0F0F),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color(0xFF1F1F1F))
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(modifier = Modifier.size(48.dp), color = sector.color.copy(alpha = 0.08f), shape = CircleShape) {}
                            Icon(sector.icon, null, modifier = Modifier.size(22.dp), tint = sector.color)
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(sector.label.uppercase(), color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(sector.tag, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = androidx.compose.ui.graphics.Color(0xFF2F2F2F), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun ProfileForm(name: String, onNameChange: (String) -> Unit, phone: String, onPhoneChange: (String) -> Unit, address: String, onAddressChange: (String) -> Unit, upi: String, onUpiChange: (String) -> Unit, onSave: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Surface(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), shape = CircleShape, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.AccountCircle, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("VERIFIED IDENTITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Gray, letterSpacing = 4.sp)
            Text("Business Profile", fontSize = 34.sp, fontWeight = FontWeight.ExtraLight, color = androidx.compose.ui.graphics.Color.White, letterSpacing = (-1).sp)
        }
        PolishedInput(name, onNameChange, "Trade Name", Icons.Outlined.Business, "Enter Shop Name")
        PolishedInput(phone, onPhoneChange, "WhatsApp Registry", Icons.Outlined.Phone, "+91 00000 00000")
        PolishedInput(address, onAddressChange, "Base Location", Icons.Outlined.LocationOn, "City, Area")
        PolishedInput(upi, onUpiChange, "Payment Handle", Icons.Outlined.QrCode, "upi@handle")
        Spacer(modifier = Modifier.height(56.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = androidx.compose.ui.graphics.Color.Black)) { 
            Text("INITIALIZE STUDIO", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp) 
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun TemplateMarketplace(sector: BusinessSector, profile: ShopProfile, onTemplateClick: (StatusTemplate) -> Unit) {
    var templates by remember { mutableStateOf<List<StatusTemplate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sector) {
        isLoading = true
        templates = QuickStatusCloud.fetchTemplates(sector.label)
        isLoading = false
    }
    
    Column(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "COLLECTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Gray, letterSpacing = 4.sp)
                Text(text = sector.label.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Light, color = androidx.compose.ui.graphics.Color.White, letterSpacing = 2.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(20.dp), tint = androidx.compose.ui.graphics.Color.White)
        }
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), 
                verticalArrangement = Arrangement.spacedBy(16.dp), 
                horizontalArrangement = Arrangement.spacedBy(16.dp), 
                modifier = Modifier.fillMaxSize(), 
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                items(templates) { template ->
                    TemplateGridItem(template, profile, onTemplateClick)
                }
            }
        }
    }
}

@Composable
fun TemplateGridItem(template: StatusTemplate, profile: ShopProfile, onTemplateClick: (StatusTemplate) -> Unit) {
    val context = LocalContext.current
    var cloudBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isImageLoading by remember { mutableStateOf(false) }

    LaunchedEffect(template.bgTextureUrl) {
        template.bgTextureUrl?.let { url ->
            isImageLoading = true
            val loader = coil.ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = loader.execute(request)
            if (result is coil.request.SuccessResult) {
                cloudBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }
            isImageLoading = false
        }
    }

    val previewBitmap = remember(cloudBitmap, template) { 
        StatusCardCanvasEngine.generateStatusCardBitmap(
            context, template, profile, "Sample", "Offer Details", "Details Here", cloudBitmap
        ) 
    }

    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.65f).clickable { onTemplateClick(template) },
        color = androidx.compose.ui.graphics.Color(0xFF111111),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color.DarkGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(bitmap = previewBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (isImageLoading) {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomStart).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(text = template.themeName.take(6), color = androidx.compose.ui.graphics.Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSandbox(template: StatusTemplate, profile: ShopProfile, greeting: String, onGreetingChange: (String) -> Unit, offer: String, onOfferChange: (String) -> Unit, subtext: String, onSubtextChange: (String) -> Unit, language: String, onLanguageChange: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    var isGenerating by remember { mutableStateOf(false) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAILoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    var remoteTexture by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(template.bgTextureUrl) {
        template.bgTextureUrl?.let { url ->
            val loader = coil.ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = loader.execute(request)
            if (result is coil.request.SuccessResult) {
                remoteTexture = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }
        }
    }

    LaunchedEffect(template, profile, greeting, offer, subtext, remoteTexture) {
        isGenerating = true
        kotlinx.coroutines.delay(100)
        currentBitmap = StatusCardCanvasEngine.generateStatusCardBitmap(context, template, profile, greeting, offer, subtext, remoteTexture)
        isGenerating = false
    }

    Scaffold(
        topBar = {
            Surface(color = androidx.compose.ui.graphics.Color.Black) {
                TopAppBar(
                    title = { Text("EDITOR", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = androidx.compose.ui.graphics.Color.White) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = androidx.compose.ui.graphics.Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Black)
                )
            }
        },
        bottomBar = {
            Surface(color = androidx.compose.ui.graphics.Color.Black, border = BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color.DarkGray)) {
                Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { currentBitmap?.let { saveAndShareBitmap(context, it, false) } }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(0.dp), border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White)) {
                        Text("EXPORT", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
                    }
                    Button(onClick = { currentBitmap?.let { saveAndShareBitmap(context, it, true) } }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = androidx.compose.ui.graphics.Color.Black)) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("WHATSAPP", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(androidx.compose.ui.graphics.Color.Black).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).background(androidx.compose.ui.graphics.Color(0xFF080808)), contentAlignment = Alignment.Center) {
                currentBitmap?.let { 
                    Image(bitmap = it.asImageBitmap(), null, modifier = Modifier.fillMaxHeight().padding(32.dp).border(0.5.dp, androidx.compose.ui.graphics.Color.DarkGray), contentScale = ContentScale.Fit) 
                }
                if (isGenerating) CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }

            Column(modifier = Modifier.padding(24.dp)) {
                Text("LANGUAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Gray, letterSpacing = 4.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "ENGLISH", "hi" to "हिन्दी", "kn" to "ಕನ್ನಡ").forEach { (code, label) ->
                        val isSelected = language == code
                        Surface(modifier = Modifier.weight(1f).height(40.dp).clickable { onLanguageChange(code) }, shape = RoundedCornerShape(0.dp), color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent, border = if (isSelected) null else BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color.DarkGray)) {
                            Box(contentAlignment = Alignment.Center) { Text(text = label, color = if (isSelected) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
                Text("CUSTOMIZATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Gray, letterSpacing = 4.sp)
                PolishedInput(greeting, onGreetingChange, "Header Title", Icons.Outlined.TextFields)
                PolishedInput(value = offer, onValueChange = onOfferChange, label = "Core Message", icon = Icons.Outlined.LocalOffer, trailingIcon = {
                        IconButton(onClick = { scope.launch { isAILoading = true; val newOffer = QuickStatusAI.generateSlogan(profile.businessName, profile.category, language); if (newOffer != null) onOfferChange(newOffer); isAILoading = false } }, enabled = !isAILoading) {
                            if (isAILoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                            else Icon(Icons.Outlined.AutoAwesome, "AI", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                        }
                    })
                PolishedInput(subtext, onSubtextChange, "Details / Terms", Icons.Outlined.Info)
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

private fun saveAndShareBitmap(context: Context, bitmap: Bitmap, targetWhatsApp: Boolean) {
    try {
        val imagesFolder = File(context.cacheDir, "images"); if (!imagesFolder.exists()) imagesFolder.mkdirs()
        val file = File(imagesFolder, "status_card.png"); val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); stream.flush(); stream.close()
        val uri: Uri = FileProvider.getUriForFile(context, "com.vyapaar.quickstatus.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (targetWhatsApp) shareIntent.setPackage("com.whatsapp")
        context.startActivity(Intent.createChooser(shareIntent, "Share QuickStatus Card"))
    } catch (e: Exception) { e.printStackTrace() }
}

// ========================================================
// 2. DYNAMIC TEXT OVERLAY PAINTING & 3. CONDITIONAL RENDER
// ========================================================

object StatusCardCanvasEngine {
    fun generateStatusCardBitmap(
        context: Context, 
        template: StatusTemplate, 
        profile: ShopProfile, 
        greeting: String, 
        offer: String, 
        subtext: String,
        bgTexture: Bitmap? = null
    ): Bitmap {
        val width = 1080; val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = 540f

        // Draw Base (Texture or Solid)
        canvas.drawColor(Color.BLACK)
        bgTexture?.let {
            val src = Rect(0, 0, it.width, it.height)
            val dst = Rect(0, 0, width, height)
            paint.alpha = 130 
            canvas.drawBitmap(it, src, dst, paint)
            paint.alpha = 255
        }

        // Parse Dynamic Colors from Template Metadata
        val primaryCol = Color.parseColor(template.primaryTextColor)
        val secondaryCol = Color.parseColor(template.secondaryTextColor)
        val accentCol = Color.parseColor(template.accentColor)

        // Reset Paint for Text
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = 0.05f 

        when (template.layoutStyle) {
            "CARD_BOTTOM_FOOTER" -> drawBottomFooterLayout(canvas, paint, greeting, offer, subtext, profile, primaryCol, secondaryCol, accentCol, width, height)
            "SIDE_BY_SIDE_GRID" -> drawSideBySideLayout(canvas, paint, greeting, offer, subtext, profile, primaryCol, secondaryCol, accentCol, width, height)
            "MINIMALIST_CENTER" -> drawMinimalistCenterLayout(canvas, paint, greeting, offer, subtext, profile, primaryCol, secondaryCol, accentCol, width, height)
            else -> drawBottomFooterLayout(canvas, paint, greeting, offer, subtext, profile, primaryCol, secondaryCol, accentCol, width, height)
        }

        return bitmap
    }

    private fun drawBottomFooterLayout(canvas: Canvas, paint: Paint, greeting: String, offer: String, subtext: String, profile: ShopProfile, pCol: Int, sCol: Int, aCol: Int, w: Int, h: Int) {
        val cx = w / 2f
        // Large Hero Offer (Centered Top-Mid)
        paint.color = pCol
        paint.textSize = 120f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        drawWrappedText(canvas, offer.uppercase(), cx, 600f, paint, 940, 30f)

        // Subtext
        paint.color = sCol
        paint.textSize = 50f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(subtext, cx, 850f, paint)

        // Docked Professional Footer
        paint.color = aCol
        paint.alpha = 240
        canvas.drawRect(0f, h - 300f, w.toFloat(), h.toFloat(), paint)
        paint.alpha = 255

        paint.color = Color.BLACK
        paint.textSize = 65f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(profile.businessName.uppercase(), cx, h - 180f, paint)

        paint.textSize = 35f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("${profile.category}  |  ${profile.phone}", cx, h - 100f, paint)
    }

    private fun drawSideBySideLayout(canvas: Canvas, paint: Paint, greeting: String, offer: String, subtext: String, profile: ShopProfile, pCol: Int, sCol: Int, aCol: Int, w: Int, h: Int) {
        // Multi-column Quadrant Mapping
        val leftX = w * 0.25f
        val rightX = w * 0.75f
        
        paint.textAlign = Paint.Align.LEFT
        paint.color = aCol
        paint.textSize = 45f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(greeting.uppercase(), 80f, 300f, paint)

        paint.color = pCol
        paint.textSize = 140f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        drawWrappedText(canvas, offer, 80f, 500f, paint, 600, 40f)

        // Bottom Details in Symmetry
        paint.color = sCol
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(profile.businessName, 80f, h - 200f, paint)
        canvas.drawText(profile.phone, 80f, h - 140f, paint)
    }

    private fun drawMinimalistCenterLayout(canvas: Canvas, paint: Paint, greeting: String, offer: String, subtext: String, profile: ShopProfile, pCol: Int, sCol: Int, aCol: Int, w: Int, h: Int) {
        val cx = w / 2f
        paint.textAlign = Paint.Align.CENTER
        
        // Boutique Aesthetic with expanded Line Height
        paint.color = sCol
        paint.textSize = 55f
        paint.letterSpacing = 0.3f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(greeting.uppercase(), cx, 400f, paint)
        paint.letterSpacing = 0.05f

        paint.color = pCol
        paint.textSize = 110f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        drawWrappedText(canvas, offer, cx, 960f, paint, 900, 60f)

        // Bottom Signature
        paint.color = aCol
        canvas.drawCircle(cx, h - 300f, 15f, paint)
        paint.color = pCol
        paint.textSize = 45f
        canvas.drawText(profile.businessName.uppercase(), cx, h - 200f, paint)
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, maxWidth: Int, lineSpacing: Float): Float {
        val words = text.split(" "); val lines = mutableListOf<String>(); var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) currentLine = testLine else { lines.add(currentLine); currentLine = word }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        var currentY = y
        for (line in lines) { canvas.drawText(line, x, currentY, paint); currentY += paint.textSize + lineSpacing }
        return currentY
    }

    private fun drawDesignNoise(canvas: Canvas, paint: Paint) {
        val random = java.util.Random()
        for (i in 0 until 100) {
            val rx = random.nextInt(1080).toFloat()
            val ry = random.nextInt(1920).toFloat()
            canvas.drawCircle(rx, ry, 2f, paint)
        }
    }
}
