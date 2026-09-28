package com.forsa.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class PaymentStartResponse(
    val orderId: String,
    val redirectUrl: String,
    val amountIqd: Int
)

private suspend fun startForsaPayment(
    baseUrl: String,
    idToken: String,
    jobId: String,
    planId: String
): PaymentStartResponse = withContext(Dispatchers.IO) {
    val cleanBaseUrl = baseUrl.trim().trimEnd('/')
    if (cleanBaseUrl.isBlank()) throw IllegalStateException("PAYMENT_API_NOT_CONFIGURED")

    val connection = (URL(cleanBaseUrl + "/api/payment/create").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 20_000
        doOutput = true
        setRequestProperty("Authorization", "Bearer " + idToken)
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }

    try {
        val requestBody = JSONObject()
            .put("jobId", jobId)
            .put("planId", planId)
            .toString()
        connection.outputStream.use { output ->
            output.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.let { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader -> reader.readText() }
        }.orEmpty()
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)

        if (code !in 200..299 || !json.optBoolean("success", false)) {
            throw IllegalStateException(json.optString("error").ifBlank { "PAYMENT_START_FAILED" })
        }

        val data = json.optJSONObject("data") ?: throw IllegalStateException("PAYMENT_START_FAILED")
        val orderId = data.optString("orderId")
        val redirectUrl = data.optString("redirectUrl")
        if (orderId.isBlank() || redirectUrl.isBlank()) {
            throw IllegalStateException("PAYMENT_START_FAILED")
        }

        PaymentStartResponse(
            orderId = orderId,
            redirectUrl = redirectUrl,
            amountIqd = data.optInt("amountIqd", 0)
        )
    } finally {
        connection.disconnect()
    }
}

private data class PaymentStatusResponse(
    val orderId: String,
    val status: String,
    val jobId: String,
    val planId: String,
    val promotionExpiresAt: Long
)

private suspend fun fetchForsaPaymentStatus(
    baseUrl: String,
    idToken: String,
    orderId: String
): PaymentStatusResponse = withContext(Dispatchers.IO) {
    val cleanBaseUrl = baseUrl.trim().trimEnd('/')
    if (cleanBaseUrl.isBlank()) throw IllegalStateException("PAYMENT_API_NOT_CONFIGURED")
    if (orderId.isBlank()) throw IllegalStateException("ORDER_ID_REQUIRED")

    val url = URL(
        cleanBaseUrl + "/api/payment/status?orderId=" +
            java.net.URLEncoder.encode(orderId, "UTF-8")
    )
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("Authorization", "Bearer " + idToken)
        setRequestProperty("Accept", "application/json")
    }

    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.let { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }.orEmpty()
        val json = if (body.isBlank()) JSONObject() else JSONObject(body)
        if (code !in 200..299 || !json.optBoolean("success", false)) {
            throw IllegalStateException(
                json.optString("error").ifBlank { "PAYMENT_STATUS_FAILED" }
            )
        }
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("PAYMENT_STATUS_FAILED")

        PaymentStatusResponse(
            orderId = data.optString("orderId", orderId),
            status = data.optString("status", "pending"),
            jobId = data.optString("jobId"),
            planId = data.optString("planId"),
            promotionExpiresAt = data.optLong("promotionExpiresAt", 0L)
        )
    } finally {
        connection.disconnect()
    }
}

private enum class AuthScreen { Welcome, Login, Register, Phone, RoleSelection, ResetPassword }
private enum class MainTab { Home, Jobs, Publish, Profile }

private const val JOB_DEFAULT_EXPIRY_DAYS = 30L
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

private fun formatForsaDate(timestamp: Long): String {
    if (timestamp <= 0L) return "غير محدد"
    return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(timestamp))
}

private data class Job(
    val id: String,
    val title: String,
    val company: String,
    val city: String,
    val type: String,
    val description: String,
    val ownerUid: String,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isExpired: Boolean = false,
    val isActive: Boolean = true,
    val isFeatured: Boolean = false,
    val promotionType: String = "",
    val promotionStatus: String = "",
    val promotionExpiresAt: Long = 0L
)

private data class ApplicationItem(
    val id: String,
    val jobId: String,
    val applicantUid: String = "",
    val jobTitle: String,
    val company: String,
    val applicantName: String,
    val applicantEmail: String,
    val note: String,
    val status: String,
    val createdAt: Long,
    val cvHeadline: String = "",
    val cvAbout: String = "",
    val cvEducation: String = "",
    val cvExperience: String = "",
    val cvSkills: String = "",
    val cvLanguages: String = "",
    val cvPhone: String = "",
    val cvCity: String = "",
    val cvFileName: String = "",
    val cvStoragePath: String = ""
)

private data class CvProfile(
    val headline: String = "",
    val about: String = "",
    val education: String = "",
    val experience: String = "",
    val skills: String = "",
    val languages: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val storagePath: String = ""
)

private data class DashboardApplication(
    val jobId: String,
    val jobTitle: String,
    val applicantName: String,
    val status: String,
    val createdAt: Long
)

private data class NotificationItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val jobId: String,
    val applicationId: String,
    val status: String,
    val read: Boolean,
    val createdAt: Long
)

class MainActivity : ComponentActivity() {
    private var paymentIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paymentIntentState = intent
        setContent {
            ForsaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ForsaApp(paymentIntent = paymentIntentState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        paymentIntentState = intent
    }
}

private object ForsaUi {
    val Primary = Color(0xFF5B4BDB)
    val PrimaryDark = Color(0xFF4536B8)
    val PrimarySoft = Color(0xFFEAE7FF)
    val Secondary = Color(0xFF0D9F86)
    val SecondarySoft = Color(0xFFDDF7F0)
    val Ink = Color(0xFF171721)
    val Muted = Color(0xFF6C6C7A)
    val Background = Color(0xFFF5F6FA)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE4E5ED)
    val Success = Color(0xFF14866B)
    val SuccessSoft = Color(0xFFE2F6EF)
    val Warning = Color(0xFFB7791F)
    val WarningSoft = Color(0xFFFFF3D9)
    val Danger = Color(0xFFC53D4A)
    val DangerSoft = Color(0xFFFDE5E7)

    val Gradient = Brush.linearGradient(
        listOf(PrimaryDark, Primary, Color(0xFF7665EC))
    )

    val CardShape = RoundedCornerShape(22.dp)
    val SheetShape = RoundedCornerShape(28.dp)
    val FieldShape = RoundedCornerShape(16.dp)
    val SmallShape = RoundedCornerShape(12.dp)
    val PillShape = RoundedCornerShape(999.dp)
    val NavShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

private val ForsaShapes = androidx.compose.material3.Shapes(
    small = ForsaUi.SmallShape,
    medium = ForsaUi.FieldShape,
    large = ForsaUi.CardShape,
    extraLarge = ForsaUi.SheetShape
)

private val ForsaTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
private fun ForsaTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.lightColorScheme(
        primary = ForsaUi.Primary,
        onPrimary = Color.White,
        primaryContainer = ForsaUi.PrimarySoft,
        onPrimaryContainer = Color(0xFF2B246E),
        secondary = ForsaUi.Secondary,
        onSecondary = Color.White,
        secondaryContainer = ForsaUi.SecondarySoft,
        onSecondaryContainer = Color(0xFF084A3E),
        tertiary = Color(0xFFE59D32),
        background = ForsaUi.Background,
        surface = ForsaUi.Surface,
        surfaceVariant = Color(0xFFF0F1F6),
        surfaceContainer = Color(0xFFF1F2F7),
        outline = ForsaUi.Border,
        onBackground = ForsaUi.Ink,
        onSurface = ForsaUi.Ink,
        onSurfaceVariant = ForsaUi.Muted,
        error = ForsaUi.Danger,
        errorContainer = ForsaUi.DangerSoft,
        onErrorContainer = Color(0xFF5C141C)
    )
    MaterialTheme(
        colorScheme = colors,
        typography = ForsaTypography,
        shapes = ForsaShapes,
        content = content
    )
}

@Composable
private fun ForsaPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Surface(
                    Modifier.fillMaxSize(),
                    shape = ForsaUi.SmallShape,
                    color = ForsaUi.Surface,
                    border = BorderStroke(1.dp, ForsaUi.Border)
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowForward, "رجوع", tint = ForsaUi.Ink, modifier = Modifier.size(19.dp))
                    }
                }
            }
            Spacer(Modifier.width(9.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ForsaUi.Muted) }
        }
        actions()
    }
}

@Composable
private fun ForsaCard(
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = ForsaUi.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasis) ForsaUi.PrimarySoft else ForsaUi.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

@Composable
private fun ForsaSectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ForsaUi.Muted) }
    }
}

@Composable
private fun ForsaMetaChip(
    text: String,
    icon: @Composable (() -> Unit)? = null,
    selected: Boolean = false
) {
    Surface(
        shape = ForsaUi.PillShape,
        color = if (selected) ForsaUi.PrimarySoft else ForsaUi.Background,
        contentColor = if (selected) ForsaUi.Primary else ForsaUi.Muted,
        border = BorderStroke(1.dp, if (selected) ForsaUi.Primary.copy(alpha = .18f) else ForsaUi.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ForsaActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ForsaUi.CardShape,
        colors = CardDefaults.cardColors(containerColor = ForsaUi.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(46.dp),
                shape = ForsaUi.SmallShape,
                color = ForsaUi.PrimarySoft,
                contentColor = ForsaUi.Primary
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ForsaUi.Muted)
            }
            Icon(Icons.Default.ArrowForward, null, tint = ForsaUi.Muted)
        }
    }
}

@Composable
private fun ForsaStatusPill(status: String) {
    val (label, bg, fg) = when (status.lowercase()) {
        "accepted", "verified", "paid" -> Triple("تم", ForsaUi.SuccessSoft, ForsaUi.Success)
        "rejected" -> Triple("مرفوض", ForsaUi.DangerSoft, ForsaUi.Danger)
        "pending", "review", "pending_payment" -> Triple("قيد المراجعة", ForsaUi.WarningSoft, ForsaUi.Warning)
        "featured" -> Triple("مميز", ForsaUi.WarningSoft, ForsaUi.Warning)
        else -> Triple(status.ifBlank { "الحالة" }, ForsaUi.PrimarySoft, ForsaUi.Primary)
    }
    Surface(shape = ForsaUi.PillShape, color = bg, contentColor = fg) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}


private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

