package com.forsa.app

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

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
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
private fun ForsaApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var screen by remember {
        mutableStateOf(
            if (auth.currentUser == null) AppScreen.Welcome else AppScreen.Home
        )
    }
    var userName by remember { mutableStateOf(auth.currentUser?.displayName ?: "") }

    fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    when (screen) {
        AppScreen.Welcome -> ForsaWelcomeScreen(
            onLogin = { screen = AppScreen.Login },
            onRegister = { screen = AppScreen.Register }
        )

        AppScreen.Login -> ForsaLoginScreen(
            onBack = { screen = AppScreen.Welcome },
            onRegister = { screen = AppScreen.Register },
            onForgotPassword = { screen = AppScreen.ResetPassword },
            onLoggedIn = {
                userName = auth.currentUser?.displayName.orEmpty()
                screen = AppScreen.Home
            },
            onMessage = ::showMessage,
            auth = auth
        )

        AppScreen.Register -> ForsaRegisterScreen(
            onBack = { screen = AppScreen.Welcome },
            onLogin = { screen = AppScreen.Login },
            onRegistered = {
                userName = auth.currentUser?.displayName.orEmpty()
                screen = AppScreen.Home
            },
            onMessage = ::showMessage,
            auth = auth
        )

        AppScreen.ResetPassword -> ForsaResetPasswordScreen(
            onBack = { screen = AppScreen.Login },
            onSent = { showMessage("تم إرسال رابط إعادة تعيين كلمة المرور") },
            onMessage = ::showMessage,
            auth = auth
        )

        AppScreen.Home -> ForsaHomeScreen(
            userName = userName,
            onLogout = {
                auth.signOut()
                userName = ""
                screen = AppScreen.Welcome
            }
        )
    }
}

@Composable
private fun ForsaWelcomeScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark()

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "فرصة",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "بوابتك للفرص المهنية في العراق",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(34.dp))

            Button(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("تسجيل الدخول", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("إنشاء حساب جديد", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "ابحث عن وظيفة، أو انشر فرصتك بسهولة",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
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
        BoxCentered {
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
private fun BoxCentered(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع"
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ForsaLoginScreen(
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoggedIn: () -> Unit,
    onMessage: (String) -> Unit,
    auth: FirebaseAuth
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader(
            title = "تسجيل الدخول",
            subtitle = "ادخل إلى حسابك في فرصة",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البريد الإلكتروني") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("كلمة المرور") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(14.dp)
        )

        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("نسيت كلمة المرور؟")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                val cleanEmail = email.trim()
                when {
                    cleanEmail.isEmpty() -> onMessage("اكتب البريد الإلكتروني أولاً")
                    password.length < 6 -> onMessage("كلمة المرور يجب أن تكون 6 أحرف على الأقل")
                    else -> {
                        loading = true
                        auth.signInWithEmailAndPassword(cleanEmail, password)
                            .addOnCompleteListener { task ->
                                loading = false
                                if (task.isSuccessful) {
                                    onLoggedIn()
                                } else {
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
            } else {
                Text("دخول", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AuthDivider()

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
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRegistered: () -> Unit,
    onMessage: (String) -> Unit,
    auth: FirebaseAuth
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader(
            title = "إنشاء حساب",
            subtitle = "أنشئ حسابك وابدأ رحلتك مع فرصة",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("الاسم") },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البريد الإلكتروني") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("كلمة المرور") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("تأكيد كلمة المرور") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val cleanName = name.trim()
                val cleanEmail = email.trim()

                when {
                    cleanName.length < 2 -> onMessage("اكتب اسمك بشكل صحيح")
                    cleanEmail.isEmpty() -> onMessage("اكتب البريد الإلكتروني")
                    password.length < 6 -> onMessage("كلمة المرور يجب أن تكون 6 أحرف على الأقل")
                    password != confirmPassword -> onMessage("كلمتا المرور غير متطابقتين")
                    else -> {
                        loading = true
                        auth.createUserWithEmailAndPassword(cleanEmail, password)
                            .addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    loading = false
                                    onMessage(firebaseError(task.exception))
                                    return@addOnCompleteListener
                                }

                                val user = auth.currentUser
                                if (user == null) {
                                    loading = false
                                    onMessage("تعذر إنشاء جلسة المستخدم")
                                    return@addOnCompleteListener
                                }

                                val profile = UserProfileChangeRequest.Builder()
                                    .setDisplayName(cleanName)
                                    .build()

                                user.updateProfile(profile).addOnCompleteListener {
                                    loading = false
                                    onRegistered()
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
            } else {
                Text("إنشاء الحساب", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        AuthDivider()

        Spacer(modifier = Modifier.height(14.dp))

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
    onBack: () -> Unit,
    onSent: () -> Unit,
    onMessage: (String) -> Unit,
    auth: FirebaseAuth
) {
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AuthScaffold {
        AuthHeader(
            title = "إعادة كلمة المرور",
            subtitle = "أرسلنا لك رابطًا لإعادة تعيين كلمة المرور",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(26.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البريد الإلكتروني") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val cleanEmail = email.trim()
                if (cleanEmail.isEmpty()) {
                    onMessage("اكتب البريد الإلكتروني")
                    return@Button
                }

                loading = true
                auth.sendPasswordResetEmail(cleanEmail)
                    .addOnCompleteListener { task ->
                        loading = false
                        if (task.isSuccessful) {
                            onSent()
                        } else {
                            onMessage(firebaseError(task.exception))
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
            } else {
                Text("إرسال الرابط", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ForsaHomeScreen(
    userName: String,
    onLogout: () -> Unit
) {
    val displayName = userName.trim().ifEmpty { "مستخدم فرصة" }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "مرحباً، $displayName 👋",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "شنو تحب تسوي اليوم؟",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "اكتشف فرص العمل",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "الخطوة التالية: الوظائف، البحث، والملف الشخصي.",
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 24.sp
                    )
                }
            }

            OutlinedButton(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("الوظائف قريباً")
            }

            TextButton(
                onClick = onLogout,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("تسجيل الخروج")
            }
        }
    }
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
            text = "أو",
            modifier = Modifier.padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

private fun firebaseError(exception: Exception?): String {
    val code = (exception as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
    return when (code) {
        "ERROR_INVALID_EMAIL" -> "البريد الإلكتروني غير صحيح"
        "ERROR_WRONG_PASSWORD" -> "كلمة المرور غير صحيحة"
        "ERROR_USER_NOT_FOUND" -> "لا يوجد حساب بهذا البريد"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "هذا البريد مستخدم مسبقاً"
        "ERROR_WEAK_PASSWORD" -> "كلمة المرور ضعيفة"
        "ERROR_INVALID_CREDENTIAL" -> "بيانات الدخول غير صحيحة"
        "ERROR_OPERATION_NOT_ALLOWED" -> "تسجيل البريد وكلمة المرور غير مفعّل في Firebase"
        else -> exception?.localizedMessage ?: "حدث خطأ، حاول مرة أخرى"
    }
}
