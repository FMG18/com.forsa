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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.launch

private enum class AppScreen {
    Welcome,
    Login,
    Register,
    ResetPassword,
    Home
}

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
    val lightColors = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF5B3CC4),
        onPrimary = Color.White,
        secondary = Color(0xFF00A896),
        background = Color(0xFFF7F7FB),
        surface = Color.White,
        onBackground = Color(0xFF19191F),
        onSurface = Color(0xFF19191F)
    )

    MaterialTheme(
        colorScheme = lightColors,
        content = content
    )
}

@Composable
private fun ForsaApp() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val auth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    var screen by remember {
        mutableStateOf(
            if (auth.currentUser == null) AppScreen.Welcome else AppScreen.Home
        )
    }
    var loading by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun finishLogin() {
        loading = false
        userName = auth.currentUser?.displayName.orEmpty()
        screen = AppScreen.Home
    }

    suspend fun signInWithGoogle() {
        if (activity == null) {
            message("تعذر فتح تسجيل Google")
            return
        }

        loading = true
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.idToken, null)

                auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) finishLogin()
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

    fun startGoogleSignIn() {
        scope.launch { signInWithGoogle() }
    }

    fun signOut() {
        scope.launch {
            auth.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            userName = ""
            screen = AppScreen.Welcome
        }
    }

    when (screen) {
        AppScreen.Welcome -> ForsaWelcomeScreen(
            loading = loading,
            onGoogleSignIn = ::startGoogleSignIn,
            onEmailLogin = { screen = AppScreen.Login },
            onRegister = { screen = AppScreen.Register }
        )

        AppScreen.Login -> ForsaLoginScreen(
            auth = auth,
            loading = loading,
            onLoading = { loading = it },
            onBack = { screen = AppScreen.Welcome },
            onRegister = { screen = AppScreen.Register },
            onForgotPassword = { screen = AppScreen.ResetPassword },
            onLoggedIn = ::finishLogin,
            onMessage = ::message
        )

        AppScreen.Register -> ForsaRegisterScreen(
            auth = auth,
            loading = loading,
            onLoading = { loading = it },
            onBack = { screen = AppScreen.Welcome },
            onLogin = { screen = AppScreen.Login },
            onRegistered = ::finishLogin,
            onMessage = ::message
        )

        AppScreen.ResetPassword -> ForsaResetPasswordScreen(
            auth = auth,
            loading = loading,
            onLoading = { loading = it },
            onBack = { screen = AppScreen.Login },
            onSent = { message("تم إرسال رابط إعادة تعيين كلمة المرور") },
            onMessage = ::message
        )

        AppScreen.Home -> ForsaHomeScreen(
            userName = userName,
            onLogout = ::signOut
        )
    }
}

@Composable
private fun ForsaWelcomeScreen(
    loading: Boolean,
    onGoogleSignIn: () -> Unit,
    onEmailLogin: () -> Unit,
    onRegister: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark()

            Spacer(modifier = Modifier.height(20.dp))

            Text("فرصة", fontSize = 42.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "بوابتك للفرص المهنية في العراق",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onGoogleSignIn,
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("المتابعة باستخدام Google", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            AuthDivider()
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onEmailLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("تسجيل الدخول بالبريد الإلكتروني", fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onRegister,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("ليس لديك حساب؟ إنشاء حساب")
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Surface(
        modifier = Modifier.size(82.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Work,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun AuthHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ForsaLoginScreen(
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoggedIn: () -> Unit,
    onMessage: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader("تسجيل الدخول", "ادخل إلى حسابك في فرصة", onBack)

        Spacer(modifier = Modifier.height(26.dp))

        EmailField(email) { email = it }
        Spacer(modifier = Modifier.height(14.dp))
        PasswordField(password, visible, "كلمة المرور", { password = it }, { visible = !visible })

        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("نسيت كلمة المرور؟")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val cleanEmail = email.trim()
                when {
                    cleanEmail.isEmpty() -> onMessage("اكتب البريد الإلكتروني أولاً")
                    password.length < 6 -> onMessage("كلمة المرور يجب أن تكون 6 أحرف على الأقل")
                    else -> {
                        onLoading(true)
                        auth.signInWithEmailAndPassword(cleanEmail, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) onLoggedIn()
                                else {
                                    onLoading(false)
                                    onMessage(firebaseError(task.exception))
                                }
                            }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("دخول", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(18.dp))
        TextButton(
            onClick = onRegister,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("ما عندك حساب؟ إنشاء حساب")
        }
    }
}

@Composable
private fun ForsaRegisterScreen(
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRegistered: () -> Unit,
    onMessage: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader("إنشاء حساب", "أنشئ حسابك باستخدام البريد الإلكتروني", onBack)

        Spacer(modifier = Modifier.height(22.dp))

        NameField(name) { name = it }
        Spacer(modifier = Modifier.height(12.dp))
        EmailField(email) { email = it }
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(password, visible, "كلمة المرور", { password = it }, { visible = !visible })
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(confirm, visible, "تأكيد كلمة المرور", { confirm = it }, { visible = !visible })

        Spacer(modifier = Modifier.height(18.dp))

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
                        auth.createUserWithEmailAndPassword(cleanEmail, password)
                            .addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    onLoading(false)
                                    onMessage(firebaseError(task.exception))
                                } else {
                                    val user = auth.currentUser
                                    if (user == null) {
                                        onLoading(false)
                                        onMessage("تعذر إنشاء جلسة المستخدم")
                                    } else {
                                        val profile = UserProfileChangeRequest.Builder()
                                            .setDisplayName(cleanName)
                                            .build()
                                        user.updateProfile(profile)
                                            .addOnCompleteListener { onRegistered() }
                                    }
                                }
                            }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("إنشاء الحساب", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onLogin,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("عندك حساب؟ تسجيل الدخول")
        }
    }
}

@Composable
private fun ForsaResetPasswordScreen(
    auth: FirebaseAuth,
    loading: Boolean,
    onLoading: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSent: () -> Unit,
    onMessage: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AuthScaffold {
        AuthHeader("إعادة كلمة المرور", "استرجع حسابك عن طريق البريد الإلكتروني", onBack)

        Spacer(modifier = Modifier.height(26.dp))
        EmailField(email) { email = it }
        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val cleanEmail = email.trim()
                if (cleanEmail.isEmpty()) {
                    onMessage("اكتب البريد الإلكتروني")
                } else {
                    onLoading(true)
                    auth.sendPasswordResetEmail(cleanEmail)
                        .addOnCompleteListener { task ->
                            onLoading(false)
                            if (task.isSuccessful) onSent()
                            else onMessage(firebaseError(task.exception))
                        }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("إرسال رابط إعادة التعيين", fontSize = 15.sp)
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
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
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
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
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
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
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
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            "أو",
            modifier = Modifier.padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ForsaHomeScreen(
    userName: String,
    onLogout: () -> Unit
) {
    val name = userName.trim().ifEmpty { "مستخدم فرصة" }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("مرحباً، $name 👋", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "تم تسجيل دخولك بنجاح",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "اكتشف فرص العمل",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "الوظائف والبحث والملف الشخصي راح تكون ضمن الخطوة التالية.",
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 24.sp
                    )
                }
            }

            TextButton(onClick = onLogout) {
                Text("تسجيل الخروج")
            }
        }
    }
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
