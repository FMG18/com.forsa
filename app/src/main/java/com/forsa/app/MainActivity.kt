package com.forsa.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material.icons.filled.Home
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
import java.util.UUID
import kotlinx.coroutines.launch

private enum class AuthScreen { Welcome, Login, Register, ResetPassword }
private enum class MainTab { Home, Jobs, Publish, Profile }

private data class Job(
    val id: String,
    val title: String,
    val company: String,
    val city: String,
    val type: String,
    val description: String,
    val ownerUid: String
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
    var profileRole by remember { mutableStateOf("باحث عن عمل") }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    val db = remember { FirebaseFirestore.getInstance() }

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val currentUid = auth.currentUser?.uid

    DisposableEffect(currentUid) {
        if (currentUid == null) {
            jobs = emptyList()
            onDispose { }
        } else {
            val jobsRegistration = db.collection("jobs")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message("تعذر تحميل الوظائف من قاعدة البيانات")
                        return@addSnapshotListener
                    }

                    jobs = snapshot?.documents
                        ?.mapNotNull { document ->
                            val id = document.id
                            val title = document.getString("title") ?: return@mapNotNull null
                            val company = document.getString("company") ?: return@mapNotNull null
                            val city = document.getString("city") ?: return@mapNotNull null
                            val type = document.getString("type") ?: "دوام كامل"
                            val description = document.getString("description") ?: ""
                            val ownerUid = document.getString("ownerUid") ?: ""
                            Job(id, title, company, city, type, description, ownerUid)
                        }
                        ?.sortedByDescending { it.id }
                        ?: emptyList()
                }

            db.collection("users").document(currentUid).get()
                .addOnSuccessListener { document ->
                    val storedRole = document.getString("role")
                    if (!storedRole.isNullOrBlank()) profileRole = storedRole
                }

            onDispose {
                jobsRegistration.remove()
            }
        }
    }

    fun signedIn() {
        loading = false
        userName = auth.currentUser?.displayName.orEmpty()
        authScreen = null
        tab = MainTab.Home
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
                    if (task.isSuccessful) signedIn()
                    else {
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
        role = profileRole,
        jobs = jobs,
        onTab = { tab = it },
        onLogout = ::signOut,
        onProfileNameChanged = { newName ->
            auth.currentUser?.let { user ->
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                loading = true
                user.updateProfile(profile).addOnCompleteListener { task ->
                    loading = false
                    if (task.isSuccessful) {
                        userName = newName
                        db.collection("users").document(user.uid)
                            .set(
                                mapOf(
                                    "displayName" to newName,
                                    "email" to user.email.orEmpty(),
                                    "role" to profileRole
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        message("تم تحديث الاسم")
                    } else {
                        message("تعذر تحديث الاسم")
                    }
                }
            }
        },
        onRoleChanged = { role ->
            profileRole = role
            auth.currentUser?.let { user ->
                db.collection("users").document(user.uid)
                    .set(
                        mapOf(
                            "displayName" to user.displayName.orEmpty(),
                            "email" to user.email.orEmpty(),
                            "role" to role
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
        },
        onPublish = { job ->
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
        },
        onDeleteJob = { job ->
            db.collection("jobs").document(job.id).delete()
                .addOnSuccessListener {
                    message("تم حذف الوظيفة")
                }
                .addOnFailureListener {
                    message("تعذر حذف الوظيفة")
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
    role: String,
    jobs: List<Job>,
    onTab: (MainTab) -> Unit,
    onLogout: () -> Unit,
    onProfileNameChanged: (String) -> Unit,
    onRoleChanged: (String) -> Unit,
    onPublish: (Job) -> Unit,
    onDeleteJob: (Job) -> Unit
) {
    Scaffold(
        topBar = {
            if (currentTab != MainTab.Publish) {
                TopAppBar(title = { Text(mainTitle(currentTab)) })
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.values().forEach { item ->
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
                    jobsCount = jobs.size,
                    onJobs = { onTab(MainTab.Jobs) },
                    onPublish = { onTab(MainTab.Publish) }
                )

                MainTab.Jobs -> JobsTab(
                    jobs = jobs,
                    currentUserJobUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                    onDelete = onDeleteJob
                )

                MainTab.Publish -> PublishTab(
                    userUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                    onPublish = onPublish,
                    onCancel = { onTab(MainTab.Home) }
                )

                MainTab.Profile -> ProfileTab(
                    userName = userName,
                    email = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                    role = role,
                    onNameChanged = onProfileNameChanged,
                    onRoleChanged = onRoleChanged,
                    onLogout = onLogout
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
                    "ابحث عن وظيفة مناسبة أو انشر فرصة عمل جديدة.",
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStat("الوظائف المنشورة", jobsCount.toString(), Modifier.weight(1f))
            SmallStat("المدن", "العراق", Modifier.weight(1f))
        }

        Text(
            "ابدأ من الأسفل",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "الوظائف للتصفح، + للنشر، والحساب لإدارة ملفك الشخصي.",
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
    onDelete: (Job) -> Unit
) {
    if (jobs.isEmpty()) {
        EmptyJobs()
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "الوظائف المتاحة",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "الوظائف محفوظة في Cloud Firestore وتظهر للمستخدمين بعد تحميلها.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        jobs.forEach { job ->
            JobCard(
                job = job,
                canDelete = job.ownerUid == currentUserJobUid,
                onDelete = { onDelete(job) }
            )
        }
    }
}

@Composable
private fun EmptyJobs() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
        Text("ماكو وظائف مضافة حالياً", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "بعد ما ننشر أول وظيفة راح تظهر هنا.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JobCard(
    job: Job,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(job.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(job.company, color = MaterialTheme.colorScheme.primary)
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
            }

            Text(job.description, color = MaterialTheme.colorScheme.onSurfaceVariant)

            TextButton(onClick = {}) {
                Icon(Icons.Default.ArrowForward, null)
                Spacer(Modifier.width(4.dp))
                Text("تفاصيل الوظيفة")
            }
        }
    }
}

@Composable
private fun PublishTab(
    userUid: String,
    onPublish: (Job) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
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
                if (title.trim().length < 2 ||
                    company.trim().length < 2 ||
                    city.trim().length < 2 ||
                    description.trim().length < 5
                ) return@Button

                onPublish(
                    Job(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        company = company.trim(),
                        city = city.trim(),
                        type = type,
                        description = description.trim(),
                        ownerUid = userUid
                    )
                )
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
private fun ProfileTab(
    userName: String,
    email: String,
    role: String,
    onNameChanged: (String) -> Unit,
    onRoleChanged: (String) -> Unit,
    onLogout: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draftName by remember(userName) { mutableStateOf(userName) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (draftName.trim().length >= 2) {
                            onNameChanged(draftName.trim())
                            editing = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("حفظ")
                }
                OutlinedButton(
                    onClick = {
                        draftName = userName
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
        }

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

        HorizontalDivider()

        TextButton(
            onClick = onLogout,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("تسجيل الخروج")
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
                                    user.updateProfile(profile).addOnCompleteListener {
                                        onSuccess()
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
        "ERROR_WRONG_PASSWORD" -> "كلمة المرور غير صحيحة"
        "ERROR_USER_NOT_FOUND" -> "لا يوجد حساب بهذا البريد"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "هذا البريد مستخدم مسبقاً"
        "ERROR_WEAK_PASSWORD" -> "كلمة المرور ضعيفة"
        "ERROR_INVALID_CREDENTIAL" -> "بيانات الدخول غير صحيحة"
        "ERROR_OPERATION_NOT_ALLOWED" -> "تسجيل البريد وكلمة المرور غير مفعّل في Firebase حالياً"
        "ERROR_NETWORK_REQUEST_FAILED" -> "تأكد من اتصال الإنترنت وحاول مرة أخرى"
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
