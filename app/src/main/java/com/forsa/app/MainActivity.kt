package com.forsa.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.InputStreamReader
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

private enum class AuthScreen { Welcome, Login, Register, ResetPassword }
private enum class MainTab { Home, Jobs, Publish, Profile }

private data class Job(
    val id: String,
    val title: String,
    val company: String,
    val city: String,
    val type: String,
    val description: String,
    val ownerUid: String,
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
    val cvCity: String = ""
)

private data class CvProfile(
    val headline: String = "",
    val about: String = "",
    val education: String = "",
    val experience: String = "",
    val skills: String = "",
    val languages: String = ""
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForsaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ForsaApp()
                }
            }
        }
    }
}

@Composable
private fun ForsaTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF5B3CC4),
        onPrimary = Color.White,
        secondary = Color(0xFF00A896),
        background = Color(0xFFF7F7FB),
        surface = Color.White,
        onBackground = Color(0xFF19191F),
        onSurface = Color(0xFF19191F)
    )

    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun ForsaApp() {
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
    var userName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    var profilePhone by remember { mutableStateOf("") }
    var profileCity by remember { mutableStateOf("") }
    var profileRole by remember { mutableStateOf("باحث عن عمل") }
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

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val currentUid = auth.currentUser?.uid

    DisposableEffect(currentUid) {
        if (currentUid == null) {
            jobs = emptyList()
            appliedJobIds = emptySet()
            savedJobIds = emptySet()
            cvProfile = CvProfile()
            unreadNotificationsCount = 0
            onDispose { }
        } else {
            profileRole = "باحث عن عمل"
            profilePhone = ""
            profileCity = ""
            companyName = ""
            companyAbout = ""
            companyCity = ""

            val jobsRegistration = db.collection("jobs")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message("تعذر تحميل الوظائف من قاعدة البيانات")
                        return@addSnapshotListener
                    }

                    jobs = snapshot?.documents
                        ?.sortedByDescending { it.getLong("createdAt") ?: 0L }
                        ?.mapNotNull { document ->
                            val id = document.id
                            val title = document.getString("title") ?: return@mapNotNull null
                            val company = document.getString("company") ?: return@mapNotNull null
                            val city = document.getString("city") ?: return@mapNotNull null
                            val type = document.getString("type") ?: "دوام كامل"
                            val description = document.getString("description") ?: ""
                            val ownerUid = document.getString("ownerUid") ?: ""
                            if (ownerUid.isBlank()) return@mapNotNull null
                            val isActive = document.getBoolean("isActive") ?: true
                            val isFeatured = document.getBoolean("isFeatured") ?: false
                            val promotionType = document.getString("promotionType").orEmpty()
                            val promotionStatus = document.getString("promotionStatus").orEmpty()
                            val promotionExpiresAt = document.getLong("promotionExpiresAt") ?: 0L
                            val featuredNow = isFeatured && (
                                promotionExpiresAt == 0L || promotionExpiresAt > System.currentTimeMillis()
                            )
                            Job(
                                id = id,
                                title = title,
                                company = company,
                                city = city,
                                type = type,
                                description = description,
                                ownerUid = ownerUid,
                                isActive = isActive,
                                isFeatured = featuredNow,
                                promotionType = promotionType,
                                promotionStatus = promotionStatus,
                                promotionExpiresAt = promotionExpiresAt
                            )
                        }
                        ?: emptyList()
                }

            val savedJobsRegistration = db.collection("savedJobs")
                .whereEqualTo("userUid", currentUid)
                .addSnapshotListener { snapshot, _ ->
                    savedJobIds = snapshot?.documents
                        ?.mapNotNull { it.getString("jobId") }
                        ?.toSet()
                        ?: emptySet()
                }

            val notificationsRegistration = db.collection("notifications")
                .whereEqualTo("targetUid", currentUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        unreadNotificationsCount = 0
                        return@addSnapshotListener
                    }

                    unreadNotificationsCount = snapshot?.documents
                        ?.count { !(it.getBoolean("read") ?: false) }
                        ?: 0
                }

            val applicationsRegistration = db.collection("applications")
                .whereEqualTo("applicantUid", currentUid)
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

            db.collection("cvProfiles").document(currentUid).get()
                .addOnSuccessListener { document ->
                    cvProfile = CvProfile(
                        headline = document.getString("headline").orEmpty(),
                        about = document.getString("about").orEmpty(),
                        education = document.getString("education").orEmpty(),
                        experience = document.getString("experience").orEmpty(),
                        skills = document.getString("skills").orEmpty(),
                        languages = document.getString("languages").orEmpty()
                    )
                }

            db.collection("users").document(currentUid).get()
                .addOnSuccessListener { document ->
                    val storedRole = document.getString("role")
                    val finalRole = if (
                        storedRole == "باحث عن عمل" || storedRole == "صاحب عمل"
                    ) {
                        storedRole
                    } else {
                        "باحث عن عمل"
                    }
                    profileRole = finalRole
                    profilePhone = document.getString("phone").orEmpty()
                    profileCity = document.getString("city").orEmpty()
                    companyName = document.getString("companyName").orEmpty()
                    companyAbout = document.getString("companyAbout").orEmpty()
                    companyCity = document.getString("companyCity").orEmpty()

                    val user = auth.currentUser
                    db.collection("users").document(currentUid).set(
                        mapOf(
                            "displayName" to (user?.displayName ?: document.getString("displayName").orEmpty()),
                            "email" to (user?.email ?: document.getString("email").orEmpty()),
                            "phone" to document.getString("phone").orEmpty(),
                            "city" to document.getString("city").orEmpty(),
                            "role" to finalRole
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }

            onDispose {
                jobsRegistration.remove()
                applicationsRegistration.remove()
                savedJobsRegistration.remove()
                notificationsRegistration.remove()
            }
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

    fun persistAuthenticatedUser(onDone: () -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            loading = false
            message("تعذر إنشاء جلسة المستخدم")
            return
        }

        val userRef = db.collection("users").document(user.uid)
        userRef.get()
            .addOnSuccessListener { document ->
                val storedRole = document.getString("role")
                val role = if (
                    storedRole == "باحث عن عمل" || storedRole == "صاحب عمل"
                ) {
                    storedRole
                } else {
                    "باحث عن عمل"
                }

                userRef.set(
                    mapOf(
                        "displayName" to user.displayName.orEmpty(),
                        "email" to user.email.orEmpty(),
                        "phone" to document.getString("phone").orEmpty(),
                        "city" to document.getString("city").orEmpty(),
                        "role" to role,
                        "companyName" to document.getString("companyName").orEmpty(),
                        "companyAbout" to document.getString("companyAbout").orEmpty(),
                        "companyCity" to document.getString("companyCity").orEmpty()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).addOnSuccessListener {
                    onDone()
                }.addOnFailureListener {
                    loading = false
                    message("تم تسجيل الدخول لكن تعذر حفظ ملف الحساب")
                }
            }
            .addOnFailureListener {
                loading = false
                message("تعذر قراءة ملف الحساب")
            }
    }

    suspend fun googleSignIn() {
        if (activity == null) {
            message("تعذر فتح تسجيل Google")
            return
        }

        loading = true
        try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .build()

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
                        persistAuthenticatedUser(::signedIn)
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
            message("تم إلغاء تسجيل Google أو تعذر اختيار الحساب")
        } catch (_: Exception) {
            loading = false
            message("تعذر تسجيل الدخول بواسطة Google")
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
            profileRole = "باحث عن عمل"
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
                onRegister = { authScreen = AuthScreen.Register }
            )

            AuthScreen.Login -> LoginScreen(
                auth = auth,
                loading = loading,
                onLoading = { loading = it },
                onBack = { authScreen = AuthScreen.Welcome },
                onRegister = { authScreen = AuthScreen.Register },
                onForgot = { authScreen = AuthScreen.ResetPassword },
                onSuccess = ::signedIn,
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

    MainScaffold(
        currentTab = tab,
        userName = userName,
        phone = profilePhone,
        city = profileCity,
        role = profileRole,
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
            } else if (job.ownerUid == user.uid) {
                message("ما تقدر تقدم على إعلانك")
            } else {
                val applicationId = job.id + "_" + user.uid
                val applicationRef = db.collection("applications").document(applicationId)
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
                    "cvCity" to profileCity
                )

                db.runTransaction { transaction ->
                    if (transaction.get(applicationRef).exists()) {
                        throw IllegalStateException("ALREADY_APPLIED")
                    }
                    transaction.set(applicationRef, applicationData)
                    null
                }.addOnSuccessListener {
                    appliedJobIds = appliedJobIds + job.id
                    createNotification(
                        targetUid = job.ownerUid,
                        type = "new_application",
                        title = "طلب تقديم جديد",
                        body = user.displayName.orEmpty().ifBlank { "باحث عن عمل" } + " قدّم على وظيفة " + job.title,
                        jobId = job.id,
                        applicationId = applicationId,
                        status = "pending"
                    )
                    message("تم إرسال طلب التقديم بنجاح")
                    selectedJob = null
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
        onRoleChanged = { role ->
            if (role == "باحث عن عمل" || role == "صاحب عمل") {
                auth.currentUser?.let { user ->
                    db.collection("users").document(user.uid)
                        .set(
                            mapOf(
                                "displayName" to user.displayName.orEmpty(),
                                "email" to user.email.orEmpty(),
                                "phone" to profilePhone,
                                "city" to profileCity,
                                "role" to role,
                                "companyName" to companyName,
                                "companyAbout" to companyAbout,
                                "companyCity" to companyCity
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .addOnSuccessListener {
                            profileRole = role
                            if (role != "صاحب عمل" && tab == MainTab.Publish) {
                                tab = MainTab.Home
                            }
                        }
                        .addOnFailureListener {
                            message("تعذر حفظ نوع الحساب")
                        }
                }
            }
        },
        onPublish = { job ->
            if (profileRole != "صاحب عمل") {
                message("نشر الوظائف متاح لحساب صاحب العمل")
            } else {
                db.collection("jobs").document(job.id).set(
                    mapOf(
                        "title" to job.title,
                        "company" to job.company,
                        "city" to job.city,
                        "type" to job.type,
                        "description" to job.description,
                        "ownerUid" to job.ownerUid,
                        "createdAt" to System.currentTimeMillis()
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
private fun WelcomeScreen(
    loading: Boolean,
    onGoogle: () -> Unit,
    onEmailLogin: () -> Unit,
    onRegister: () -> Unit
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark()
            Spacer(Modifier.height(20.dp))
            Text("فرصة", fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "منصة فرص العمل في العراق",
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            Button(
                onClick = onGoogle,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else Text("المتابعة باستخدام Google", fontSize = 16.sp)
            }

            Spacer(Modifier.height(14.dp))
            AuthDivider()
            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = onEmailLogin,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Email, null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الدخول بالبريد الإلكتروني")
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRegister) {
                Text("ليس لديك حساب؟ إنشاء حساب")
            }
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
    onPasswordReset: () -> Unit,
    onRoleChanged: (String) -> Unit,
    onPublish: (Job) -> Unit,
    onDeleteJob: (Job) -> Unit,
    onEditJob: (Job) -> Unit,
    onToggleJobActive: (Job) -> Unit,
    onPromoteJob: (Job) -> Unit
) {
    val userUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val canPublish = role == "صاحب عمل"
    val visibleTabs = MainTab.values().filter { it != MainTab.Publish || canPublish }

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
        topBar = {
            if (currentTab != MainTab.Publish) {
                TopAppBar(title = { Text(mainTitle(currentTab)) })
            }
        },
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { item ->
                    NavigationBarItem(
                        selected = currentTab == item,
                        onClick = { onTab(item) },
                        icon = {
                            Icon(tabIcon(item), contentDescription = null)
                        },
                        label = { Text(tabLabel(item)) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                MainTab.Home -> HomeTab(
                    userName = userName,
                    role = role,
                    jobsCount = jobs.count { it.isActive },
                    canPublish = canPublish,
                    onJobs = { onTab(MainTab.Jobs) },
                    onPublish = { onTab(MainTab.Publish) }
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
                    } else {
                        RoleRequiredScreen(
                            title = "النشر متاح لصاحب العمل",
                            description = "بدّل نوع الحساب من الملف الشخصي حتى تقدر تنشر وظائف.",
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
                    jobs = jobs,
                    savedJobIds = savedJobIds,
                    cvProfile = cvProfile,
                    unreadNotificationsCount = unreadNotificationsCount,
                    userUid = userUid,
                    db = db,
                    onProfileSaved = onProfileSaved,
                    onCompanyProfileSaved = onCompanyProfileSaved,
                    onPasswordReset = onPasswordReset,
                    onRoleChanged = onRoleChanged,
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
private fun HomeTab(
    userName: String,
    role: String,
    jobsCount: Int,
    canPublish: Boolean,
    onJobs: () -> Unit,
    onPublish: () -> Unit
) {
    val name = userName.trim().ifEmpty { "مستخدم فرصة" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("مرحباً، $name 👋", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "حسابك: $role",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    "خلك قريب من فرصتك الجاية",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (canPublish) {
                        "ابحث عن وظيفة مناسبة أو انشر فرصة عمل جديدة."
                    } else {
                        "ابحث عن الوظيفة المناسبة وتابع طلباتك من حسابك."
                    },
                    color = Color.White.copy(alpha = .9f),
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onJobs,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("الوظائف")
                    }

                    if (canPublish) {
                        OutlinedButton(
                            onClick = onPublish,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("نشر وظيفة")
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStat("إجمالي الوظائف", jobsCount.toString(), Modifier.weight(1f))
            SmallStat("المدن", "العراق", Modifier.weight(1f))
        }

        Text(
            if (canPublish) "إدارة حسابك من الأسفل" else "تابع فرصك من الحساب",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (canPublish) {
                "من الحساب تقدر تدير إعلاناتك وتشوف طلبات المتقدمين."
            } else {
                "من الحساب تقدر تشوف طلباتك وحالتها وتحدث بياناتك."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmallStat(title: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
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
    if (selectedJob != null) {
        JobDetailsScreen(
            job = selectedJob,
            isOwner = selectedJob.ownerUid == currentUserJobUid,
            alreadyApplied = selectedJob.id in appliedJobIds,
            canApply = role == "باحث عن عمل",
            isSaved = selectedJob.id in savedJobIds,
            onToggleSaved = { onToggleSaved(selectedJob) },
            onBack = onClearSelectedJob,
            onApply = onApply
        )
        return
    }

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("الكل") }

    val filteredJobs = jobs.filter { job ->
        val visibleToUser = job.isActive || (role == "صاحب عمل" && job.ownerUid == currentUserJobUid)
        if (!visibleToUser) return@filter false

        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            job.title.contains(q, ignoreCase = true) ||
            job.company.contains(q, ignoreCase = true) ||
            job.city.contains(q, ignoreCase = true) ||
            job.description.contains(q, ignoreCase = true)

        val matchesType = typeFilter == "الكل" || job.type == typeFilter
        matchesQuery && matchesType
    }.sortedWith(
        compareByDescending<Job> { it.isFeatured }
            .thenByDescending { it.promotionExpiresAt }
            .thenByDescending { it.id }
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ابحث عن فرصة", fontSize = 22.sp, fontWeight = FontWeight.Bold)

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
            shape = RoundedCornerShape(14.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("الكل", "دوام كامل", "دوام جزئي", "عن بُعد").forEach { option ->
                FilterChip(
                    selected = typeFilter == option,
                    onClick = { typeFilter = option },
                    label = { Text(option) }
                )
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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(job.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(job.company, color = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onToggleSaved) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "إزالة من المحفوظة" else "حفظ الوظيفة"
                    )
                }

                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(job.city) })
                AssistChip(onClick = {}, label = { Text(job.type) })
                if (job.isFeatured) {
                    AssistChip(onClick = {}, label = { Text("مميز") })
                }
                if (!job.isActive) {
                    AssistChip(onClick = {}, label = { Text("موقوف") })
                }
            }

            Text(
                job.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )

            if (alreadyApplied) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("تم التقديم")
                    }
                }
            }

            TextButton(onClick = onOpen) {
                Text("عرض تفاصيل الوظيفة")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }

            Text(
                "تفاصيل الوظيفة",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleSaved) {
                Icon(
                    if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isSaved) "إزالة من المحفوظة" else "حفظ الوظيفة"
                )
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(job.title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(job.company, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(job.city) })
                    AssistChip(onClick = {}, label = { Text(job.type) })
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text("هذا الإعلان منشور من حسابك.", Modifier.padding(16.dp))
                }
            }

            !canApply -> {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "التقديم متاح من حساب الباحث عن عمل. تقدر تبدّل نوع الحساب من الملف الشخصي.",
                        Modifier.padding(16.dp)
                    )
                }
            }

            alreadyApplied -> {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(16.dp)
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
    var description by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowForward, "رجوع")
            }
            Text("نشر فرصة عمل", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

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
                            isActive = true
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            Modifier.size(86.dp),
            CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
            Text("رجوع")
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
    val plans = listOf(
        Triple("boost_7", "مميز 7 أيام", 5000L),
        Triple("top_7", "تثبيت 7 أيام", 8000L),
        Triple("urgent_48", "عاجل 48 ساعة", 3000L)
    )
    var selectedPlan by remember { mutableStateOf(plans.first()) }
    var submitting by remember { mutableStateOf(false) }
    var activeOrderId by remember { mutableStateOf("") }
    var paymentStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val paymentApiBaseUrl = BuildConfig.FORSA_PAYMENT_API_BASE_URL

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }
            Text("ترويج الإعلان", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(job.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(job.company, color = MaterialTheme.colorScheme.primary)
                Text(
                    "اختَر خدمة الترويج المناسبة لإعلانك.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        plans.forEach { plan ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.second, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "السعر: " + plan.third + " د.ع",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FilterChip(
                        selected = selectedPlan.first == plan.first,
                        onClick = { selectedPlan = plan },
                        label = { Text("اختيار") }
                    )
                }
            }
        }

        DisposableEffect(activeOrderId) {
            if (activeOrderId.isBlank()) {
                onDispose { }
            } else {
                val registration = db.collection("promotionOrders").document(activeOrderId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                        paymentStatus = snapshot.getString("status").orEmpty()
                        if (paymentStatus == "paid") {
                            submitting = false
                            onMessage("تم الدفع وتفعيل ترقية الإعلان")
                            onDone()
                        }
                    }

                onDispose { registration.remove() }
            }
        }

        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                if (paymentStatus.isNotBlank() && paymentStatus != "paid") {
                    "حالة الدفع: " + paymentStatus
                } else if (paymentApiBaseUrl.isBlank()) {
                    "الدفع مهيأ داخل التطبيق، وباقي فقط ربط عنوان خادم الدفع وإضافة مفاتيح ZainCash بعد الموافقة."
                } else {
                    "سيتم تحويلك إلى بوابة ZainCash لإكمال الدفع بشكل آمن."
                },
                Modifier.padding(14.dp)
            )
        }

        Button(
            onClick = {
                if (submitting) return@Button
                if (userUid.isBlank()) {
                    onMessage("سجّل الدخول أولاً")
                    return@Button
                }
                if (paymentApiBaseUrl.isBlank()) {
                    onMessage("خادم الدفع غير مربوط بعد")
                    return@Button
                }

                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    onMessage("سجّل الدخول أولاً")
                    return@Button
                }

                submitting = true
                paymentStatus = "payment_initializing"
                user.getIdToken(false)
                    .addOnSuccessListener { tokenResult ->
                        val idToken = tokenResult.token
                        if (idToken.isNullOrBlank()) {
                            submitting = false
                            paymentStatus = "payment_init_failed"
                            onMessage("تعذر التحقق من جلسة الحساب")
                            return@addOnSuccessListener
                        }

                        scope.launch {
                            try {
                                val result = startForsaPayment(
                                    baseUrl = paymentApiBaseUrl,
                                    idToken = idToken,
                                    jobId = job.id,
                                    planId = selectedPlan.first
                                )
                                activeOrderId = result.orderId
                                paymentStatus = "pending_payment"
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(result.redirectUrl))
                                )
                            } catch (exception: Exception) {
                                submitting = false
                                paymentStatus = "payment_init_failed"
                                val message = exception.message.orEmpty()
                                onMessage(
                                    if (message == "PAYMENT_API_NOT_CONFIGURED") {
                                        "خادم الدفع غير مربوط بعد"
                                    } else {
                                        "تعذر بدء عملية الدفع"
                                    }
                                )
                            }
                        }
                    }
                    .addOnFailureListener {
                        submitting = false
                        paymentStatus = "payment_init_failed"
                        onMessage("تعذر التحقق من جلسة الحساب")
                    }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("الدفع عبر ZainCash", fontSize = 16.sp)
            }
        }
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
    jobs: List<Job>,
    savedJobIds: Set<String>,
    cvProfile: CvProfile,
    unreadNotificationsCount: Int,
    userUid: String,
    db: FirebaseFirestore,
    onProfileSaved: (String, String, String) -> Unit,
    onCompanyProfileSaved: (String, String, String) -> Unit,
    onPasswordReset: () -> Unit,
    onRoleChanged: (String) -> Unit,
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
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Person, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تعديل الملف الشخصي")
                    }
                    OutlinedButton(
                        onClick = onPasswordReset,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
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

                Text("نوع الحساب", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("باحث عن عمل", "صاحب عمل").forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { onRoleChanged(option) },
                            label = { Text(option) }
                        )
                    }
                }

                if (role == "صاحب عمل") {
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(actionLabel)
            }
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }
            Text(
                "ملف الشركة",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

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
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("حفظ ملف الشركة", fontSize = 16.sp)
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }
            Text(
                "لوحة صاحب العمل",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

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
            shape = RoundedCornerShape(20.dp)
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("إعلاناتي")
            }
            OutlinedButton(
                onClick = onApplications,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("الطلبات")
            }
        }

        Text("آخر الطلبات", fontSize = 19.sp, fontWeight = FontWeight.Bold)

        if (applications.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(16.dp)
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, "رجوع")
            }
            Text("إعلاناتي", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        if (myJobs.isEmpty()) {
            Text(
                "ما عندك إعلانات منشورة حالياً.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            myJobs.forEach { job ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(job.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(job.company, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (job.isActive) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                if (job.isActive) "الإعلان ظاهر للباحثين" else "الإعلان موقوف",
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
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(Modifier.width(6.dp))
                                Text("تعديل")
                            }
                            Button(
                                onClick = onApplications,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
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
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (job.isFeatured) "مميز" else "ترقية الإعلان")
                            }
                            OutlinedButton(
                                onClick = { onToggleJobActive(job) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (job.isActive) "إيقاف الإعلان" else "تفعيل الإعلان")
                            }
                            OutlinedButton(
                                onClick = { deleteTarget = job },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, "رجوع")
            }
            Text("تعديل الوظيفة", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

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
            shape = RoundedCornerShape(16.dp)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, "رجوع")
            }
            Text("طلبات التقديم", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

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
                    shape = RoundedCornerShape(18.dp)
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
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("عرض السيرة الذاتية")
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
                                    shape = RoundedCornerShape(14.dp)
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
                                    shape = RoundedCornerShape(14.dp)
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
                                shape = RoundedCornerShape(14.dp)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }
            Text("الإشعارات", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        if (notifications.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text("ماكو إشعارات حالياً.", Modifier.padding(16.dp))
            }
        } else {
            notifications.forEach { notification ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
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
                    shape = RoundedCornerShape(14.dp),
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, "رجوع")
            }
            Text("طلباتي", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

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
                    shape = RoundedCornerShape(18.dp)
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
                                shape = RoundedCornerShape(14.dp)
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
    val label = when (status) {
        "accepted" -> "مقبول"
        "rejected" -> "مرفوض"
        else -> "قيد المراجعة"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }
            Text("السيرة الذاتية", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            "اكتب معلوماتك الأساسية حتى تكون جاهزة عند التقديم.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                    finalProfile.languages.isEmpty()
                ) {
                    onMessage("أضف معلومة واحدة على الأقل إلى السيرة")
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
            shape = RoundedCornerShape(16.dp)
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
            shape = RoundedCornerShape(16.dp),
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
            }
            Text("المحفوظة", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

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
                Text("ما عندك وظائف محفوظة", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                    shape = RoundedCornerShape(18.dp)
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
                            shape = RoundedCornerShape(14.dp)
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AuthHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowForward, "رجوع")
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
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
            shape = RoundedCornerShape(16.dp)
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
                                                        "role" to "باحث عن عمل"
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
            shape = RoundedCornerShape(16.dp)
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
            shape = RoundedCornerShape(16.dp)
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
        shape = RoundedCornerShape(14.dp)
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
        shape = RoundedCornerShape(14.dp)
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
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun AuthScaffold(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize()) {
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
        else -> exception?.localizedMessage ?: "حدث خطأ، حاول مرة أخرى"
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