@Composable
private fun ForsaApp(paymentIntent: Intent? = null) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val auth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    var authScreen by remember {
        mutableStateOf(if (auth.currentUser == null) AuthScreen.Welcome else null)
    }
    var tab by remember { mutableStateOf(MainTab.Home) }
    var loading by remember { mutableStateOf(false) }
    var profileLoaded by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    var profilePhone by remember { mutableStateOf("") }
    var profileCity by remember { mutableStateOf("") }
    var profileRole by remember { mutableStateOf("") }
    var verificationStatus by remember { mutableStateOf("unverified") }
    var verificationNote by remember { mutableStateOf("") }
    var verificationDocumentName by remember { mutableStateOf("") }
    var verificationDocumentPath by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var companyAbout by remember { mutableStateOf("") }
    var companyCity by remember { mutableStateOf("") }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var promotionTarget by remember { mutableStateOf<Job?>(null) }
    var appliedJobIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedJobIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cvProfile by remember { mutableStateOf(CvProfile()) }
    var unreadNotificationsCount by remember { mutableStateOf(0) }
    val db = remember { FirebaseFirestore.getInstance() }
    val paymentApiBaseUrl = BuildConfig.FORSA_PAYMENT_API_BASE_URL

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val verificationDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val user = auth.currentUser ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri).orEmpty()
        val allowed = setOf("application/pdf", "image/jpeg", "image/png")
        if (mime !in allowed) {
            message("ارفع PDF أو JPG أو PNG فقط")
            return@rememberLauncherForActivityResult
        }
        val originalName = queryDisplayName(context, uri) ?: "document"
        val extension = originalName.substringAfterLast('.', "").lowercase().ifBlank {
            when (mime) {
                "application/pdf" -> "pdf"
                "image/jpeg" -> "jpg"
                else -> "png"
            }
        }
        val finalName = "verification_" + System.currentTimeMillis() + "." + extension
        val path = "employerVerificationDocs/" + user.uid + "/" + finalName
        val ref = FirebaseStorage.getInstance().reference.child(path)
        loading = true
        ref.putFile(
            uri,
            com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(mime)
                .build()
        ).addOnSuccessListener {
            db.collection("users").document(user.uid).set(
                mapOf(
                    "verificationDocumentName" to originalName,
                    "verificationDocumentPath" to path,
                    "verificationDocumentUpdatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).addOnSuccessListener {
                verificationDocumentName = originalName
                verificationDocumentPath = path
                loading = false
                message("تم رفع مستند التوثيق")
            }.addOnFailureListener {
                loading = false
                message("تم رفع الملف لكن تعذر حفظ بياناته")
            }
        }.addOnFailureListener {
            loading = false
            message("تعذر رفع مستند التوثيق")
        }
    }

    androidx.compose.runtime.LaunchedEffect(paymentIntent) {
        val uri = paymentIntent?.data
        if (
            uri?.scheme.equals("forsa", ignoreCase = true) &&
            uri?.host.equals("payment", ignoreCase = true)
        ) {
            val orderId = uri?.getQueryParameter("orderId").orEmpty()
            val user = auth.currentUser

            if (orderId.isBlank() || user == null) {
                if (orderId.isNotBlank()) {
                    message("تعذر ربط نتيجة الدفع بالحساب الحالي")
                }
            } else {
                user.getIdToken(false)
                    .addOnSuccessListener { tokenResult ->
                        val idToken = tokenResult.token
                        if (idToken.isNullOrBlank()) {
                            message("تعذر التحقق من جلسة الدفع")
                            return@addOnSuccessListener
                        }

                        scope.launch {
                            try {
                                val result = fetchForsaPaymentStatus(
                                    baseUrl = paymentApiBaseUrl,
                                    idToken = idToken,
                                    orderId = orderId
                                )
                                when (result.status) {
                                    "paid" -> {
                                        promotionTarget = null
                                        tab = MainTab.Profile
                                        message("تم تأكيد الدفع وتفعيل ترقية الإعلان")
                                    }
                                    "failed", "refunded", "partially_refunded" -> {
                                        message("عملية الدفع لم تكتمل: " + result.status)
                                    }
                                    else -> {
                                        message("الدفع قيد التحقق، راح تتحدث الحالة تلقائياً")
                                    }
                                }
                            } catch (exception: Exception) {
                                message(
                                    if (exception.message == "PAYMENT_API_NOT_CONFIGURED") {
                                        "خادم الدفع غير مربوط بعد"
                                    } else {
                                        "تعذر التحقق من نتيجة الدفع"
                                    }
                                )
                            }
                        }
                    }
                    .addOnFailureListener {
                        message("تعذر التحقق من جلسة الدفع")
                    }
            }
        }
    }

    val currentUid = auth.currentUser?.uid

    DisposableEffect(currentUid to profileRole) {
        var jobsRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        var applicationsRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        var savedJobsRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        var notificationsRegistration: com.google.firebase.firestore.ListenerRegistration? = null

        if (currentUid == null) {
            profileLoaded = true
            jobs = emptyList()
            appliedJobIds = emptySet()
            savedJobIds = emptySet()
            cvProfile = CvProfile()
            unreadNotificationsCount = 0
        } else {
            val uid = currentUid
            val authenticatedUser = auth.currentUser

            // لا نفتح الصلاحيات الرئيسية قبل التحقق من الدور المحفوظ.
            profileLoaded = false
            profilePhone = authenticatedUser?.phoneNumber.orEmpty()
            profileCity = ""
            companyName = ""
            companyAbout = ""
            companyCity = ""
            userName = authenticatedUser?.displayName.orEmpty()

            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    val storedRole = document.getString("role")
                    val roleConfirmed = document.getBoolean("roleConfirmed") == true

                    if (
                        (storedRole == "باحث عن عمل" || storedRole == "صاحب عمل") &&
                        roleConfirmed
                    ) {
                        profileRole = storedRole
                        verificationStatus = document.getString("verificationStatus")
                            .orEmpty()
                            .ifBlank { "unverified" }
                        verificationNote = document.getString("verificationNote").orEmpty()
                        verificationDocumentName = document.getString("verificationDocumentName").orEmpty()
                        verificationDocumentPath = document.getString("verificationDocumentPath").orEmpty()
                        profilePhone = document.getString("phone").orEmpty()
                            .ifBlank { auth.currentUser?.phoneNumber.orEmpty() }
                        profileCity = document.getString("city").orEmpty()
                        companyName = document.getString("companyName").orEmpty()
                        companyAbout = document.getString("companyAbout").orEmpty()
                        companyCity = document.getString("companyCity").orEmpty()
                        userName = auth.currentUser?.displayName
                            ?: document.getString("displayName").orEmpty()

                        jobsRegistration = db.collection("jobs")
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    message("تعذر تحميل الوظائف من قاعدة البيانات")
                                    return@addSnapshotListener
                                }

                                jobs = snapshot?.documents
                                    ?.sortedByDescending { it.getLong("createdAt") ?: 0L }
                                    ?.mapNotNull { jobDocument ->
                                        val title = jobDocument.getString("title")
                                            ?: return@mapNotNull null
                                        val company = jobDocument.getString("company")
                                            ?: return@mapNotNull null
                                        val city = jobDocument.getString("city")
                                            ?: return@mapNotNull null
                                        val type = jobDocument.getString("type") ?: "دوام كامل"
                                        val description =
                                            jobDocument.getString("description") ?: ""
                                        val ownerUid =
                                            jobDocument.getString("ownerUid") ?: ""
                                        if (ownerUid.isBlank()) return@mapNotNull null

                                        val storedIsActive =
                                            jobDocument.getBoolean("isActive") ?: true
                                        val createdAt =
                                            jobDocument.getLong("createdAt") ?: 0L
                                        val expiresAt = jobDocument.getLong("expiresAt")
                                            ?: if (createdAt > 0L) {
                                                createdAt + JOB_DEFAULT_EXPIRY_DAYS * MILLIS_PER_DAY
                                            } else 0L
                                        val isExpired = expiresAt > 0L &&
                                            expiresAt <= System.currentTimeMillis()
                                        val isActive = storedIsActive && !isExpired
                                        val isFeatured =
                                            jobDocument.getBoolean("isFeatured") ?: false
                                        val promotionType =
                                            jobDocument.getString("promotionType").orEmpty()
                                        val promotionStatus =
                                            jobDocument.getString("promotionStatus").orEmpty()
                                        val promotionExpiresAt =
                                            jobDocument.getLong("promotionExpiresAt") ?: 0L
                                        val featuredNow = isFeatured && (
                                            promotionExpiresAt == 0L ||
                                                promotionExpiresAt > System.currentTimeMillis()
                                            )

                                        Job(
                                            id = jobDocument.id,
                                            title = title,
                                            company = company,
                                            city = city,
                                            type = type,
                                            description = description,
                                            ownerUid = ownerUid,
                                            createdAt = createdAt,
                                            expiresAt = expiresAt,
                                            isExpired = isExpired,
                                            isActive = isActive,
                                            isFeatured = featuredNow,
                                            promotionType = promotionType,
                                            promotionStatus = promotionStatus,
                                            promotionExpiresAt = promotionExpiresAt
                                        )
                                    }
                                    ?: emptyList()
                            }

                        savedJobsRegistration = db.collection("savedJobs")
                            .whereEqualTo("userUid", uid)
                            .addSnapshotListener { snapshot, _ ->
                                savedJobIds = snapshot?.documents
                                    ?.mapNotNull { it.getString("jobId") }
                                    ?.toSet()
                                    ?: emptySet()
                            }

                        notificationsRegistration = db.collection("notifications")
                            .whereEqualTo("targetUid", uid)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    unreadNotificationsCount = 0
                                    return@addSnapshotListener
                                }

                                unreadNotificationsCount = snapshot?.documents
                                    ?.count { !(it.getBoolean("read") ?: false) }
                                    ?: 0
                            }

                        applicationsRegistration = db.collection("applications")
                            .whereEqualTo("applicantUid", uid)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    appliedJobIds = emptySet()
                                    return@addSnapshotListener
                                }

                                appliedJobIds = snapshot?.documents
                                    ?.mapNotNull { it.getString("jobId") }
                                    ?.toSet()
                                    ?: emptySet()
                            }

                        db.collection("cvProfiles").document(uid).get()
                            .addOnSuccessListener { cvDocument ->
                                cvProfile = CvProfile(
                                    headline = cvDocument.getString("headline").orEmpty(),
                                    about = cvDocument.getString("about").orEmpty(),
                                    education = cvDocument.getString("education").orEmpty(),
                                    experience = cvDocument.getString("experience").orEmpty(),
                                    skills = cvDocument.getString("skills").orEmpty(),
                                    languages = cvDocument.getString("languages").orEmpty(),
                                    fileName = cvDocument.getString("fileName").orEmpty(),
                                    fileSize = cvDocument.getLong("fileSize") ?: 0L,
                                    storagePath = cvDocument.getString("storagePath").orEmpty()
                                )
                            }

                        // المزامنة لا تلمس role أو roleConfirmed حتى يبقى الدور مقفلاً.
                        db.collection("users").document(uid).set(
                            mapOf(
                                "displayName" to userName,
                                "email" to (
                                    auth.currentUser?.email
                                        ?: document.getString("email").orEmpty()
                                    ),
                                "phone" to profilePhone,
                                "city" to profileCity,
                                "companyName" to companyName,
                                "companyAbout" to companyAbout,
                                "companyCity" to companyCity
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).addOnFailureListener {
                            // المزامنة ثانوية ولا تمنع استخدام التطبيق.
                        }

                        profileLoaded = true
                    } else {
                        profileLoaded = true
                        authScreen = AuthScreen.RoleSelection
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    profileLoaded = true
                    authScreen = AuthScreen.Welcome
                    message("تعذر التحقق من نوع الحساب. سجّل الدخول مرة أخرى")
                }
        }

        onDispose {
            jobsRegistration?.remove()
            applicationsRegistration?.remove()
            savedJobsRegistration?.remove()
            notificationsRegistration?.remove()
        }
    }

    fun createNotification(
        targetUid: String,
        type: String,
        title: String,
        body: String,
        jobId: String,
        applicationId: String,
        status: String
    ) {
        if (targetUid.isBlank()) return
        db.collection("notifications").document(UUID.randomUUID().toString()).set(
            mapOf(
                "targetUid" to targetUid,
                "actorUid" to auth.currentUser?.uid.orEmpty(),
                "type" to type,
                "title" to title,
                "body" to body,
                "jobId" to jobId,
                "applicationId" to applicationId,
                "status" to status,
                "read" to false,
                "createdAt" to System.currentTimeMillis()
            )
        )
    }

    fun signedIn() {
        loading = false
        userName = auth.currentUser?.displayName.orEmpty()
        authScreen = null
        tab = MainTab.Home
    }


    fun persistUserBasics(
        role: String? = null,
        onDone: () -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            loading = false
            message("تعذر إنشاء جلسة المستخدم")
            return
        }

        val baseData = mutableMapOf<String, Any>(
            "displayName" to user.displayName.orEmpty(),
            "email" to user.email.orEmpty(),
            "phone" to user.phoneNumber.orEmpty()
        )
        if (role != null) {
            baseData["role"] = role
            baseData["roleConfirmed"] = true
            baseData["verificationStatus"] = "unverified"
            baseData["city"] = ""
            baseData["companyName"] = ""
            baseData["companyAbout"] = ""
            baseData["companyCity"] = ""
        }

        db.collection("users").document(user.uid)
            .set(baseData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if (role != null) profileRole = role
                onDone()
            }
            .addOnFailureListener {
                loading = false
                if (role == null) {
                    // نجاح Firebase Auth كافٍ لفتح الحساب؛ مزامنة البيانات الشخصية يمكن أن تكمل لاحقاً.
                    message("تم تسجيل الدخول، ويمكن إكمال مزامنة الملف لاحقاً")
                    onDone()
                } else {
                    message("تعذر حفظ نوع الحساب، حاول مرة أخرى")
                }
            }
    }

    fun continueAuthenticatedUser() {
        val user = auth.currentUser
        if (user == null) {
            loading = false
            message("تعذر إنشاء جلسة المستخدم")
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val storedRole = document.getString("role")
                val roleConfirmed = document.getBoolean("roleConfirmed") == true
                if (
                    (storedRole == "باحث عن عمل" || storedRole == "صاحب عمل") &&
                    roleConfirmed
                ) {
                    profileRole = storedRole
                    persistUserBasics(onDone = ::signedIn)
                } else {
                    loading = false
                    authScreen = AuthScreen.RoleSelection
                }
            }
            .addOnFailureListener {
                loading = false
                message("تعذر التحقق من نوع الحساب. حاول مرة أخرى")
            }
    }

    fun saveSelectedRole(role: String) {
        if (role != "باحث عن عمل" && role != "صاحب عمل") {
            message("اختر نوع الحساب أولاً")
            return
        }

        loading = true
        profileLoaded = true
        persistUserBasics(role = role, onDone = ::signedIn)
    }

    fun requestEmployerVerification() {
        val user = auth.currentUser
        if (user == null) {
            message("سجّل الدخول أولاً")
            return
        }
        if (profileRole != "صاحب عمل") {
            message("التوثيق متاح لحساب صاحب العمل فقط")
            return
        }
        if (verificationStatus == "verified") {
            message("حساب صاحب العمل موثّق مسبقاً")
            return
        }

        when {
            companyName.trim().length < 2 -> message("أضف اسم الشركة أو الجهة أولاً")
            companyCity.trim().length < 2 -> message("أضف مدينة الشركة أولاً")
            companyAbout.trim().length < 10 -> message("أضف نبذة واضحة عن الشركة أولاً")
            verificationDocumentPath.isBlank() -> message("ارفع مستند إثبات الشركة أولاً")
            else -> {
                loading = true
                db.collection("users").document(user.uid)
                    .set(
                        mapOf(
                            "verificationStatus" to "pending",
                            "verificationRequestedAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        verificationStatus = "pending"
                        verificationNote = ""
                        loading = false
                        message("تم إرسال طلب توثيق صاحب العمل للمراجعة")
                    }
                    .addOnFailureListener {
                        loading = false
                        message("تعذر إرسال طلب التوثيق، حاول مرة أخرى")
                    }
            }
        }
    }

    fun persistAuthenticatedUser(onDone: () -> Unit) {
        // للحسابات الموجودة: نقرأ الدور المحفوظ، وإذا كان مفقوداً نطلب اختياره بدلاً من افتراضه.
        continueAuthenticatedUser()
    }

    suspend fun googleSignIn() {
        if (activity == null) {
            message("تعذر فتح تسجيل Google")
            return
        }

        loading = true
        try {
            // زر Google يستخدم مسار Sign in with Google المباشر حتى لا يعتمد
            // على بيانات اعتماد محفوظة قديمة/محذوفة على الجهاز.
            val option = GetSignInWithGoogleOption.Builder(
                context.getString(R.string.default_web_client_id)
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.idToken, null)

                auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continueAuthenticatedUser()
                    } else {
                        loading = false
                        message(firebaseError(task.exception))
                    }
                }
            } else {
                loading = false
                message("تعذر قراءة حساب Google")
            }
        } catch (_: GetCredentialException) {
            loading = false
            message("تعذر فتح تسجيل الدخول بواسطة Google. جرّب اختيار حساب Google مرة أخرى")
        } catch (_: Exception) {
            loading = false
            message("تعذر تسجيل الدخول بواسطة Google. تأكد من اتصال الإنترنت وإعداد Google في Firebase")
        }
    }

    fun signOut() {
        scope.launch {
            auth.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            userName = ""
            profilePhone = ""
            profileCity = ""
            profileRole = ""
            verificationStatus = "unverified"
            verificationNote = ""
            verificationDocumentName = ""
            verificationDocumentPath = ""
            companyName = ""
            companyAbout = ""
            companyCity = ""
            appliedJobIds = emptySet()
            savedJobIds = emptySet()
            cvProfile = CvProfile()
            unreadNotificationsCount = 0
            selectedJob = null
            promotionTarget = null
            authScreen = AuthScreen.Welcome
        }
    }

    val authState = authScreen
    if (authState != null) {
        when (authState) {
            AuthScreen.Welcome -> WelcomeScreen(
                loading = loading,
                onGoogle = { scope.launch { googleSignIn() } },
                onEmailLogin = { authScreen = AuthScreen.Login },
                onPhone = { authScreen = AuthScreen.Phone },
                onRegister = { authScreen = AuthScreen.Register }
            )

            AuthScreen.Login -> LoginScreen(
                auth = auth,
                loading = loading,
                onLoading = { loading = it },
                onBack = { authScreen = AuthScreen.Welcome },
                onRegister = { authScreen = AuthScreen.Register },
                onForgot = { authScreen = AuthScreen.ResetPassword },
                onSuccess = { continueAuthenticatedUser() },
                onMessage = ::message
            )

            AuthScreen.Register -> RegisterScreen(
                auth = auth,
                loading = loading,
                onLoading = { loading = it },
                onBack = { authScreen = AuthScreen.Welcome },
                onLogin = { authScreen = AuthScreen.Login },
                onSuccess = ::signedIn,
                onMessage = ::message
            )

            AuthScreen.RoleSelection -> RoleSelectionScreen(
                loading = loading,
                onSelect = ::saveSelectedRole
            )

            AuthScreen.Phone -> PhoneAuthScreen(
                activity = activity,
                auth = auth,
                loading = loading,
                onLoading = { loading = it },
                onBack = { authScreen = AuthScreen.Welcome },
                onSuccess = { continueAuthenticatedUser() },
                onMessage = ::message
            )

            AuthScreen.ResetPassword -> ResetScreen(
                auth = auth,
                loading = loading,
                onLoading = { loading = it },
                onBack = { authScreen = AuthScreen.Login },
                onMessage = ::message
            )
        }
        return
    }

    if (!profileLoaded) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("جارٍ تحميل ملف الحساب…")
            }
        }
        return
    }

    MainScaffold(
        currentTab = tab,
        userName = userName,
        phone = profilePhone,
        city = profileCity,
        role = profileRole,
        verificationStatus = verificationStatus,
        verificationNote = verificationNote,
        verificationDocumentName = verificationDocumentName,
        verificationDocumentPicker = verificationDocumentPicker,
        companyName = companyName,
        companyAbout = companyAbout,
        companyCity = companyCity,
        jobs = jobs,
        db = db,
        onMessage = ::message,
        promotionTarget = promotionTarget,
        onPromotionTarget = { promotionTarget = it },
        onPromotionDone = { promotionTarget = null },
        selectedJob = selectedJob,
        appliedJobIds = appliedJobIds,
        savedJobIds = savedJobIds,
        cvProfile = cvProfile,
        unreadNotificationsCount = unreadNotificationsCount,
        onCvSaved = { cvProfile = it },
        onToggleSaved = { job ->
            val user = auth.currentUser
            if (user == null) {
                message("سجّل الدخول أولاً")
            } else {
                val ref = db.collection("savedJobs").document(job.id + "_" + user.uid)
                if (job.id in savedJobIds) {
                    ref.delete()
                        .addOnSuccessListener { savedJobIds = savedJobIds - job.id }
                        .addOnFailureListener { message("تعذر إزالة الوظيفة من المحفوظة") }
                } else {
                    ref.set(
                        mapOf(
                            "jobId" to job.id,
                            "userUid" to user.uid,
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).addOnSuccessListener { savedJobIds = savedJobIds + job.id }
                        .addOnFailureListener { message("تعذر حفظ الوظيفة") }
                }
            }
        },
        onSelectJob = { selectedJob = it },
        onClearSelectedJob = { selectedJob = null },
        onApplyToJob = { job, note ->
            val user = auth.currentUser
            if (user == null) {
                message("سجّل الدخول أولاً")
            } else if (profileRole != "باحث عن عمل") {
                message("بدّل نوع الحساب إلى باحث عن عمل حتى تقدر تقدم")
            } else if (job.expiresAt > 0L && job.expiresAt <= System.currentTimeMillis()) {
                message("انتهت مدة هذا الإعلان والتقديم عليه مغلق")
            } else if (job.ownerUid == user.uid) {
                message("ما تقدر تقدم على إعلانك")
            } else {
                val applicationId = job.id + "_" + user.uid
                val applicationRef = db.collection("applications").document(applicationId)
                val applicationCvFileName = cvProfile.fileName.trim()
                val applicationCvPath = if (
                    cvProfile.storagePath.isNotBlank() && applicationCvFileName.isNotBlank()
                ) {
                    "applicationCvs/" + applicationId + "/" + applicationCvFileName
                } else {
                    ""
                }

                val applicationData = mapOf(
                    "jobId" to job.id,
                    "jobTitle" to job.title,
                    "company" to job.company,
                    "applicantUid" to user.uid,
                    "applicantName" to user.displayName.orEmpty(),
                    "applicantEmail" to user.email.orEmpty(),
                    "employerUid" to job.ownerUid,
                    "note" to note.trim(),
                    "status" to "pending",
                    "createdAt" to System.currentTimeMillis(),
                    "cvHeadline" to cvProfile.headline,
                    "cvAbout" to cvProfile.about,
                    "cvEducation" to cvProfile.education,
                    "cvExperience" to cvProfile.experience,
                    "cvSkills" to cvProfile.skills,
                    "cvLanguages" to cvProfile.languages,
                    "cvPhone" to profilePhone,
                    "cvCity" to profileCity,
                    "cvFileName" to applicationCvFileName,
                    "cvStoragePath" to applicationCvPath
                )

                fun notifyApplicationCreated() {
                    appliedJobIds = appliedJobIds + job.id
                    createNotification(
                        targetUid = job.ownerUid,
                        type = "new_application",
                        title = "طلب تقديم جديد",
                        body = user.displayName.orEmpty().ifBlank { "باحث عن عمل" } +
                            " قدّم على وظيفة " + job.title,
                        jobId = job.id,
                        applicationId = applicationId,
                        status = "pending"
                    )
                    message("تم إرسال طلب التقديم بنجاح")
                    selectedJob = null
                }

                fun createApplicationRecord() {
                    db.runTransaction { transaction ->
                        if (transaction.get(applicationRef).exists()) {
                            throw IllegalStateException("ALREADY_APPLIED")
                        }
                        transaction.set(applicationRef, applicationData)
                        null
                    }.addOnSuccessListener {
                        if (applicationCvPath.isBlank()) {
                            notifyApplicationCreated()
                            return@addOnSuccessListener
                        }

                        FirebaseStorage.getInstance().reference
                            .child(cvProfile.storagePath)
                            .getBytes(10L * 1024L * 1024L)
                            .addOnSuccessListener { bytes ->
                                val extension = applicationCvFileName.substringAfterLast('.', "").lowercase()
                                val contentType = when (extension) {
                                    "pdf" -> "application/pdf"
                                    "doc" -> "application/msword"
                                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    else -> "application/octet-stream"
                                }

                                FirebaseStorage.getInstance().reference
                                    .child(applicationCvPath)
                                    .putBytes(
                                        bytes,
                                        com.google.firebase.storage.StorageMetadata.Builder()
                                            .setContentType(contentType)
                                            .build()
                                    )
                                    .addOnSuccessListener {
                                        notifyApplicationCreated()
                                    }
                                    .addOnFailureListener {
                                        applicationRef.delete().addOnCompleteListener {
                                            message("تم إلغاء التقديم لأن ملف السيرة لم يُجهّز")
                                        }
                                    }
                            }
                            .addOnFailureListener {
                                applicationRef.delete().addOnCompleteListener {
                                    message("تعذر تجهيز ملف السيرة لهذا الطلب")
                                }
                            }
                    }.addOnFailureListener { exception ->
                        if (exception is IllegalStateException &&
                            exception.message == "ALREADY_APPLIED"
                        ) {
                            appliedJobIds = appliedJobIds + job.id
                            message("أنت مقدم على هذه الوظيفة مسبقاً")
                        } else {
                            message("تعذر إرسال طلب التقديم")
                        }
                    }
                }

                createApplicationRecord()
            }
        },
        onTab = { tab = it },
        onLogout = ::signOut,
        onProfileSaved = { newName, newPhone, newCity ->
            val user = auth.currentUser
            if (user == null) {
                message("سجّل الدخول أولاً")
            } else {
                val cleanName = newName.trim()
                val cleanPhone = newPhone.trim()
                val cleanCity = newCity.trim()

                when {
                    cleanName.length < 2 -> message("الاسم يجب أن يكون حرفين على الأقل")
                    cleanPhone.isNotEmpty() && cleanPhone.length < 7 -> message("رقم الهاتف غير صحيح")
                    cleanCity.isNotEmpty() && cleanCity.length < 2 -> message("المدينة غير صحيحة")
                    else -> {
                        loading = true
                        val profile = UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()

                        user.updateProfile(profile).addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                loading = false
                                message("تعذر تحديث بيانات الحساب")
                            } else {
                                db.collection("users").document(user.uid)
                                    .set(
                                        mapOf(
                                            "displayName" to cleanName,
                                            "email" to user.email.orEmpty(),
                                            "phone" to cleanPhone,
                                            "city" to cleanCity,
                                            "role" to profileRole,
                                            "companyName" to companyName,
                                            "companyAbout" to companyAbout,
                                            "companyCity" to companyCity
                                        ),
                                        com.google.firebase.firestore.SetOptions.merge()
                                    )
                                    .addOnSuccessListener {
                                        userName = cleanName
                                        profilePhone = cleanPhone
                                        profileCity = cleanCity
                                        loading = false
                                        message("تم حفظ بيانات الملف الشخصي")
                                    }
                                    .addOnFailureListener {
                                        loading = false
                                        message("تعذر حفظ بيانات الملف الشخصي")
                                    }
                            }
                        }
                    }
                }
            }
        },
        onRequestEmployerVerification = ::requestEmployerVerification,
        onCompanyProfileSaved = { newCompanyName, newCompanyAbout, newCompanyCity ->
            val user = auth.currentUser
            if (user == null) {
                message("سجّل الدخول أولاً")
            } else {
                val cleanName = newCompanyName.trim()
                val cleanAbout = newCompanyAbout.trim()
                val cleanCity = newCompanyCity.trim()

                when {
                    profileRole != "صاحب عمل" -> message("بيانات الشركة متاحة لحساب صاحب العمل")
                    cleanName.length < 2 -> message("اكتب اسم الشركة أو الجهة")
                    cleanCity.length < 2 -> message("اكتب مدينة الشركة")
                    cleanAbout.length < 10 -> message("اكتب نبذة أوضح عن الشركة")
                    else -> {
                        loading = true
                        db.collection("users").document(user.uid)
                            .set(
                                mapOf(
                                    "companyName" to cleanName,
                                    "companyAbout" to cleanAbout,
                                    "companyCity" to cleanCity
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            .addOnSuccessListener {
                                companyName = cleanName
                                companyAbout = cleanAbout
                                companyCity = cleanCity
                                loading = false
                                message("تم حفظ بيانات الشركة")
                            }
                            .addOnFailureListener {
                                loading = false
                                message("تعذر حفظ بيانات الشركة")
                            }
                    }
                }
            }
        },
        onPasswordReset = {
            val emailAddress = auth.currentUser?.email.orEmpty()
            if (emailAddress.isBlank()) {
                message("البريد الإلكتروني غير متوفر")
            } else {
                auth.sendPasswordResetEmail(emailAddress)
                    .addOnSuccessListener {
                        message("تم إرسال رابط تغيير كلمة المرور إلى بريدك")
                    }
                    .addOnFailureListener {
                        message("تعذر إرسال رابط تغيير كلمة المرور")
                    }
            }
        },
        onPublish = { job ->
            if (profileRole != "صاحب عمل") {
                message("نشر الوظائف متاح لحساب صاحب العمل")
            } else if (verificationStatus != "verified") {
                message("لازم توثّق حساب صاحب العمل قبل نشر الوظائف")
            } else {
                db.collection("jobs").document(job.id).set(
                    mapOf(
                        "title" to job.title,
                        "company" to job.company,
                        "city" to job.city,
                        "type" to job.type,
                        "description" to job.description,
                        "ownerUid" to job.ownerUid,
                        "createdAt" to System.currentTimeMillis(),
                        "expiresAt" to job.expiresAt,
                        "isActive" to true
                    )
                ).addOnSuccessListener {
                    tab = MainTab.Jobs
                    message("تم نشر الوظيفة بنجاح")
                }.addOnFailureListener {
                    message("تعذر نشر الوظيفة، تأكد من إعداد Firestore")
                }
            }
        },
        onDeleteJob = { job ->
            if (profileRole != "صاحب عمل" || job.ownerUid != auth.currentUser?.uid) {
                message("ما عندك صلاحية لحذف هذا الإعلان")
            } else {
                db.collection("jobs").document(job.id).delete()
                    .addOnSuccessListener {
                        message("تم حذف الوظيفة")
                    }
                    .addOnFailureListener {
                        message("تعذر حذف الوظيفة")
                    }
            }
        },
        onEditJob = { job ->
            if (profileRole != "صاحب عمل" || job.ownerUid != auth.currentUser?.uid) {
                message("ما عندك صلاحية لتعديل هذا الإعلان")
            } else {
                db.collection("jobs").document(job.id)
                    .update(
                        mapOf(
                            "title" to job.title,
                            "company" to job.company,
                            "city" to job.city,
                            "type" to job.type,
                            "description" to job.description
                        )
                    )
                    .addOnSuccessListener {
                        message("تم تحديث الوظيفة بنجاح")
                    }
                    .addOnFailureListener {
                        message("تعذر تحديث الوظيفة")
                    }
            }
        },
        onToggleJobActive = { job ->
            if (profileRole != "صاحب عمل" || job.ownerUid != auth.currentUser?.uid) {
                message("ما عندك صلاحية لتغيير حالة هذا الإعلان")
            } else if (job.isExpired) {
                message("هذا الإعلان منتهي وما يقدر يرجع نشط")
            } else {
                db.collection("jobs").document(job.id)
                    .update("isActive", !job.isActive)
                    .addOnSuccessListener {
                        message(if (job.isActive) "تم إيقاف الإعلان" else "تم تفعيل الإعلان")
                    }
                    .addOnFailureListener {
                        message("تعذر تغيير حالة الإعلان")
                    }
            }
        },
        onPromoteJob = { job ->
            if (profileRole != "صاحب عمل" || job.ownerUid != auth.currentUser?.uid) {
                message("ما عندك صلاحية لترقية هذا الإعلان")
            } else {
                promotionTarget = job
            }
        }
    )
}

