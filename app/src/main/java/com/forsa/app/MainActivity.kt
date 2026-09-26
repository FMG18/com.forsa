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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
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
import kotlinx.coroutines.launch

private enum class AppScreen {
    Welcome,
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
    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }
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

    suspend fun signInWithGoogle() {
        if (activity == null) {
            message("تعذر فتح تسجيل Google")
            return
        }

        loading = true

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(com.forsa.app.R.string.default_web_client_id))
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
                        loading = false
                        if (task.isSuccessful) {
                            userName = auth.currentUser?.displayName.orEmpty()
                            screen = AppScreen.Home
                        } else {
                            message(firebaseError(task.exception))
                        }
                    }
            } else {
                loading = false
                message("تعذر قراءة حساب Google")
            }
        } catch (e: GetCredentialException) {
            loading = false
            message("تم إلغاء تسجيل الدخول أو تعذر اختيار حساب Google")
        } catch (e: Exception) {
            loading = false
            message("تعذر تسجيل الدخول بواسطة Google")
        }
    }

    fun startGoogleSignIn() {
        scope.launch {
            signInWithGoogle()
        }
    }

    fun signOut() {
        scope.launch {
            auth.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
                // Firebase sign-out is still completed even if the provider state cannot be cleared.
            }
            userName = ""
            screen = AppScreen.Welcome
        }
    }

    when (screen) {
        AppScreen.Welcome -> ForsaWelcomeScreen(
            loading = loading,
            onGoogleSignIn = ::startGoogleSignIn
        )

        AppScreen.Home -> ForsaHomeScreen(
            userName = userName,
            onGoogleSignOut = ::signOut
        )
    }
}

@Composable
private fun ForsaWelcomeScreen(
    loading: Boolean,
    onGoogleSignIn: () -> Unit
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
                onClick = onGoogleSignIn,
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
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

            Text(
                text = "تسجيل الدخول وإنشاء الحساب يتمان بحساب Google",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 21.sp
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
private fun ForsaHomeScreen(
    userName: String,
    onGoogleSignOut: () -> Unit
) {
    val displayName = userName.trim().ifEmpty { "مستخدم فرصة" }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مرحباً، $displayName 👋",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "أنت الآن مسجل دخولك بحساب Google",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }

                IconButton(onClick = onGoogleSignOut) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "تسجيل الخروج"
                    )
                }
            }

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
                        text = "هنا راح تكون الوظائف والبحث والتخصصات والفرص المنشورة.",
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
                Text("الوظائف — قريباً")
            }

            TextButton(
                onClick = onGoogleSignOut,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("تسجيل الخروج")
            }
        }
    }
}

private fun firebaseError(exception: Exception?): String {
    return when ((exception as? com.google.firebase.auth.FirebaseAuthException)?.errorCode) {
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "هذا البريد مرتبط بطريقة تسجيل دخول أخرى"
        "ERROR_INVALID_CREDENTIAL" ->
            "بيانات Google غير صالحة"
        "ERROR_NETWORK_REQUEST_FAILED" ->
            "تأكد من اتصال الإنترنت وحاول مرة أخرى"
        else ->
            exception?.localizedMessage ?: "تعذر تسجيل الدخول بواسطة Google"
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