@Composable
private fun RoleChoiceSection(
    selectedRole: String?,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("اختار نوع الحساب", style = MaterialTheme.typography.titleLarge)
        Text(
            "اختيارك يحدد الصلاحيات والواجهة المناسبة لك.",
            style = MaterialTheme.typography.bodyMedium,
            color = ForsaUi.Muted
        )
        listOf(
            Triple("باحث عن عمل", "اكتشف الوظائف وقدّم على الفرص وتابع طلباتك.", Icons.Default.Person),
            Triple("صاحب عمل", "انشر الوظائف وأدر المتقدمين ووسّع وصول إعلانك.", Icons.Default.BusinessCenter)
        ).forEach { (option, description, icon) ->
            val selected = selectedRole == option
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(option) },
                shape = ForsaUi.CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) ForsaUi.PrimarySoft else ForsaUi.Surface
                ),
                border = if (selected) BorderStroke(1.5.dp, ForsaUi.Primary.copy(alpha = .40f)) else BorderStroke(1.dp, ForsaUi.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.size(50.dp),
                        shape = ForsaUi.FieldShape,
                        color = if (selected) ForsaUi.Primary else ForsaUi.Background,
                        contentColor = if (selected) Color.White else ForsaUi.Primary
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(23.dp))
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option, style = MaterialTheme.typography.titleMedium)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ForsaUi.Muted,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, null, tint = ForsaUi.Primary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionScreen(
    loading: Boolean,
    onSelect: (String) -> Unit
) {
    var selectedRole by remember { mutableStateOf<String?>(null) }

    AuthScaffold {
        AuthHeader(
            "اختيار نوع الحساب",
            "هذه الخطوة مطلوبة حتى نحدد لك صلاحيات وواجهة الحساب بشكل صحيح.",
            onBack = null
        )
        Spacer(Modifier.height(24.dp))

        RoleChoiceSection(
            selectedRole = selectedRole,
            onSelect = { selectedRole = it }
        )

        Spacer(Modifier.height(22.dp))

        Text(
            "راح تقدر تستخدم وظائف الحساب حسب الدور المختار، لذلك ما راح نكمل بدون تأكيد اختيارك.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = { selectedRole?.let(onSelect) },
            enabled = selectedRole != null && !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("تأكيد نوع الحساب", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    loading: Boolean,
    onGoogle: () -> Unit,
    onEmailLogin: () -> Unit,
    onPhone: () -> Unit,
    onRegister: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = ForsaUi.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            BrandMark()
            Spacer(Modifier.height(18.dp))
            Text("فرصة", style = MaterialTheme.typography.displayMedium)
            Text(
                "مساحتك لاكتشاف فرص العمل وبناء مسارك المهني",
                style = MaterialTheme.typography.bodyLarge,
                color = ForsaUi.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ForsaUi.SheetShape,
                color = ForsaUi.Surface,
                border = BorderStroke(1.dp, ForsaUi.Border)
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ForsaMetaChip(
                        "حساب آمن",
                        icon = { Icon(Icons.Default.Verified, null, Modifier.size(15.dp)) },
                        selected = true
                    )
                    Text("ابدأ بالطريقة اللي تناسبك", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "سجل دخولك أو أنشئ حساب جديد خلال خطوات بسيطة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForsaUi.Muted
                    )
                    Button(
                        onClick = onGoogle,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = ForsaUi.FieldShape
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("المتابعة باستخدام Google")
                        }
                    }
                    OutlinedButton(
                        onClick = onEmailLogin,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = ForsaUi.FieldShape
                    ) {
                        Icon(Icons.Default.Email, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("الدخول بالبريد الإلكتروني")
                    }
                    OutlinedButton(
                        onClick = onPhone,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = ForsaUi.FieldShape
                    ) {
                        Text("المتابعة برقم الهاتف")
                    }
                    AuthDivider()
                    TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                        Text("ليس لديك حساب؟ إنشاء حساب")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "ابدأ حسابك، أكمل ملفك، وخلي فرصتك أقرب.",
                style = MaterialTheme.typography.labelMedium,
                color = ForsaUi.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}


@Composable
private fun BrandMark() {
    Surface(
        Modifier.size(82.dp),
        CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Work,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    currentTab: MainTab,
    userName: String,
    phone: String,
    city: String,
    role: String,
    verificationStatus: String,
    verificationNote: String,
    verificationDocumentName: String,
    verificationDocumentPicker: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
    companyName: String,
    companyAbout: String,
    companyCity: String,
    jobs: List<Job>,
    db: FirebaseFirestore,
    onMessage: (String) -> Unit,
    promotionTarget: Job?,
    onPromotionTarget: (Job?) -> Unit,
    onPromotionDone: () -> Unit,
    selectedJob: Job?,
    appliedJobIds: Set<String>,
    savedJobIds: Set<String>,
    cvProfile: CvProfile,
    unreadNotificationsCount: Int,
    onCvSaved: (CvProfile) -> Unit,
    onToggleSaved: (Job) -> Unit,
    onSelectJob: (Job) -> Unit,
    onClearSelectedJob: () -> Unit,
    onApplyToJob: (Job, String) -> Unit,
    onTab: (MainTab) -> Unit,
    onLogout: () -> Unit,
    onProfileSaved: (String, String, String) -> Unit,
    onCompanyProfileSaved: (String, String, String) -> Unit,
    onRequestEmployerVerification: () -> Unit,
    onPasswordReset: () -> Unit,
    onPublish: (Job) -> Unit,
    onDeleteJob: (Job) -> Unit,
    onEditJob: (Job) -> Unit,
    onToggleJobActive: (Job) -> Unit,
    onPromoteJob: (Job) -> Unit
) {
    val userUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val employerAccount = role == "صاحب عمل"
    val canPublish = employerAccount && verificationStatus == "verified"
    val visibleTabs = MainTab.values().filter { it != MainTab.Publish || employerAccount }

    if (promotionTarget != null && role == "صاحب عمل") {
        PromotionScreen(
            job = promotionTarget,
            db = db,
            userUid = userUid,
            onBack = { onPromotionTarget(null) },
            onDone = onPromotionDone,
            onMessage = onMessage
        )
        return
    }

    Scaffold(
        containerColor = ForsaUi.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(mainTitle(currentTab), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (userName.isNotBlank()) "فرصة • $userName" else "فرصة",
                            style = MaterialTheme.typography.labelMedium,
                            color = ForsaUi.Muted
                        )
                    }
                },
                navigationIcon = {
                    BrandMark()
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = ForsaUi.Background,
                    scrolledContainerColor = ForsaUi.Background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ForsaUi.Surface,
                tonalElevation = 1.dp,
                modifier = Modifier.background(ForsaUi.Surface, ForsaUi.NavShape)
            ) {
                visibleTabs.forEach { item ->
                    NavigationBarItem(
                        selected = currentTab == item,
                        onClick = { onTab(item) },
                        icon = { Icon(tabIcon(item), contentDescription = null) },
                        label = { Text(tabLabel(item), style = MaterialTheme.typography.labelMedium) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = ForsaUi.Primary,
                            selectedTextColor = ForsaUi.Primary,
                            indicatorColor = ForsaUi.PrimarySoft,
                            unselectedIconColor = ForsaUi.Muted,
                            unselectedTextColor = ForsaUi.Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = ForsaUi.Background
        ) {
            when (currentTab) {
                MainTab.Home -> HomeTab(
                    userName = userName,
                    role = role,
                    jobsCount = jobs.count { it.isActive && !it.isExpired },
                    canPublish = canPublish,
                    jobs = jobs,
                    cvProfile = cvProfile,
                    profileCity = city,
                    onJobs = { onTab(MainTab.Jobs) },
                    onPublish = { onTab(MainTab.Publish) },
                    onSelectJob = onSelectJob
                )
                MainTab.Jobs -> JobsTab(
                    jobs = jobs,
                    currentUserJobUid = userUid,
                    role = role,
                    selectedJob = selectedJob,
                    appliedJobIds = appliedJobIds,
                    savedJobIds = savedJobIds,
                    onToggleSaved = onToggleSaved,
                    onSelectJob = onSelectJob,
                    onClearSelectedJob = onClearSelectedJob,
                    onApply = onApplyToJob,
                    onDelete = onDeleteJob
                )
                MainTab.Publish -> {
                    if (canPublish) {
                        PublishTab(
                            userUid = userUid,
                            defaultCompanyName = companyName,
                            defaultCompanyCity = companyCity,
                            onPublish = onPublish,
                            onCancel = { onTab(MainTab.Home) },
                            onMessage = onMessage
                        )
                    } else if (employerAccount) {
                        RoleRequiredScreen(
                            title = "توثيق صاحب العمل مطلوب",
                            description = when (verificationStatus) {
                                "pending" -> "طلب التوثيق قيد المراجعة. بعد الموافقة راح تقدر تنشر الوظائف."
                                "rejected" -> "طلب التوثيق مرفوض حالياً. حدّث بيانات الشركة وأرسل الطلب من جديد."
                                else -> "أكمل بيانات الشركة من الملف الشخصي ثم أرسل طلب التوثيق."
                            },
                            onBack = { onTab(MainTab.Profile) }
                        )
                    } else {
                        RoleRequiredScreen(
                            title = "النشر متاح لصاحب العمل",
                            description = "ميزة نشر الوظائف متاحة لحساب صاحب العمل.",
                            onBack = { onTab(MainTab.Home) }
                        )
                    }
                }
                MainTab.Profile -> ProfileTab(
                    userName = userName,
                    phone = phone,
                    city = city,
                    email = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                    companyName = companyName,
                    companyAbout = companyAbout,
                    companyCity = companyCity,
                    role = role,
                    verificationStatus = verificationStatus,
                    verificationNote = verificationNote,
                    verificationDocumentName = verificationDocumentName,
                    verificationDocumentPicker = verificationDocumentPicker,
                    jobs = jobs,
                    savedJobIds = savedJobIds,
                    cvProfile = cvProfile,
                    unreadNotificationsCount = unreadNotificationsCount,
                    userUid = userUid,
                    db = db,
                    onProfileSaved = onProfileSaved,
                    onCompanyProfileSaved = onCompanyProfileSaved,
                    onRequestEmployerVerification = onRequestEmployerVerification,
                    onPasswordReset = onPasswordReset,
                    onLogout = onLogout,
                    onDeleteJob = onDeleteJob,
                    onEditJob = onEditJob,
                    onToggleJobActive = onToggleJobActive,
                    onPromoteJob = onPromoteJob,
                    onToggleSaved = onToggleSaved,
                    onSelectJob = onSelectJob,
                    onCvSaved = onCvSaved,
                    onMessage = onMessage
                )
            }
        }
    }
}

@Composable
private fun recommendedJobs(
    jobs: List<Job>,
    cvProfile: CvProfile,
    profileCity: String
): List<Job> {
    val terms = (
        cvProfile.skills.split(',', '،', ';', '؛', '\n') +
            cvProfile.headline.split(',', '،', ';', '؛', '\n')
    )
        .map { it.trim().lowercase() }
        .filter { it.length >= 2 }
        .distinct()

    val city = profileCity.trim().lowercase()
    if (terms.isEmpty() && city.isBlank()) return emptyList()

    return jobs.asSequence()
        .filter { it.isActive && !it.isExpired }
        .map { job ->
            val title = job.title.lowercase()
            val description = job.description.lowercase()
            val company = job.company.lowercase()
            val jobCity = job.city.lowercase()
            var score = 0
            terms.forEach { term ->
                if (title.contains(term)) score += 4
                if (description.contains(term)) score += 2
                if (company.contains(term)) score += 1
            }
            if (city.isNotBlank() && jobCity.contains(city)) score += 5
            job to score
        }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<Job, Int>> { it.second }
                .thenByDescending { it.first.isFeatured }
                .thenByDescending { it.first.id }
        )
        .take(5)
        .map { it.first }
        .toList()
}

@Composable
private fun HomeTab(
    userName: String,
    role: String,
    jobsCount: Int,
    canPublish: Boolean,
    jobs: List<Job>,
    cvProfile: CvProfile,
    profileCity: String,
    onJobs: () -> Unit,
    onPublish: () -> Unit,
    onSelectJob: (Job) -> Unit
) {
    val name = userName.trim().ifEmpty { "مستخدم فرصة" }
    val suggestions = if (role == "باحث عن عمل") recommendedJobs(jobs, cvProfile, profileCity) else emptyList()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("مرحباً، $name 👋", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (role == "صاحب عمل") "لوحة فرصك وإعلاناتك" else "اكتشف الفرصة الأقرب لك",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForsaUi.Muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Surface(
                Modifier.size(48.dp),
                shape = CircleShape,
                color = ForsaUi.PrimarySoft,
                contentColor = ForsaUi.Primary
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text((name.firstOrNull() ?: 'م').uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        Surface(
            Modifier.fillMaxWidth(),
            shape = ForsaUi.SheetShape,
            color = Color.Transparent
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(ForsaUi.Gradient, ForsaUi.SheetShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ForsaMetaChip(
                    if (canPublish) "حساب موثق وجاهز للنشر" else "جاهز لاكتشاف الفرص",
                    icon = {
                        Icon(
                            if (canPublish) Icons.Default.Verified else Icons.Default.Work,
                            null,
                            Modifier.size(15.dp)
                        )
                    },
                    selected = true
                )
                Text("خلك أقرب لخطوتك الجاية", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (canPublish) "انشر وظيفة، تابع الطلبات، وروّج إعلانك من نفس المكان."
                    else "ابحث، احفظ الوظائف، وقدّم وتابع حالة طلباتك بدون تعقيد.",
                    color = Color.White.copy(alpha = .84f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = onJobs,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = ForsaUi.FieldShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = ForsaUi.Primary
                        )
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("استكشاف")
                    }
                    if (canPublish) {
                        OutlinedButton(
                            onClick = onPublish,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = ForsaUi.FieldShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("نشر وظيفة")
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SmallStat("وظائف متاحة", jobsCount.toString(), Modifier.weight(1f))
            SmallStat("السوق", "العراق", Modifier.weight(1f))
        }

        ForsaSectionTitle(
            if (canPublish) "وصول سريع" else "اختصاراتك",
            if (canPublish) "الأدوات الأساسية لإدارة حضورك." else "كل ما تحتاجه للوصول إلى فرصك."
        )
        ForsaActionTile(
            title = if (canPublish) "إدارة الوظائف" else "تصفح الوظائف",
            subtitle = if (canPublish) "الإعلانات والطلبات والترويج" else "بحث وفلترة ومشاهدة التفاصيل",
            icon = Icons.Default.Search,
            onClick = onJobs
        )

        if (suggestions.isNotEmpty()) {
            ForsaSectionTitle("مقترحة لك", "اقتراحات حسب السيرة الذاتية والمدينة.")
            suggestions.take(3).forEach { job ->
                Card(
                    Modifier.fillMaxWidth().clickable { onSelectJob(job) },
                    shape = ForsaUi.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ForsaUi.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(46.dp),
                            shape = ForsaUi.FieldShape,
                            color = if (job.isFeatured) ForsaUi.WarningSoft else ForsaUi.PrimarySoft,
                            contentColor = if (job.isFeatured) ForsaUi.Warning else ForsaUi.Primary
                        ) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (job.isFeatured) Icons.Default.RocketLaunch else Icons.Default.BusinessCenter,
                                    null,
                                    Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title, style = MaterialTheme.typography.titleMedium)
                            Text(job.company, color = ForsaUi.Primary, style = MaterialTheme.typography.bodyMedium)
                            Text(job.city + " • " + job.type, color = ForsaUi.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Default.ArrowForward, null, tint = ForsaUi.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}


@Composable
private fun SmallStat(title: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = ForsaUi.CardShape
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JobsTab(
    jobs: List<Job>,
    currentUserJobUid: String,
    role: String,
    selectedJob: Job?,
    appliedJobIds: Set<String>,
    savedJobIds: Set<String>,
    onToggleSaved: (Job) -> Unit,
    onSelectJob: (Job) -> Unit,
    onClearSelectedJob: () -> Unit,
    onApply: (Job, String) -> Unit,
    onDelete: (Job) -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            now = System.currentTimeMillis()
        }
    }

    if (selectedJob != null) {
        val selectedJobExpiredNow = selectedJob.expiresAt > 0L &&
            selectedJob.expiresAt <= now
        val selectedJobForDetails = if (selectedJobExpiredNow) {
            selectedJob.copy(isExpired = true, isActive = false)
        } else {
            selectedJob
        }

        JobDetailsScreen(
            job = selectedJobForDetails,
            isOwner = selectedJobForDetails.ownerUid == currentUserJobUid,
            alreadyApplied = selectedJobForDetails.id in appliedJobIds,
            canApply = role == "باحث عن عمل",
            isSaved = selectedJobForDetails.id in savedJobIds,
            onToggleSaved = { onToggleSaved(selectedJobForDetails) },
            onBack = onClearSelectedJob,
            onApply = onApply
        )
        return
    }

    var query by remember { mutableStateOf("") }
    var cityFilter by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("الكل") }
    var sortOption by remember { mutableStateOf("الأحدث") }

    val filteredJobs = jobs.filter { job ->
        val currentlyOpen = job.isActive && (job.expiresAt == 0L || job.expiresAt > now)
        val visibleToUser = currentlyOpen || (role == "صاحب عمل" && job.ownerUid == currentUserJobUid)
        if (!visibleToUser) return@filter false

        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            job.title.contains(q, ignoreCase = true) ||
            job.company.contains(q, ignoreCase = true) ||
            job.city.contains(q, ignoreCase = true) ||
            job.description.contains(q, ignoreCase = true)

        val cityQ = cityFilter.trim()
        val matchesCity = cityQ.isEmpty() || job.city.contains(cityQ, ignoreCase = true)
        val matchesType = typeFilter == "الكل" || job.type == typeFilter
        matchesQuery && matchesCity && matchesType
    }.let { list ->
        when (sortOption) {
            "الأقرب انتهاءً" -> list.sortedWith(
                compareByDescending<Job> { it.isFeatured }
                    .thenBy { if (it.expiresAt == 0L) Long.MAX_VALUE else it.expiresAt }
                    .thenByDescending { it.createdAt }
            )
            "الأحدث" -> list.sortedWith(
                compareByDescending<Job> { it.isFeatured }
                    .thenByDescending { it.createdAt }
            )
            else -> list.sortedWith(compareByDescending<Job> { it.isFeatured }.thenByDescending { it.createdAt })
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaSectionTitle("اكتشف فرصة", "ابحث بالمسمى أو الشركة أو المدينة، ثم استخدم الفلاتر للوصول للنتيجة المناسبة.")

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ابحث بالمسمى أو الشركة أو المدينة") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            shape = ForsaUi.FieldShape
        )

        OutlinedTextField(
            value = cityFilter,
            onValueChange = { cityFilter = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("فلترة حسب المدينة") },
            singleLine = true,
            trailingIcon = {
                if (cityFilter.isNotBlank()) {
                    TextButton(onClick = { cityFilter = "" }) { Text("مسح") }
                }
            },
            shape = ForsaUi.FieldShape
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("الكل", "دوام كامل", "دوام جزئي", "عن بُعد").forEach { option ->
                FilterChip(
                    selected = typeFilter == option,
                    onClick = { typeFilter = option },
                    label = { Text(option) }
                )
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الترتيب:", fontSize = 13.sp)
            listOf("الأحدث", "الأقرب انتهاءً").forEach { option ->
                FilterChip(
                    selected = sortOption == option,
                    onClick = { sortOption = option },
                    label = { Text(option) }
                )
            }
            if (query.isNotBlank() || cityFilter.isNotBlank() || typeFilter != "الكل" || sortOption != "الأحدث") {
                TextButton(
                    onClick = {
                        query = ""
                        cityFilter = ""
                        typeFilter = "الكل"
                        sortOption = "الأحدث"
                    }
                ) {
                    Text("مسح الكل")
                }
            }
        }

        Text(
            text = "النتائج: " + filteredJobs.size,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (filteredJobs.isEmpty()) {
            EmptyJobs(filtered = jobs.isNotEmpty())
        } else {
            filteredJobs.forEach { job ->
                JobCard(
                    job = job,
                    canDelete = role == "صاحب عمل" && job.ownerUid == currentUserJobUid,
                    alreadyApplied = job.id in appliedJobIds,
                    isSaved = job.id in savedJobIds,
                    onToggleSaved = { onToggleSaved(job) },
                    onOpen = { onSelectJob(job) },
                    onDelete = { onDelete(job) }
                )
            }
        }
    }
}

@Composable
private fun EmptyJobs(filtered: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            Modifier.size(84.dp),
            CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.BusinessCenter,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            if (filtered) "ما لقينا وظائف مطابقة" else "ماكو وظائف مضافة حالياً",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (filtered) "جرّب تغيّر كلمة البحث أو نوع الدوام."
            else "أول ما تننشر وظيفة راح تظهر هنا.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JobCard(
    job: Job,
    canDelete: Boolean,
    alreadyApplied: Boolean,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = ForsaUi.CardShape,
        colors = CardDefaults.cardColors(containerColor = ForsaUi.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (job.isFeatured) 3.dp else 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Surface(
                    Modifier.size(48.dp),
                    shape = ForsaUi.FieldShape,
                    color = if (job.isFeatured) ForsaUi.WarningSoft else ForsaUi.PrimarySoft,
                    contentColor = if (job.isFeatured) ForsaUi.Warning else ForsaUi.Primary
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(if (job.isFeatured) Icons.Default.RocketLaunch else Icons.Default.BusinessCenter, null, Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(job.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (job.isFeatured) ForsaStatusPill("featured")
                    }
                    Text(job.company, color = ForsaUi.Primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isSaved) "إزالة من المحفوظة" else "حفظ الوظيفة",
                        tint = if (isSaved) ForsaUi.Primary else ForsaUi.Muted
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ForsaUi.Danger)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ForsaMetaChip(job.city, icon = { Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp)) })
                ForsaMetaChip(job.type, icon = { Icon(Icons.Default.Schedule, null, Modifier.size(14.dp)) })
                if (job.isExpired) ForsaMetaChip("منتهي") else if (!job.isActive) ForsaMetaChip("موقوف")
            }
            Text(job.description, style = MaterialTheme.typography.bodyMedium, color = ForsaUi.Muted, maxLines = 3)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (job.expiresAt > 0L) {
                    Text(
                        if (job.isExpired) "انتهى " + formatForsaDate(job.expiresAt) else "ينتهي " + formatForsaDate(job.expiresAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (job.isExpired) ForsaUi.Danger else ForsaUi.Muted,
                        modifier = Modifier.weight(1f)
                    )
                } else Spacer(Modifier.weight(1f))
                if (alreadyApplied) {
                    ForsaMetaChip("تم التقديم", icon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp)) }, selected = true)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("عرض التفاصيل", style = MaterialTheme.typography.labelLarge, color = ForsaUi.Primary)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null, tint = ForsaUi.Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun JobDetailsScreen(
    job: Job,
    isOwner: Boolean,
    alreadyApplied: Boolean,
    canApply: Boolean,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onBack: () -> Unit,
    onApply: (Job, String) -> Unit
) {
    var note by remember(job.id) { mutableStateOf("") }
    var submitting by remember(job.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ForsaPageHeader(
            title = "تفاصيل الوظيفة",
            subtitle = job.company,
            onBack = onBack,
            actions = {
                IconButton(onClick = onToggleSaved, modifier = Modifier.size(42.dp)) {
                    Surface(
                        Modifier.fillMaxSize(),
                        shape = ForsaUi.SmallShape,
                        color = ForsaUi.Surface,
                        border = BorderStroke(1.dp, ForsaUi.Border)
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isSaved) "إزالة من المحفوظة" else "حفظ الوظيفة",
                                tint = if (isSaved) ForsaUi.Primary else ForsaUi.Muted
                            )
                        }
                    }
                }
            }
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = ForsaUi.CardShape
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(job.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Text(job.company, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(job.city) })
                    AssistChip(onClick = {}, label = { Text(job.type) })
                }

                Text(
                    if (job.isExpired) {
                        "انتهى الإعلان في " + formatForsaDate(job.expiresAt)
                    } else {
                        "ينتهي الإعلان في " + formatForsaDate(job.expiresAt)
                    },
                    color = if (job.isExpired) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 13.sp
                )

                if (job.isFeatured && job.promotionExpiresAt > 0L) {
                    Text(
                        "الترويج فعال حتى " + formatForsaDate(job.promotionExpiresAt),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider()

                Text("وصف الوظيفة", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(job.description, fontSize = 16.sp, lineHeight = 25.sp)
            }
        }

        when {
            isOwner -> {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text("هذا الإعلان منشور من حسابك.", Modifier.padding(16.dp))
                }
            }

            !canApply -> {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "التقديم متاح من حساب الباحث عن عمل. نوع الحساب ثابت بعد تأكيد التسجيل.",
                        Modifier.padding(16.dp)
                    )
                }
            }

            job.isExpired -> {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "انتهت مدة هذا الإعلان، لذلك التقديم عليه مغلق.",
                        Modifier.padding(16.dp)
                    )
                }
            }

            alreadyApplied -> {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تم إرسال طلبك لهذه الوظيفة.")
                    }
                }
            }

            else -> {
                Text("التقديم على الوظيفة", fontSize = 19.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رسالة قصيرة لصاحب العمل (اختياري)") },
                    minLines = 4
                )

                Button(
                    onClick = {
                        if (!submitting) {
                            submitting = true
                            onApply(job, note)
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = ForsaUi.FieldShape
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("إرسال طلب التقديم", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishTab(
    userUid: String,
    defaultCompanyName: String,
    defaultCompanyCity: String,
    onPublish: (Job) -> Unit,
    onCancel: () -> Unit,
    onMessage: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var company by remember(defaultCompanyName) { mutableStateOf(defaultCompanyName) }
    var city by remember(defaultCompanyCity) { mutableStateOf(defaultCompanyCity) }
    var type by remember { mutableStateOf("دوام كامل") }
    var durationDays by remember { mutableStateOf(JOB_DEFAULT_EXPIRY_DAYS) }
    var description by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("نشر فرصة عمل", "أنشئ إعلاناً واضحاً وسهل البحث", onCancel)

        Text(
            "أدخل المعلومات الأساسية للوظيفة.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المسمى الوظيفي") },
            singleLine = true
        )

        OutlinedTextField(
            value = company,
            onValueChange = { company = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اسم الشركة أو الجهة") },
            singleLine = true
        )

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المدينة") },
            singleLine = true
        )

        Text("نوع الوظيفة", fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("دوام كامل", "دوام جزئي", "عن بُعد").forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option) }
                )
            }
        }

        Text("مدة نشر الإعلان", fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(7L, 14L, 30L, 60L).forEach { days ->
                FilterChip(
                    selected = durationDays == days,
                    onClick = { durationDays = days },
                    label = { Text("$days يوم") }
                )
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("وصف الوظيفة") },
            minLines = 5
        )

        Button(
            onClick = {
                val cleanTitle = title.trim()
                val cleanCompany = company.trim()
                val cleanCity = city.trim()
                val cleanDescription = description.trim()

                when {
                    cleanTitle.length < 2 -> onMessage("اكتب المسمى الوظيفي")
                    cleanCompany.length < 2 -> onMessage("اكتب اسم الشركة أو الجهة")
                    cleanCity.length < 2 -> onMessage("اكتب المدينة")
                    cleanDescription.length < 5 -> onMessage("اكتب وصفاً أوضح للوظيفة")
                    userUid.isBlank() -> onMessage("تعذر التحقق من حسابك")
                    else -> onPublish(
                        Job(
                            id = UUID.randomUUID().toString(),
                            title = cleanTitle,
                            company = cleanCompany,
                            city = cleanCity,
                            type = type,
                            description = cleanDescription,
                            ownerUid = userUid,
                            createdAt = System.currentTimeMillis(),
                            expiresAt = System.currentTimeMillis() + durationDays * MILLIS_PER_DAY,
                            isActive = true
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            Text("نشر الوظيفة", fontSize = 16.sp)
        }

        Text(
            "سيتم حفظ الإعلان مباشرة في قاعدة بيانات فرصة ليظهر للمستخدمين.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoleRequiredScreen(
    title: String,
    description: String,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(88.dp), shape = ForsaUi.SheetShape, color = ForsaUi.PrimarySoft, contentColor = ForsaUi.Primary) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        ForsaCard(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(description, style = MaterialTheme.typography.bodyLarge, color = ForsaUi.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = ForsaUi.FieldShape) { Text("العودة للحساب") }
        }
    }
}

@Composable
private fun PromotionScreen(
    job: Job,
    db: FirebaseFirestore,
    userUid: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onMessage: (String) -> Unit
) {
    val plans = listOf(Triple("boost_7", "مميز 7 أيام", 5000L), Triple("top_7", "تثبيت 7 أيام", 8000L), Triple("urgent_48", "عاجل 48 ساعة", 3000L))
    var selectedPlan by remember { mutableStateOf(plans.first()) }
    var submitting by remember { mutableStateOf(false) }
    var activeOrderId by remember { mutableStateOf("") }
    var paymentStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val paymentApiBaseUrl = BuildConfig.FORSA_PAYMENT_API_BASE_URL

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Surface(Modifier.fillMaxSize(), shape = ForsaUi.SmallShape, color = ForsaUi.Surface, border = BorderStroke(1.dp, ForsaUi.Border)) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowForward, "رجوع") } }
            }
            Spacer(Modifier.width(8.dp))
            Text("روّج إعلانك", style = MaterialTheme.typography.headlineLarge)
        }

        Surface(Modifier.fillMaxWidth(), shape = ForsaUi.SheetShape, color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().background(ForsaUi.Gradient, ForsaUi.SheetShape).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(46.dp), shape = ForsaUi.FieldShape, color = Color.White.copy(alpha = .16f), contentColor = Color.White) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.RocketLaunch, null, Modifier.size(22.dp)) } }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) { Text(job.title, color = Color.White, style = MaterialTheme.typography.titleLarge); Text(job.company, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyMedium) }
                }
                Text("اختر طريقة الترويج المناسبة لإعلانك.", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium)
            }
        }

        ForsaSectionTitle("اختر الترقية", "الأسعار الحالية كما هي، والتصميم فقط تم تحديثه.")
        plans.forEach { plan ->
            val selected = selectedPlan.first == plan.first
            val description = when (plan.first) { "boost_7" -> "شارة مميز لمدة 7 أيام."; "top_7" -> "رفع الإعلان للأعلى لمدة 7 أيام."; else -> "تمييز عاجل لمدة 48 ساعة." }
            Card(Modifier.fillMaxWidth().clickable { selectedPlan = plan }, shape = ForsaUi.CardShape, colors = CardDefaults.cardColors(containerColor = if (selected) ForsaUi.PrimarySoft else ForsaUi.Surface), border = BorderStroke(1.dp, if (selected) ForsaUi.Primary.copy(alpha = .35f) else ForsaUi.Border), elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(46.dp), shape = ForsaUi.FieldShape, color = if (selected) ForsaUi.Primary else ForsaUi.Background, contentColor = if (selected) Color.White else ForsaUi.Primary) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(if (plan.first == "top_7") Icons.Default.LocationOn else if (plan.first == "urgent_48") Icons.Default.Schedule else Icons.Default.RocketLaunch, null, Modifier.size(21.dp)) } }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) { Text(plan.second, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodyMedium, color = ForsaUi.Muted); Text(plan.third.toString() + " د.ع", style = MaterialTheme.typography.labelLarge, color = ForsaUi.Primary, modifier = Modifier.padding(top = 5.dp)) }
                    if (selected) Icon(Icons.Default.CheckCircle, null, tint = ForsaUi.Primary, modifier = Modifier.size(23.dp))
                }
            }
        }

        DisposableEffect(activeOrderId) {
            if (activeOrderId.isBlank()) onDispose { } else {
                val registration = db.collection("promotionOrders").document(activeOrderId).addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    paymentStatus = snapshot.getString("status").orEmpty()
                    if (paymentStatus == "paid") { submitting = false; onMessage("تم الدفع وتفعيل ترقية الإعلان"); onDone() }
                }
                onDispose { registration.remove() }
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = ForsaUi.FieldShape, color = if (paymentStatus.isNotBlank() && paymentStatus != "paid") ForsaUi.WarningSoft else ForsaUi.SecondarySoft, contentColor = if (paymentStatus.isNotBlank() && paymentStatus != "paid") ForsaUi.Warning else ForsaUi.Success) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (paymentStatus.isNotBlank() && paymentStatus != "paid") Icons.Default.Schedule else Icons.Default.Payments, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                Text(when { paymentStatus.isNotBlank() && paymentStatus != "paid" -> "حالة الدفع: " + paymentStatus; paymentApiBaseUrl.isBlank() -> "الدفع جاهز من جهة التطبيق، لكن خادم الدفع غير مربوط حالياً."; else -> "سيتم تحويلك إلى ZainCash لإكمال الدفع." }, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(onClick = {
            if (submitting) return@Button
            if (userUid.isBlank()) { onMessage("سجّل الدخول أولاً"); return@Button }
            if (paymentApiBaseUrl.isBlank()) { onMessage("خادم الدفع غير مربوط بعد"); return@Button }
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) { onMessage("سجّل الدخول أولاً"); return@Button }
            submitting = true; paymentStatus = "payment_initializing"
            user.getIdToken(false).addOnSuccessListener { tokenResult ->
                val idToken = tokenResult.token
                if (idToken.isNullOrBlank()) { submitting = false; paymentStatus = "payment_init_failed"; onMessage("تعذر التحقق من جلسة الحساب"); return@addOnSuccessListener }
                scope.launch {
                    try {
                        val result = startForsaPayment(paymentApiBaseUrl, idToken, job.id, selectedPlan.first)
                        activeOrderId = result.orderId; paymentStatus = "pending_payment"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.redirectUrl)))
                    } catch (exception: Exception) {
                        submitting = false; paymentStatus = "payment_init_failed"
                        onMessage(if (exception.message == "PAYMENT_API_NOT_CONFIGURED") "خادم الدفع غير مربوط بعد" else "تعذر بدء عملية الدفع")
                    }
                }
            }.addOnFailureListener { submitting = false; paymentStatus = "payment_init_failed"; onMessage("تعذر التحقق من جلسة الحساب") }
        }, enabled = !submitting, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ForsaUi.FieldShape) {
            if (submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else { Icon(Icons.Default.Payments, null, Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text("المتابعة إلى ZainCash") }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ProfileTab(
    userName: String,
    phone: String,
    city: String,
    email: String,
    companyName: String,
    companyAbout: String,
    companyCity: String,
    role: String,
    verificationStatus: String,
    verificationNote: String,
    verificationDocumentName: String,
    verificationDocumentPicker: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
    jobs: List<Job>,
    savedJobIds: Set<String>,
    cvProfile: CvProfile,
    unreadNotificationsCount: Int,
    userUid: String,
    db: FirebaseFirestore,
    onProfileSaved: (String, String, String) -> Unit,
    onCompanyProfileSaved: (String, String, String) -> Unit,
    onRequestEmployerVerification: () -> Unit,
    onPasswordReset: () -> Unit,
    onLogout: () -> Unit,
    onDeleteJob: (Job) -> Unit,
    onEditJob: (Job) -> Unit,
    onToggleJobActive: (Job) -> Unit,
    onPromoteJob: (Job) -> Unit,
    onToggleSaved: (Job) -> Unit,
    onSelectJob: (Job) -> Unit,
    onCvSaved: (CvProfile) -> Unit,
    onMessage: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draftName by remember(userName) { mutableStateOf(userName) }
    var draftPhone by remember(phone) { mutableStateOf(phone) }
    var draftCity by remember(city) { mutableStateOf(city) }
    var loggingOut by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("main") }
    var editingJob by remember { mutableStateOf<Job?>(null) }

    if (editingJob != null) {
        EditJobScreen(
            job = editingJob!!,
            onBack = { editingJob = null },
            onSave = { updatedJob ->
                onEditJob(updatedJob)
                editingJob = null
            },
            onMessage = onMessage
        )
        return
    }

    when (section) {
        "company" -> CompanyProfileScreen(
            companyName = companyName,
            companyAbout = companyAbout,
            companyCity = companyCity,
            onBack = { section = "main" },
            onSave = { newName, newAbout, newCity ->
                onCompanyProfileSaved(newName, newAbout, newCity)
                section = "main"
            },
            onMessage = onMessage
        )

        "dashboard" -> EmployerDashboardScreen(
            jobs = jobs,
            userUid = userUid,
            db = db,
            onBack = { section = "main" },
            onJobs = { section = "employerJobs" },
            onApplications = { section = "employerApps" },
            onMessage = onMessage
        )

        "employerJobs" -> EmployerJobsScreen(
            jobs = jobs,
            userUid = userUid,
            db = db,
            onBack = { section = "main" },
            onApplications = { section = "employerApps" },
            onDeleteJob = onDeleteJob,
            onEditJob = { editingJob = it },
            onToggleJobActive = onToggleJobActive,
            onPromoteJob = onPromoteJob,
            onMessage = onMessage
        )

        "notifications" -> NotificationsScreen(
            db = db,
            userUid = userUid,
            onBack = { section = "main" },
            onMessage = onMessage
        )

        "employerApps" -> EmployerApplicationsScreen(
            db = db,
            userUid = userUid,
            onBack = { section = "main" },
            onMessage = onMessage
        )

        "myApps" -> MyApplicationsScreen(
            db = db,
            userUid = userUid,
            onBack = { section = "main" },
            onMessage = onMessage
        )

        "savedJobs" -> SavedJobsScreen(
            jobs = jobs,
            savedJobIds = savedJobIds,
            onBack = { section = "main" },
            onToggleSaved = onToggleSaved,
            onOpen = onSelectJob
        )

        "cv" -> CvProfileScreen(
            profile = cvProfile,
            userUid = userUid,
            db = db,
            onBack = { section = "main" },
            onSaved = onCvSaved,
            onMessage = onMessage
        )

        else -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    Modifier.size(86.dp).align(Alignment.CenterHorizontally),
                    CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (userName.trim().firstOrNull() ?: 'م').uppercase(),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    "الملف الشخصي",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (editing) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الاسم") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draftPhone,
                        onValueChange = { draftPhone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رقم الهاتف (اختياري)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        )
                    )
                    OutlinedTextField(
                        value = draftCity,
                        onValueChange = { draftCity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("المدينة (اختياري)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (draftName.trim().length >= 2 &&
                                    (draftPhone.trim().isEmpty() || draftPhone.trim().length >= 7) &&
                                    (draftCity.trim().isEmpty() || draftCity.trim().length >= 2)
                                ) {
                                    onProfileSaved(
                                        draftName.trim(),
                                        draftPhone.trim(),
                                        draftCity.trim()
                                    )
                                    editing = false
                                } else {
                                    onMessage("راجع الاسم ورقم الهاتف والمدينة")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ")
                        }
                        OutlinedButton(
                            onClick = {
                                draftName = userName
                                draftPhone = phone
                                draftCity = city
                                editing = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء")
                        }
                    }
                } else {
                    ProfileInfo("الاسم", userName.ifBlank { "بدون اسم" })
                    ProfileInfo("البريد", email.ifBlank { "غير متوفر" })
                    ProfileInfo("رقم الهاتف", phone.ifBlank { "غير مضاف" })
                    ProfileInfo("المدينة", city.ifBlank { "غير مضافة" })
                    ProfileInfo("نوع الحساب", role)
                    OutlinedButton(
                        onClick = { editing = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = ForsaUi.FieldShape
                    ) {
                        Icon(Icons.Default.Person, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تعديل الملف الشخصي")
                    }
                    OutlinedButton(
                        onClick = onPasswordReset,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = ForsaUi.FieldShape
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تغيير كلمة المرور")
                    }
                }

                ProfileActionCard(
                    title = "الإشعارات",
                    description = if (unreadNotificationsCount > 0) {
                        "عندك " + unreadNotificationsCount + " إشعار غير مقروء."
                    } else {
                        "ما عندك إشعارات غير مقروءة حالياً."
                    },
                    actionLabel = "فتح الإشعارات",
                    onClick = { section = "notifications" }
                )

                ProfileActionCard(
                    title = "المحفوظة",
                    description = "الوظائف اللي حفظتها حتى ترجع لها لاحقاً.",
                    actionLabel = "فتح المحفوظة",
                    onClick = { section = "savedJobs" }
                )

                ProfileActionCard(
                    title = "السيرة الذاتية",
                    description = cvCompletionText(cvProfile),
                    actionLabel = "إدارة السيرة الذاتية",
                    onClick = { section = "cv" }
                )

                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("نوع الحساب", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            role,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "نوع الحساب يُحدد عند التسجيل ويُحفظ للحساب.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                if (role == "صاحب عمل") {
                    val verificationDescription = when (verificationStatus) {
                        "verified" -> "حساب صاحب العمل موثّق وتقدر تنشر الوظائف."
                        "pending" -> "طلب التوثيق قيد المراجعة."
                        "rejected" -> "تم رفض الطلب. راجع بيانات الشركة وأرسل الطلب من جديد."
                        else -> "حساب صاحب العمل غير موثّق بعد. أكمل بيانات الشركة ثم اطلب التوثيق."
                    }
                    ProfileActionCard(
                        title = "توثيق صاحب العمل",
                        description = verificationDescription +
                            if (verificationNote.isNotBlank()) "\nملاحظة: $verificationNote" else "",
                        actionLabel = when (verificationStatus) {
                            "verified" -> "موثّق ✓"
                            "pending" -> "قيد المراجعة"
                            "rejected" -> "إعادة طلب التوثيق"
                            else -> "طلب التوثيق"
                        },
                        onClick = {
                            when (verificationStatus) {
                                "verified" -> onMessage("حساب صاحب العمل موثّق")
                                "pending" -> onMessage("طلب التوثيق قيد المراجعة")
                                else -> onRequestEmployerVerification()
                            }
                        }
                    )
                    ProfileActionCard(
                        title = "مستند إثبات الشركة",
                        description = if (verificationDocumentName.isBlank()) {
                            "ارفع مستنداً يثبت بيانات الشركة حتى تقدر الإدارة تراجع طلب التوثيق."
                        } else {
                            "المستند المرفوع: $verificationDocumentName"
                        },
                        actionLabel = if (verificationDocumentName.isBlank()) "رفع المستند" else "استبدال المستند",
                        onClick = { verificationDocumentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }
                    )
                    ProfileActionCard(
                        title = "ملف الشركة",
                        description = if (companyName.isBlank()) {
                            "أضف اسم الشركة ونبذة عنها حتى تكون بياناتك جاهزة عند نشر الوظائف."
                        } else {
                            "الشركة: $companyName" + if (companyCity.isNotBlank()) " — $companyCity" else ""
                        },
                        actionLabel = "إدارة ملف الشركة",
                        onClick = { section = "company" }
                    )
                    ProfileActionCard(
                        title = "لوحة صاحب العمل",
                        description = "ملخص سريع لإعلاناتك وطلبات المتقدمين وحالاتها.",
                        actionLabel = "فتح اللوحة",
                        onClick = { section = "dashboard" }
                    )
                    ProfileActionCard(
                        title = "إعلاناتي",
                        description = "شوف الوظائف اللي نشرتها وإدارتها.",
                        actionLabel = "فتح الإعلانات",
                        onClick = { section = "employerJobs" }
                    )
                    ProfileActionCard(
                        title = "طلبات التقديم",
                        description = "شوف المتقدمين على وظائفك وغيّر حالة الطلب.",
                        actionLabel = "فتح الطلبات",
                        onClick = { section = "employerApps" }
                    )
                } else {
                    ProfileActionCard(
                        title = "طلباتي",
                        description = "تابع الوظائف اللي قدمت عليها وحالة كل طلب.",
                        actionLabel = "فتح طلباتي",
                        onClick = { section = "myApps" }
                    )
                }

                HorizontalDivider()

                TextButton(
                    onClick = { loggingOut = true },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("تسجيل الخروج")
                }

                if (loggingOut) {
                    AlertDialog(
                        onDismissRequest = { loggingOut = false },
                        title = { Text("تسجيل الخروج؟") },
                        text = { Text("راح يتم تسجيل الخروج من حساب فرصة على هذا الجهاز.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    loggingOut = false
                                    onLogout()
                                }
                            ) {
                                Text("تسجيل الخروج")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { loggingOut = false }) {
                                Text("إلغاء")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionCard(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    val icon = when {
        title.contains("الإشعارات") -> Icons.Default.Notifications
        title.contains("المحفوظة") -> Icons.Default.Bookmark
        title.contains("السيرة") -> Icons.Default.Description
        title.contains("الشركة") || title.contains("التوثيق") -> Icons.Default.BusinessCenter
        else -> Icons.Default.ArrowForward
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ForsaUi.CardShape,
        colors = CardDefaults.cardColors(containerColor = ForsaUi.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = ForsaUi.SmallShape, color = ForsaUi.PrimarySoft, contentColor = ForsaUi.Primary) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = ForsaUi.Muted, maxLines = 2)
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = ForsaUi.Primary, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.Default.ArrowForward, null, tint = ForsaUi.Muted)
        }
    }
}

@Composable
private fun CompanyProfileScreen(
    companyName: String,
    companyAbout: String,
    companyCity: String,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onMessage: (String) -> Unit
) {
    var name by remember(companyName) { mutableStateOf(companyName) }
    var about by remember(companyAbout) { mutableStateOf(companyAbout) }
    var city by remember(companyCity) { mutableStateOf(companyCity) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("ملف الشركة", "حدّث بيانات شركتك وخلّيها جاهزة للباحثين عن عمل", onBack)

        Text(
            "احفظ معلومات شركتك مرة واحدة حتى تكون جاهزة عند نشر الوظائف.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اسم الشركة أو الجهة") },
            singleLine = true
        )

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("مدينة الشركة") },
            singleLine = true
        )

        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("نبذة عن الشركة") },
            minLines = 5
        )

        Button(
            onClick = {
                val cleanName = name.trim()
                val cleanAbout = about.trim()
                val cleanCity = city.trim()

                when {
                    cleanName.length < 2 -> onMessage("اكتب اسم الشركة أو الجهة")
                    cleanCity.length < 2 -> onMessage("اكتب مدينة الشركة")
                    cleanAbout.length < 10 -> onMessage("اكتب نبذة أوضح عن الشركة")
                    else -> onSave(cleanName, cleanAbout, cleanCity)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            Text("حفظ ملف الشركة", fontSize = 16.sp)
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = ForsaUi.FieldShape
        ) {
            Text("إلغاء")
        }
    }
}

@Composable
private fun EmployerDashboardScreen(
    jobs: List<Job>,
    userUid: String,
    db: FirebaseFirestore,
    onBack: () -> Unit,
    onJobs: () -> Unit,
    onApplications: () -> Unit,
    onMessage: (String) -> Unit
) {
    var applications by remember { mutableStateOf<List<DashboardApplication>>(emptyList()) }

    DisposableEffect(userUid) {
        if (userUid.isBlank()) {
            onDispose { }
        } else {
            val registration = db.collection("applications")
                .whereEqualTo("employerUid", userUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        applications = emptyList()
                        onMessage("تعذر تحميل إحصائيات لوحة صاحب العمل")
                        return@addSnapshotListener
                    }

                    applications = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val jobId = doc.getString("jobId").orEmpty()
                            if (jobId.isBlank()) return@mapNotNull null

                            DashboardApplication(
                                jobId = jobId,
                                jobTitle = doc.getString("jobTitle").orEmpty(),
                                applicantName = doc.getString("applicantName").orEmpty().ifBlank { "متقدم" },
                                status = doc.getString("status").orEmpty().ifBlank { "pending" },
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                }

            onDispose { registration.remove() }
        }
    }

    val myJobs = jobs.filter { it.ownerUid == userUid }
    val activeJobs = myJobs.count { it.isActive }
    val pausedJobs = myJobs.size - activeJobs
    val totalApplications = applications.size
    val pendingApplications = applications.count { it.status == "pending" }
    val acceptedApplications = applications.count { it.status == "accepted" }
    val rejectedApplications = applications.count { it.status == "rejected" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("لوحة صاحب العمل", "نظرة سريعة على إعلاناتك وطلبات التقديم", onBack)

        Text(
            "ملخص حسابك في مكان واحد.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardStatCard(
                title = "إجمالي الوظائف",
                value = myJobs.size.toString(),
                modifier = Modifier.weight(1f)
            )
            DashboardStatCard(
                title = "نشطة",
                value = activeJobs.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardStatCard(
                title = "موقوفة",
                value = pausedJobs.toString(),
                modifier = Modifier.weight(1f)
            )
            DashboardStatCard(
                title = "إجمالي الطلبات",
                value = totalApplications.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = ForsaUi.CardShape
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("حالات الطلبات", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("قيد المراجعة: $pendingApplications")
                Text("مقبول: $acceptedApplications")
                Text("مرفوض: $rejectedApplications")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onJobs,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = ForsaUi.FieldShape
            ) {
                Text("إعلاناتي")
            }
            OutlinedButton(
                onClick = onApplications,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = ForsaUi.FieldShape
            ) {
                Text("الطلبات")
            }
        }

        Text("آخر الطلبات", fontSize = 19.sp, fontWeight = FontWeight.Bold)

        if (applications.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = ForsaUi.FieldShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "ماكو طلبات تقديم حالياً. أول ما يتقدم شخص راح يظهر هنا.",
                    Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            applications.take(5).forEach { application ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            application.jobTitle.ifBlank { "وظيفة" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(application.applicantName)
                        StatusBadge(application.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = ForsaUi.CardShape, colors = CardDefaults.cardColors(containerColor = ForsaUi.Surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = ForsaUi.Primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = ForsaUi.Muted)
        }
    }
}

@Composable
private fun EmployerJobsScreen(
    jobs: List<Job>,
    userUid: String,
    db: FirebaseFirestore,
    onBack: () -> Unit,
    onApplications: () -> Unit,
    onDeleteJob: (Job) -> Unit,
    onEditJob: (Job) -> Unit,
    onToggleJobActive: (Job) -> Unit,
    onPromoteJob: (Job) -> Unit,
    onMessage: (String) -> Unit
) {
    var applicationCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var deleteTarget by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(userUid) {
        if (userUid.isBlank()) {
            onDispose { }
        } else {
            val registration = db.collection("applications")
                .whereEqualTo("employerUid", userUid)
                .addSnapshotListener { snapshot, _ ->
                    applicationCounts = snapshot?.documents
                        ?.mapNotNull { it.getString("jobId") }
                        ?.groupingBy { it }
                        ?.eachCount()
                        ?: emptyMap()
                }

            onDispose { registration.remove() }
        }
    }

    val myJobs = jobs.filter { it.ownerUid == userUid }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("إعلاناتي", "إدارة الإعلانات والترويج والحالة", onBack)

        if (myJobs.isEmpty()) {
            Text(
                "ما عندك إعلانات منشورة حالياً.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            myJobs.forEach { job ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.CardShape
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(job.title, style = MaterialTheme.typography.titleLarge)
                        Text(job.company, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            shape = ForsaUi.SmallShape,
                            color = if (job.isActive) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                when {
                                    job.isExpired -> "الإعلان منتهي"
                                    job.isActive -> "الإعلان ظاهر للباحثين"
                                    else -> "الإعلان موقوف"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(job.city) })
                            AssistChip(onClick = {}, label = { Text(job.type) })
                        }
                        Text(
                            "طلبات التقديم: ${applicationCounts[job.id] ?: 0}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            job.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEditJob(job) },
                                modifier = Modifier.weight(1f),
                                shape = ForsaUi.FieldShape
                            ) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(Modifier.width(6.dp))
                                Text("تعديل")
                            }
                            Button(
                                onClick = onApplications,
                                modifier = Modifier.weight(1f),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text("الطلبات")
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPromoteJob(job) },
                                modifier = Modifier.weight(1f),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text(if (job.isFeatured) "مميز" else "ترقية الإعلان")
                            }
                            OutlinedButton(
                                onClick = { onToggleJobActive(job) },
                                enabled = !job.isExpired,
                                modifier = Modifier.weight(1f),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text(
                                    when {
                                        job.isExpired -> "منتهي"
                                        job.isActive -> "إيقاف الإعلان"
                                        else -> "تفعيل الإعلان"
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = { deleteTarget = job },
                                modifier = Modifier.weight(1f),
                                shape = ForsaUi.FieldShape
                            ) {
                                Icon(Icons.Default.Delete, null)
                                Spacer(Modifier.width(6.dp))
                                Text("حذف نهائي")
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الإعلان؟") },
            text = { Text("راح ينحذف هذا الإعلان من قائمة الوظائف.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteJob(job)
                    }
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun EditJobScreen(
    job: Job,
    onBack: () -> Unit,
    onSave: (Job) -> Unit,
    onMessage: (String) -> Unit
) {
    var title by remember(job.id) { mutableStateOf(job.title) }
    var company by remember(job.id) { mutableStateOf(job.company) }
    var city by remember(job.id) { mutableStateOf(job.city) }
    var type by remember(job.id) { mutableStateOf(job.type) }
    var description by remember(job.id) { mutableStateOf(job.description) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("تعديل الوظيفة", "حدّث معلومات الإعلان بدون فقدان بياناته", onBack)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المسمى الوظيفي") },
            singleLine = true
        )

        OutlinedTextField(
            value = company,
            onValueChange = { company = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اسم الشركة أو الجهة") },
            singleLine = true
        )

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المدينة") },
            singleLine = true
        )

        Text("نوع الوظيفة", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("دوام كامل", "دوام جزئي", "عن بُعد").forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option) }
                )
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("وصف الوظيفة") },
            minLines = 5
        )

        Button(
            onClick = {
                val cleanTitle = title.trim()
                val cleanCompany = company.trim()
                val cleanCity = city.trim()
                val cleanDescription = description.trim()
                when {
                    cleanTitle.length < 2 -> onMessage("اكتب المسمى الوظيفي")
                    cleanCompany.length < 2 -> onMessage("اكتب اسم الشركة أو الجهة")
                    cleanCity.length < 2 -> onMessage("اكتب المدينة")
                    cleanDescription.length < 5 -> onMessage("اكتب وصفاً أوضح للوظيفة")
                    type !in listOf("دوام كامل", "دوام جزئي", "عن بُعد") -> onMessage("اختر نوع الوظيفة")
                    else -> onSave(
                        job.copy(
                            title = cleanTitle,
                            company = cleanCompany,
                            city = cleanCity,
                            type = type,
                            description = cleanDescription
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            Text("حفظ التعديلات", fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmployerApplicationsScreen(
    db: FirebaseFirestore,
    userUid: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    var applications by remember { mutableStateOf<List<ApplicationItem>>(emptyList()) }
    var statusFilter by remember { mutableStateOf("الكل") }
    var selectedCv by remember { mutableStateOf<ApplicationItem?>(null) }
    val context = LocalContext.current
    val storage = remember { FirebaseStorage.getInstance() }

    val filteredApplications = applications.filter { app ->
        statusFilter == "الكل" || app.status == statusFilter
    }
    val pendingCount = applications.count { it.status == "pending" }
    val acceptedCount = applications.count { it.status == "accepted" }
    val rejectedCount = applications.count { it.status == "rejected" }

    DisposableEffect(userUid) {
        if (userUid.isBlank()) {
            onDispose { }
        } else {
            val registration = db.collection("applications")
                .whereEqualTo("employerUid", userUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onMessage("تعذر تحميل طلبات التقديم")
                        applications = emptyList()
                        return@addSnapshotListener
                    }

                    applications = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val jobId = doc.getString("jobId") ?: return@mapNotNull null
                            ApplicationItem(
                                id = doc.id,
                                jobId = jobId,
                                applicantUid = doc.getString("applicantUid").orEmpty(),
                                jobTitle = doc.getString("jobTitle").orEmpty(),
                                company = doc.getString("company").orEmpty(),
                                applicantName = doc.getString("applicantName").orEmpty(),
                                applicantEmail = doc.getString("applicantEmail").orEmpty(),
                                note = doc.getString("note").orEmpty(),
                                status = doc.getString("status") ?: "pending",
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                cvHeadline = doc.getString("cvHeadline").orEmpty(),
                                cvAbout = doc.getString("cvAbout").orEmpty(),
                                cvEducation = doc.getString("cvEducation").orEmpty(),
                                cvExperience = doc.getString("cvExperience").orEmpty(),
                                cvSkills = doc.getString("cvSkills").orEmpty(),
                                cvLanguages = doc.getString("cvLanguages").orEmpty(),
                                cvPhone = doc.getString("cvPhone").orEmpty(),
                                cvCity = doc.getString("cvCity").orEmpty(),
                                cvFileName = doc.getString("cvFileName").orEmpty(),
                                cvStoragePath = doc.getString("cvStoragePath").orEmpty()
                            )
                        }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                }

            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("طلبات التقديم", "راجع المتقدمين وتابع مراحل الطلبات", onBack)

        if (applications.isNotEmpty()) {
            Text(
                "الإجمالي ${applications.size}  •  قيد المراجعة ${pendingCount}  •  مقبول ${acceptedCount}  •  مرفوض ${rejectedCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "الكل" to "الكل",
                    "pending" to "قيد المراجعة",
                    "accepted" to "مقبول",
                    "rejected" to "مرفوض"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = statusFilter == value,
                        onClick = { statusFilter = value },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (applications.isEmpty()) {
            Text(
                "ما وصلت طلبات تقديم حالياً.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (filteredApplications.isEmpty()) {
            Text(
                "ماكو طلبات ضمن هذا الفلتر.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredApplications.forEach { app ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.CardShape
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(app.jobTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(app.company, color = MaterialTheme.colorScheme.primary)
                        Text("المتقدم: ${app.applicantName.ifBlank { "بدون اسم" }}")
                        Text(
                            app.applicantEmail.ifBlank { "بدون بريد" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (app.note.isNotBlank()) {
                            Text(
                                "رسالة المتقدم",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(app.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        val hasCv = app.cvHeadline.isNotBlank() ||
                            app.cvAbout.isNotBlank() ||
                            app.cvEducation.isNotBlank() ||
                            app.cvExperience.isNotBlank() ||
                            app.cvSkills.isNotBlank() ||
                            app.cvLanguages.isNotBlank() ||
                            app.cvPhone.isNotBlank() ||
                            app.cvCity.isNotBlank()

                        if (hasCv) {
                            OutlinedButton(
                                onClick = { selectedCv = app },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text("عرض السيرة الذاتية")
                            }
                        }

                        if (app.cvStoragePath.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    storage.reference.child(app.cvStoragePath).downloadUrl
                                        .addOnSuccessListener { url ->
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                            } catch (_: Exception) {
                                                onMessage("تعذر فتح ملف السيرة")
                                            }
                                        }
                                        .addOnFailureListener {
                                            onMessage("تعذر الوصول إلى ملف السيرة")
                                        }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text(
                                    "فتح ملف CV" +
                                        if (app.cvFileName.isNotBlank()) " — " + app.cvFileName else ""
                                )
                            }
                        }

                        StatusBadge(app.status)

                        if (app.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        db.collection("applications").document(app.id)
                                            .update("status", "accepted")
                                            .addOnSuccessListener {
                                                db.collection("notifications").document(UUID.randomUUID().toString()).set(
                                                    mapOf(
                                                        "targetUid" to app.applicantUid,
                                                        "actorUid" to userUid,
                                                        "type" to "application_status",
                                                        "title" to "تم قبول طلبك",
                                                        "body" to "تم قبول طلبك على وظيفة " + app.jobTitle,
                                                        "jobId" to app.jobId,
                                                        "applicationId" to app.id,
                                                        "status" to "accepted",
                                                        "read" to false,
                                                        "createdAt" to System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            .addOnFailureListener {
                                                onMessage("تعذر قبول الطلب")
                                            }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = ForsaUi.FieldShape
                                ) {
                                    Text("قبول")
                                }
                                OutlinedButton(
                                    onClick = {
                                        db.collection("applications").document(app.id)
                                            .update("status", "rejected")
                                            .addOnSuccessListener {
                                                db.collection("notifications").document(UUID.randomUUID().toString()).set(
                                                    mapOf(
                                                        "targetUid" to app.applicantUid,
                                                        "actorUid" to userUid,
                                                        "type" to "application_status",
                                                        "title" to "تم رفض طلبك",
                                                        "body" to "تم رفض طلبك على وظيفة " + app.jobTitle,
                                                        "jobId" to app.jobId,
                                                        "applicationId" to app.id,
                                                        "status" to "rejected",
                                                        "read" to false,
                                                        "createdAt" to System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            .addOnFailureListener {
                                                onMessage("تعذر رفض الطلب")
                                            }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = ForsaUi.FieldShape
                                ) {
                                    Text("رفض")
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    db.collection("applications").document(app.id)
                                            .update("status", "pending")
                                            .addOnSuccessListener {
                                                db.collection("notifications").document(UUID.randomUUID().toString()).set(
                                                    mapOf(
                                                        "targetUid" to app.applicantUid,
                                                        "actorUid" to userUid,
                                                        "type" to "application_status",
                                                        "title" to "عاد طلبك للمراجعة",
                                                        "body" to "تمت إعادة طلبك للمراجعة على وظيفة " + app.jobTitle,
                                                        "jobId" to app.jobId,
                                                        "applicationId" to app.id,
                                                        "status" to "pending",
                                                        "read" to false,
                                                        "createdAt" to System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            .addOnFailureListener {
                                            onMessage("تعذر إعادة الطلب للمراجعة")
                                        }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text("إعادة للمراجعة")
                            }
                        }
                    }
                }
            }
        }
    }
    selectedCv?.let { app ->
        CvSnapshotDialog(
            app = app,
            onClose = { selectedCv = null }
        )
    }
}

@Composable
private fun CvSnapshotDialog(
    app: ApplicationItem,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text("السيرة — " + app.applicantName.ifBlank { "متقدم" })
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CvSnapshotField("المسمى المهني", app.cvHeadline)
                CvSnapshotField("المدينة", app.cvCity)
                CvSnapshotField("الهاتف", app.cvPhone)
                CvSnapshotField("نبذة", app.cvAbout)
                CvSnapshotField("التعليم", app.cvEducation)
                CvSnapshotField("الخبرة", app.cvExperience)
                CvSnapshotField("المهارات", app.cvSkills)
                CvSnapshotField("اللغات", app.cvLanguages)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
private fun CvSnapshotField(label: String, value: String) {
    if (value.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotificationsScreen(
    db: FirebaseFirestore,
    userUid: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }

    DisposableEffect(userUid) {
        if (userUid.isBlank()) {
            onDispose { }
        } else {
            val registration = db.collection("notifications")
                .whereEqualTo("targetUid", userUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        notifications = emptyList()
                        onMessage("تعذر تحميل الإشعارات")
                        return@addSnapshotListener
                    }

                    notifications = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val title = doc.getString("title").orEmpty()
                            val body = doc.getString("body").orEmpty()
                            if (title.isBlank() && body.isBlank()) return@mapNotNull null
                            NotificationItem(
                                id = doc.id,
                                type = doc.getString("type").orEmpty(),
                                title = title,
                                body = body,
                                jobId = doc.getString("jobId").orEmpty(),
                                applicationId = doc.getString("applicationId").orEmpty(),
                                status = doc.getString("status").orEmpty(),
                                read = doc.getBoolean("read") ?: false,
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                }

            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("الإشعارات", "آخر تحديثات حسابك وطلباتك", onBack)

        if (notifications.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = ForsaUi.CardShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text("ماكو إشعارات حالياً.", Modifier.padding(16.dp))
            }
        } else {
            notifications.forEach { notification ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.CardShape
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            notification.title,
                            fontSize = 18.sp,
                            fontWeight = if (notification.read) FontWeight.SemiBold else FontWeight.Bold
                        )
                        Text(notification.body)
                        if (notification.status.isNotBlank()) {
                            StatusBadge(notification.status)
                        }
                        if (!notification.read) {
                            TextButton(
                                onClick = {
                                    db.collection("notifications").document(notification.id)
                                        .update("read", true)
                                        .addOnFailureListener {
                                            onMessage("تعذر تحديث حالة الإشعار")
                                        }
                                }
                            ) {
                                Text("تحديد كمقروء")
                            }
                        }
                    }
                }
            }

            val unread = notifications.filterNot { it.read }
            if (unread.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val batch = db.batch()
                        unread.forEach { item ->
                            batch.update(
                                db.collection("notifications").document(item.id),
                                "read",
                                true
                            )
                        }
                        batch.commit().addOnFailureListener {
                            onMessage("تعذر تحديد الإشعارات كمقروءة")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ForsaUi.FieldShape,
                ) {
                    Text("تحديد الكل كمقروء")
                }
            }
        }
    }
}

@Composable
private fun MyApplicationsScreen(
    db: FirebaseFirestore,
    userUid: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    var applications by remember { mutableStateOf<List<ApplicationItem>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<ApplicationItem?>(null) }
    var statusFilter by remember { mutableStateOf("الكل") }

    val filteredApplications = applications.filter { app ->
        statusFilter == "الكل" || app.status == statusFilter
    }
    val pendingCount = applications.count { it.status == "pending" }
    val acceptedCount = applications.count { it.status == "accepted" }
    val rejectedCount = applications.count { it.status == "rejected" }

    DisposableEffect(userUid) {
        if (userUid.isBlank()) {
            onDispose { }
        } else {
            val registration = db.collection("applications")
                .whereEqualTo("applicantUid", userUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onMessage("تعذر تحميل طلباتك")
                        applications = emptyList()
                        return@addSnapshotListener
                    }

                    applications = snapshot?.documents
                        ?.mapNotNull { doc ->
                            ApplicationItem(
                                id = doc.id,
                                jobId = doc.getString("jobId").orEmpty(),
                                applicantUid = doc.getString("applicantUid").orEmpty(),
                                jobTitle = doc.getString("jobTitle").orEmpty(),
                                company = doc.getString("company").orEmpty(),
                                applicantName = doc.getString("applicantName").orEmpty(),
                                applicantEmail = doc.getString("applicantEmail").orEmpty(),
                                note = doc.getString("note").orEmpty(),
                                status = doc.getString("status") ?: "pending",
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                cvHeadline = doc.getString("cvHeadline").orEmpty(),
                                cvAbout = doc.getString("cvAbout").orEmpty(),
                                cvEducation = doc.getString("cvEducation").orEmpty(),
                                cvExperience = doc.getString("cvExperience").orEmpty(),
                                cvSkills = doc.getString("cvSkills").orEmpty(),
                                cvLanguages = doc.getString("cvLanguages").orEmpty(),
                                cvPhone = doc.getString("cvPhone").orEmpty(),
                                cvCity = doc.getString("cvCity").orEmpty()
                            )
                        }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                }

            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("طلباتي", "تابع كل طلبات التقديم وحالاتها", onBack)

        if (applications.isNotEmpty()) {
            Text(
                "الإجمالي ${applications.size}  •  قيد المراجعة ${pendingCount}  •  مقبول ${acceptedCount}  •  مرفوض ${rejectedCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "الكل" to "الكل",
                    "pending" to "قيد المراجعة",
                    "accepted" to "مقبول",
                    "rejected" to "مرفوض"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = statusFilter == value,
                        onClick = { statusFilter = value },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (applications.isEmpty()) {
            Text(
                "بعدك ما قدمت على وظائف.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (filteredApplications.isEmpty()) {
            Text(
                "ماكو طلبات ضمن هذا الفلتر.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredApplications.forEach { app ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.CardShape
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(app.jobTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(app.company, color = MaterialTheme.colorScheme.primary)
                        StatusBadge(app.status)

                        if (app.note.isNotBlank()) {
                            Text(
                                "رسالتك",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(app.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (app.status == "pending") {
                            OutlinedButton(
                                onClick = { deleteTarget = app },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ForsaUi.FieldShape
                            ) {
                                Text("سحب الطلب")
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { app ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("سحب الطلب؟") },
            text = { Text("راح ينحذف طلب التقديم وما راح يبقى ضمن طلباتك.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        db.collection("applications").document(app.id).delete()
                            .addOnSuccessListener {
                                onMessage("تم سحب الطلب")
                            }
                            .addOnFailureListener {
                                onMessage("تعذر سحب الطلب")
                            }
                        deleteTarget = null
                    }
                ) {
                    Text("سحب الطلب")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val normalized = status.lowercase()
    val label = when (normalized) {
        "accepted" -> "مقبول"
        "rejected" -> "مرفوض"
        "interview" -> "مقابلة"
        "shortlisted" -> "القائمة المختصرة"
        "withdrawn" -> "مسحوب"
        "verified" -> "موثق"
        else -> "قيد المراجعة"
    }
    val bg = when (normalized) {
        "accepted", "verified" -> ForsaUi.SuccessSoft
        "rejected" -> ForsaUi.DangerSoft
        "pending", "submitted", "viewed" -> ForsaUi.WarningSoft
        else -> ForsaUi.PrimarySoft
    }
    val fg = when (normalized) {
        "accepted", "verified" -> ForsaUi.Success
        "rejected" -> ForsaUi.Danger
        "pending", "submitted", "viewed" -> ForsaUi.Warning
        else -> ForsaUi.Primary
    }
    Surface(shape = ForsaUi.PillShape, color = bg, contentColor = fg) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (normalized == "accepted" || normalized == "verified") Icons.Default.CheckCircle else Icons.Default.Schedule, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CvProfileScreen(
    profile: CvProfile,
    userUid: String,
    db: FirebaseFirestore,
    onBack: () -> Unit,
    onSaved: (CvProfile) -> Unit,
    onMessage: (String) -> Unit
) {
    var headline by remember(profile) { mutableStateOf(profile.headline) }
    var about by remember(profile) { mutableStateOf(profile.about) }
    var education by remember(profile) { mutableStateOf(profile.education) }
    var experience by remember(profile) { mutableStateOf(profile.experience) }
    var skills by remember(profile) { mutableStateOf(profile.skills) }
    var languages by remember(profile) { mutableStateOf(profile.languages) }
    var saving by remember { mutableStateOf(false) }
    var fileBusy by remember { mutableStateOf(false) }
    var currentFileName by remember(profile) { mutableStateOf(profile.fileName) }
    var currentFileSize by remember(profile) { mutableStateOf(profile.fileSize) }
    var currentStoragePath by remember(profile) { mutableStateOf(profile.storagePath) }
    val context = LocalContext.current
    val storage = remember { FirebaseStorage.getInstance() }

    fun fileSizeText(bytes: Long): String {
        if (bytes <= 0L) return ""
        return if (bytes < 1024L * 1024L) {
            (bytes / 1024L).toString() + " KB"
        } else {
            String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }

    val pickCv = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (userUid.isBlank()) {
            onMessage("سجّل الدخول أولاً")
            return@rememberLauncherForActivityResult
        }

        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val allowedMime = mimeType in setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        if (!allowedMime && extension !in setOf("pdf", "doc", "docx")) {
            onMessage("المسموح فقط PDF أو DOC أو DOCX")
            return@rememberLauncherForActivityResult
        }

        val size = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
        if (size <= 0L) {
            onMessage("تعذر قراءة حجم الملف")
            return@rememberLauncherForActivityResult
        }
        if (size > 10L * 1024L * 1024L) {
            onMessage("حجم الملف يجب أن لا يتجاوز 10 ميغابايت")
            return@rememberLauncherForActivityResult
        }

        val safeName = (displayName.ifBlank {
            "cv_" + System.currentTimeMillis() + "." + if (extension.isBlank()) "pdf" else extension
        }).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val storagePath = "cvFiles/" + userUid + "/" + safeName
        val oldPath = currentStoragePath
        fileBusy = true

        storage.reference.child(storagePath)
            .putFile(uri)
            .addOnSuccessListener {
                val saveMetadata = {
                    db.collection("cvProfiles").document(userUid)
                        .set(
                            mapOf(
                                "fileName" to safeName,
                                "fileSize" to size,
                                "storagePath" to storagePath,
                                "updatedAt" to System.currentTimeMillis()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .addOnSuccessListener {
                            currentFileName = safeName
                            currentFileSize = size
                            currentStoragePath = storagePath
                            fileBusy = false
                            onSaved(
                                profile.copy(
                                    fileName = safeName,
                                    fileSize = size,
                                    storagePath = storagePath
                                )
                            )
                            onMessage("تم رفع ملف السيرة بنجاح")
                        }
                        .addOnFailureListener {
                            fileBusy = false
                            storage.reference.child(storagePath).delete()
                            onMessage("تم رفع الملف لكن تعذر حفظ بياناته")
                        }
                }
                if (oldPath.isNotBlank() && oldPath != storagePath) {
                    storage.reference.child(oldPath).delete().addOnCompleteListener { saveMetadata() }
                } else {
                    saveMetadata()
                }
            }
            .addOnFailureListener {
                fileBusy = false
                onMessage("تعذر رفع ملف السيرة")
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("السيرة الذاتية", "ملفك المهني جاهز للعرض عند التقديم", onBack)

        Text(
            "اكتب معلوماتك الأساسية حتى تكون جاهزة عند التقديم.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            Modifier.fillMaxWidth(),
            shape = ForsaUi.FieldShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (currentFileName.isBlank()) {
                        "ماكو ملف CV مرفوع"
                    } else {
                        "ملف CV: " + currentFileName +
                            if (currentFileSize > 0L) " — " + fileSizeText(currentFileSize) else ""
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "PDF أو DOC أو DOCX، وبحد أقصى 10 ميغابايت. الملف يبقى خاص بحسابك.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!fileBusy && !saving) {
                                pickCv.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                )
                            }
                        },
                        enabled = !fileBusy && !saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (currentFileName.isBlank()) "رفع ملف" else "استبدال الملف")
                    }
                    if (currentFileName.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                if (!fileBusy && !saving) {
                                    fileBusy = true
                                    storage.reference.child(currentStoragePath).downloadUrl
                                        .addOnSuccessListener { url ->
                                            fileBusy = false
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                            } catch (_: Exception) {
                                                onMessage("تعذر فتح الملف")
                                            }
                                        }
                                        .addOnFailureListener {
                                            fileBusy = false
                                            onMessage("تعذر فتح ملف السيرة")
                                        }
                                }
                            },
                            enabled = !fileBusy && !saving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("فتح")
                        }
                        OutlinedButton(
                            onClick = {
                                if (!fileBusy && !saving) {
                                    fileBusy = true
                                    storage.reference.child(currentStoragePath).delete()
                                        .addOnSuccessListener {
                                            db.collection("cvProfiles").document(userUid)
                                                .set(
                                                    mapOf(
                                                        "fileName" to "",
                                                        "fileSize" to 0L,
                                                        "storagePath" to "",
                                                        "updatedAt" to System.currentTimeMillis()
                                                    ),
                                                    com.google.firebase.firestore.SetOptions.merge()
                                                )
                                                .addOnSuccessListener {
                                                    fileBusy = false
                                                    currentFileName = ""
                                                    currentFileSize = 0L
                                                    currentStoragePath = ""
                                                    onSaved(profile.copy(fileName = "", fileSize = 0L, storagePath = ""))
                                                    onMessage("تم حذف ملف السيرة")
                                                }
                                                .addOnFailureListener {
                                                    fileBusy = false
                                                    onMessage("تم حذف الملف لكن تعذر تحديث البيانات")
                                                }
                                        }
                                        .addOnFailureListener {
                                            fileBusy = false
                                            onMessage("تعذر حذف ملف السيرة")
                                        }
                                }
                            },
                            enabled = !fileBusy && !saving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حذف")
                        }
                    }
                }
                if (fileBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ معالجة ملف السيرة…")
                    }
                }
            }
        }

        OutlinedTextField(
            value = headline,
            onValueChange = { headline = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المسمى المهني") },
            placeholder = { Text("مثال: مطور Android") },
            singleLine = true
        )

        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("نبذة عنك") },
            minLines = 4
        )

        OutlinedTextField(
            value = education,
            onValueChange = { education = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("التعليم") },
            minLines = 3
        )

        OutlinedTextField(
            value = experience,
            onValueChange = { experience = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("الخبرة") },
            minLines = 4
        )

        OutlinedTextField(
            value = skills,
            onValueChange = { skills = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المهارات") },
            placeholder = { Text("مثال: Kotlin، Excel، مبيعات") },
            minLines = 2
        )

        OutlinedTextField(
            value = languages,
            onValueChange = { languages = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اللغات") },
            minLines = 2
        )

        Button(
            onClick = {
                if (userUid.isBlank()) {
                    onMessage("سجّل الدخول أولاً")
                    return@Button
                }

                val finalProfile = CvProfile(
                    headline = headline.trim(),
                    about = about.trim(),
                    education = education.trim(),
                    experience = experience.trim(),
                    skills = skills.trim(),
                    languages = languages.trim()
                )

                if (
                    finalProfile.headline.isEmpty() &&
                    finalProfile.about.isEmpty() &&
                    finalProfile.education.isEmpty() &&
                    finalProfile.experience.isEmpty() &&
                    finalProfile.skills.isEmpty() &&
                    finalProfile.languages.isEmpty() &&
                    currentStoragePath.isEmpty()
                ) {
                    onMessage("أضف معلومة واحدة على الأقل أو ارفع ملف السيرة")
                    return@Button
                }

                saving = true
                db.collection("cvProfiles").document(userUid)
                    .set(
                        mapOf(
                            "headline" to finalProfile.headline,
                            "about" to finalProfile.about,
                            "education" to finalProfile.education,
                            "experience" to finalProfile.experience,
                            "skills" to finalProfile.skills,
                            "languages" to finalProfile.languages,
                            "fileName" to currentFileName,
                            "fileSize" to currentFileSize,
                            "storagePath" to currentStoragePath,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        saving = false
                        onSaved(finalProfile)
                        onMessage("تم حفظ السيرة الذاتية")
                    }
                    .addOnFailureListener {
                        saving = false
                        onMessage("تعذر حفظ السيرة الذاتية")
                    }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            if (saving) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("حفظ السيرة الذاتية", fontSize = 16.sp)
            }
        }

        Surface(
            Modifier.fillMaxWidth(),
            shape = ForsaUi.FieldShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            val currentProfile = CvProfile(
                headline = headline.trim(),
                about = about.trim(),
                education = education.trim(),
                experience = experience.trim(),
                skills = skills.trim(),
                languages = languages.trim()
            )
            Text(
                cvCompletionText(currentProfile),
                Modifier.padding(14.dp)
            )
        }
    }
}

private fun cvCompletionText(profile: CvProfile): String {
    val filled = listOf(
        profile.headline,
        profile.about,
        profile.education,
        profile.experience,
        profile.skills,
        profile.languages
    ).count { it.isNotBlank() }

    return when {
        filled == 0 -> "السيرة الذاتية غير مكتملة — أضف بياناتك"
        filled < 3 -> "السيرة الذاتية جزئية — أضف المزيد من المعلومات"
        filled < 6 -> "السيرة الذاتية جيدة — بقيت بعض المعلومات"
        else -> "السيرة الذاتية مكتملة"
    }
}

@Composable
private fun SavedJobsScreen(
    jobs: List<Job>,
    savedJobIds: Set<String>,
    onBack: () -> Unit,
    onToggleSaved: (Job) -> Unit,
    onOpen: (Job) -> Unit
) {
    val saved = jobs
        .filter { it.id in savedJobIds && it.isActive }
        .sortedByDescending { it.id }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ForsaPageHeader("المحفوظة", "الوظائف التي حفظتها للرجوع إليها", onBack)

        if (saved.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text("ما عندك وظائف محفوظة", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "اضغط القلب على أي وظيفة حتى تحفظها.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            saved.forEach { job ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = ForsaUi.CardShape
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(job.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(job.company, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onToggleSaved(job) }) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = "إزالة من المحفوظة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(job.city) })
                            AssistChip(onClick = {}, label = { Text(job.type) })
                        }

                        OutlinedButton(
                            onClick = { onOpen(job) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ForsaUi.FieldShape
                        ) {
                            Text("عرض الوظيفة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfo(label: String, value: String) {
    Card(Modifier.fillMaxWidth(), shape = ForsaUi.CardShape, colors = CardDefaults.cardColors(containerColor = ForsaUi.Surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = ForsaUi.Muted)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AuthHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                    Surface(Modifier.fillMaxSize(), shape = ForsaUi.SmallShape, color = ForsaUi.Surface, border = BorderStroke(1.dp, ForsaUi.Border)) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowForward, "رجوع", tint = ForsaUi.Ink)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = ForsaUi.Muted)
    }
}

@Composable
private fun LoginScreen(
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onSuccess: () -> Unit,
    onMessage: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader("تسجيل الدخول", "ادخل إلى حسابك في فرصة", onBack)
        Spacer(Modifier.height(26.dp))
        EmailField(email) { email = it }
        Spacer(Modifier.height(12.dp))
        PasswordField(password, visible, "كلمة المرور", { password = it }) { visible = !visible }

        TextButton(onClick = onForgot, modifier = Modifier.align(Alignment.Start)) {
            Text("نسيت كلمة المرور؟")
        }

        Button(
            onClick = {
                val cleanEmail = email.trim()
                when {
                    cleanEmail.isEmpty() -> onMessage("اكتب البريد الإلكتروني أولاً")
                    password.length < 6 -> onMessage("كلمة المرور يجب أن تكون 6 أحرف على الأقل")
                    else -> {
                        onLoading(true)
                        auth.signInWithEmailAndPassword(cleanEmail, password).addOnCompleteListener { task ->
                            if (task.isSuccessful) onSuccess()
                            else {
                                onLoading(false)
                                onMessage(firebaseError(task.exception))
                            }
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("دخول", fontSize = 16.sp)
        }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("ما عندك حساب؟ إنشاء حساب")
        }
    }
}

@Composable
private fun RegisterScreen(
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSuccess: () -> Unit,
    onMessage: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var role by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader("إنشاء حساب", "أنشئ حسابك باستخدام البريد الإلكتروني", onBack)
        Spacer(Modifier.height(20.dp))
        NameField(name) { name = it }
        Spacer(Modifier.height(12.dp))
        EmailField(email) { email = it }
        Spacer(Modifier.height(12.dp))
        PasswordField(password, visible, "كلمة المرور", { password = it }) { visible = !visible }
        Spacer(Modifier.height(12.dp))
        PasswordField(confirm, visible, "تأكيد كلمة المرور", { confirm = it }) { visible = !visible }
        Spacer(Modifier.height(16.dp))
        RoleChoiceSection(
            selectedRole = role,
            onSelect = { role = it }
        )
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                val cleanName = name.trim()
                val cleanEmail = email.trim()
                when {
                    cleanName.length < 2 -> onMessage("اكتب اسمك بشكل صحيح")
                    cleanEmail.isEmpty() -> onMessage("اكتب البريد الإلكتروني")
                    password.length < 6 -> onMessage("كلمة المرور يجب أن تكون 6 أحرف على الأقل")
                    password != confirm -> onMessage("كلمتا المرور غير متطابقتين")
                    role == null -> onMessage("اختار نوع الحساب أولاً")
                    else -> {
                        onLoading(true)
                        auth.createUserWithEmailAndPassword(cleanEmail, password).addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                onLoading(false)
                                onMessage(firebaseError(task.exception))
                            } else {
                                auth.currentUser?.let { user ->
                                    val profile = UserProfileChangeRequest.Builder()
                                        .setDisplayName(cleanName)
                                        .build()
                                    user.updateProfile(profile).addOnCompleteListener { profileTask ->
                                        if (!profileTask.isSuccessful) {
                                            onLoading(false)
                                            onMessage("تعذر حفظ اسم الحساب")
                                        } else {
                                            FirebaseFirestore.getInstance()
                                                .collection("users")
                                                .document(user.uid)
                                                .set(
                                                    mapOf(
                                                        "displayName" to cleanName,
                                                        "email" to user.email.orEmpty(),
                                                        "phone" to "",
                                                        "city" to "",
                                                        "role" to role.orEmpty(),
                                                        "roleConfirmed" to true,
                                                        "verificationStatus" to "unverified",
                                                        "companyName" to "",
                                                        "companyAbout" to "",
                                                        "companyCity" to ""
                                                    ),
                                                    com.google.firebase.firestore.SetOptions.merge()
                                                )
                                                .addOnCompleteListener { saveTask ->
                                                    if (saveTask.isSuccessful) {
                                                        onSuccess()
                                                    } else {
                                                        onLoading(false)
                                                        onMessage("تم إنشاء الحساب لكن تعذر حفظ الملف الشخصي")
                                                    }
                                                }
                                        }
                                    }
                                } ?: run {
                                    onLoading(false)
                                    onMessage("تعذر إنشاء جلسة المستخدم")
                                }
                            }
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("إنشاء الحساب", fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("عندك حساب؟ تسجيل الدخول")
        }
    }
}

@Composable
private fun PhoneAuthScreen(
    activity: Activity?,
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onMessage: (String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    fun finishWithCredential(credential: PhoneAuthCredential) {
        if (verifying) return
        verifying = true
        onLoading(true)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            verifying = false
            onLoading(false)
            if (task.isSuccessful) {
                onSuccess()
            } else {
                onMessage(firebaseError(task.exception))
            }
        }
    }

    fun sendCode() {
        val normalized = normalizeIraqiPhone(phone)
        when {
            activity == null -> onMessage("تعذر بدء التحقق على الهاتف")
            normalized == null -> onMessage("اكتب رقم هاتف عراقي صحيح، مثل 0770XXXXXXX")
            else -> {
                onLoading(true)
                auth.setLanguageCode("ar")
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        finishWithCredential(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        verifying = false
                        onLoading(false)
                        onMessage(phoneAuthError(e))
                    }

                    override fun onCodeSent(
                        newVerificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        verificationId = newVerificationId
                        onLoading(false)
                        onMessage("تم إرسال رمز التحقق إلى $normalized")
                    }
                }

                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(normalized)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()

                PhoneAuthProvider.verifyPhoneNumber(options)
            }
        }
    }

    AuthScaffold {
        AuthHeader(
            if (verificationId == null) "تسجيل الدخول بالهاتف" else "أدخل رمز التحقق",
            if (verificationId == null) {
                "راح نرسل رمز لمرة واحدة إلى رقمك"
            } else {
                "اكتب الرمز المكوّن من 6 أرقام حتى نكمل تسجيل الدخول"
            },
            onBack
        )
        Spacer(Modifier.height(24.dp))

        if (verificationId == null) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("رقم الهاتف العراقي") },
                placeholder = { Text("0770XXXXXXX أو +96477XXXXXXX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                shape = ForsaUi.FieldShape
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "قد تصلك رسالة SMS للتحقق، وقد تنطبق رسوم الرسائل حسب شركة الاتصالات.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = ::sendCode,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = ForsaUi.FieldShape
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("إرسال رمز التحقق", fontSize = 16.sp)
                }
            }
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.filter(Char::isDigit).take(6)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("رمز التحقق") },
                placeholder = { Text("000000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                shape = ForsaUi.FieldShape
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    val id = verificationId.orEmpty()
                    when {
                        id.isBlank() -> onMessage("أرسل رمز التحقق أولاً")
                        code.length != 6 -> onMessage("رمز التحقق يجب أن يكون 6 أرقام")
                        verifying || loading -> Unit
                        else -> finishWithCredential(
                            PhoneAuthProvider.getCredential(id, code)
                        )
                    }
                },
                enabled = !loading && !verifying,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = ForsaUi.FieldShape
            ) {
                if (loading || verifying) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("تحقق ودخول", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    verificationId = null
                    code = ""
                },
                enabled = !loading && !verifying,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = ForsaUi.FieldShape
            ) {
                Text("تغيير الرقم")
            }

            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = ::sendCode,
                enabled = !loading && !verifying,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("إعادة إرسال الرمز")
            }
        }
    }
}

private fun normalizeIraqiPhone(raw: String): String? {
    val value = raw.trim()
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")

    if (value.startsWith("+964")) {
        val local = value.removePrefix("+964")
        return if (local.matches(Regex("7[3-9]\\d{8}"))) "+964$local" else null
    }

    if (value.startsWith("00964")) {
        val local = value.removePrefix("00964")
        return if (local.matches(Regex("7[3-9]\\d{8}"))) "+964$local" else null
    }

    if (value.matches(Regex("07[3-9]\\d{8}"))) {
        return "+964" + value.drop(1)
    }

    if (value.matches(Regex("7[3-9]\\d{8}"))) {
        return "+964$value"
    }

    return null
}

private fun phoneAuthError(exception: FirebaseException): String {
    return when ((exception as? FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_PHONE_NUMBER" -> "رقم الهاتف غير صحيح"
        "ERROR_TOO_MANY_REQUESTS" -> "محاولات كثيرة. انتظر قليلاً ثم حاول مرة أخرى"
        "ERROR_QUOTA_EXCEEDED" -> "تم تجاوز حد الرسائل مؤقتاً. حاول لاحقاً"
        "ERROR_CAPTCHA_CHECK_FAILED" -> "تعذر التحقق من أن الطلب صادر من التطبيق"
        "ERROR_APP_NOT_AUTHORIZED" -> "التطبيق غير مفعّل لاستخدام تسجيل الدخول بالهاتف في Firebase"
        "ERROR_OPERATION_NOT_ALLOWED" -> "تسجيل الدخول برقم الهاتف غير مفعّل في Firebase"
        "ERROR_INVALID_VERIFICATION_CODE" -> "رمز التحقق غير صحيح"
        "ERROR_SESSION_EXPIRED" -> "انتهت صلاحية الرمز. أرسل رمزاً جديداً"
        "ERROR_INVALID_CREDENTIAL" -> "بيانات التحقق غير صالحة. أعد إرسال الرمز"
        "ERROR_WEB_CONTEXT_CANCELED" -> "تم إلغاء التحقق. حاول مرة أخرى"
        "ERROR_INTERNAL_ERROR" -> "حدث خطأ داخلي أثناء التحقق. حاول مرة أخرى"
        else -> "تعذر إرسال أو التحقق من رمز الهاتف. حاول مرة أخرى"
    }
}

@Composable
private fun ResetScreen(
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AuthScaffold {
        AuthHeader("إعادة كلمة المرور", "استرجع حسابك عن طريق البريد الإلكتروني", onBack)
        Spacer(Modifier.height(26.dp))
        EmailField(email) { email = it }
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                val cleanEmail = email.trim()
                if (cleanEmail.isEmpty()) {
                    onMessage("اكتب البريد الإلكتروني")
                } else {
                    onLoading(true)
                    auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener { task ->
                        onLoading(false)
                        if (task.isSuccessful) onMessage("تم إرسال رابط إعادة التعيين")
                        else onMessage(firebaseError(task.exception))
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = ForsaUi.FieldShape
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("إرسال الرابط", fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("البريد الإلكتروني") },
        leadingIcon = { Icon(Icons.Default.Email, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        shape = ForsaUi.FieldShape
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("الاسم") },
        leadingIcon = { Icon(Icons.Default.Person, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        shape = ForsaUi.FieldShape
    )
}

@Composable
private fun PasswordField(
    value: String,
    visible: Boolean,
    label: String,
    onValueChange: (String) -> Unit,
    onToggle: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        shape = ForsaUi.FieldShape
    )
}

@Composable
private fun AuthScaffold(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = ForsaUi.Background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            content = content
        )
    }
}

@Composable
private fun AuthDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("أو", Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}

private fun mainTitle(tab: MainTab): String = when (tab) {
    MainTab.Home -> "الرئيسية"
    MainTab.Jobs -> "الوظائف"
    MainTab.Publish -> "نشر وظيفة"
    MainTab.Profile -> "حسابي"
}

private fun tabLabel(tab: MainTab): String = when (tab) {
    MainTab.Home -> "الرئيسية"
    MainTab.Jobs -> "الوظائف"
    MainTab.Publish -> "نشر"
    MainTab.Profile -> "حسابي"
}

private fun tabIcon(tab: MainTab) = when (tab) {
    MainTab.Home -> Icons.Default.Home
    MainTab.Jobs -> Icons.Default.Search
    MainTab.Publish -> Icons.Default.AddCircle
    MainTab.Profile -> Icons.Default.Person
}

private fun firebaseError(exception: Exception?): String {
    return when ((exception as? com.google.firebase.auth.FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_EMAIL" -> "البريد الإلكتروني غير صحيح"
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_LOGIN_CREDENTIALS",
        "ERROR_INVALID_CREDENTIAL" -> "البريد الإلكتروني أو كلمة المرور غير صحيحة"
        "ERROR_USER_NOT_FOUND" -> "لا يوجد حساب بهذا البريد"
        "ERROR_USER_DISABLED" -> "هذا الحساب معطّل"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "هذا البريد مستخدم مسبقاً"
        "ERROR_WEAK_PASSWORD" -> "كلمة المرور ضعيفة — استخدم 6 أحرف أو أكثر"
        "ERROR_OPERATION_NOT_ALLOWED" -> "طريقة تسجيل الدخول هذه غير مفعّلة في Firebase حالياً"
        "ERROR_NETWORK_REQUEST_FAILED" -> "تأكد من اتصال الإنترنت وحاول مرة أخرى"
        "ERROR_TOO_MANY_REQUESTS" -> "محاولات كثيرة. انتظر قليلاً ثم حاول مرة أخرى"
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "هذا البريد مرتبط بطريقة تسجيل دخول أخرى"
        "ERROR_INVALID_VERIFICATION_CODE" -> "رمز التحقق غير صحيح"
        "ERROR_SESSION_EXPIRED" -> "انتهت صلاحية رمز التحقق. أرسل رمزاً جديداً"
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "بيانات تسجيل الدخول مستخدمة مع حساب آخر"
        "ERROR_PROVIDER_ALREADY_LINKED" -> "طريقة تسجيل الدخول هذه مرتبطة بالحساب مسبقاً"
        "ERROR_INVALID_PHONE_NUMBER" -> "رقم الهاتف غير صحيح"
        "ERROR_MISSING_PHONE_NUMBER" -> "رقم الهاتف غير متوفر"
        else -> "حدث خطأ في تسجيل الدخول، حاول مرة أخرى"
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
