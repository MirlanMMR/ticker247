package com.mirlanmamytov.ticker247.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@Composable
fun SignInScreen(
    isFirstLaunch: Boolean = true,
    onSignedIn: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    // Уже вошёл — просто показываем сплэш и идём дальше
    val alreadySignedIn = remember { auth.currentUser != null }

    val alpha       = remember { Animatable(0f) }
    val sloganAlpha = remember { Animatable(0f) }
    val btnAlpha    = remember { Animatable(0f) }

    var signingIn  by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )

    // Google Sign-In launcher
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.mirlanmamytov.ticker247.R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                signingIn = true
                errorMsg = null
            } catch (e: ApiException) {
                signingIn = false
                errorMsg = "Не удалось войти. Попробуй ещё раз."
            }
        } else {
            signingIn = false
        }
    }

    // Вход через Firebase после получения Google аккаунта
    LaunchedEffect(signingIn) {
        if (!signingIn) return@LaunchedEffect
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@LaunchedEffect
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).await()
            onSignedIn()
        } catch (e: Exception) {
            signingIn = false
            errorMsg = "Ошибка входа. Проверь соединение."
        }
    }

    // Анимация появления + логика перехода
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(600))
        delay(400)
        sloganAlpha.animateTo(1f, animationSpec = tween(700))

        when {
            alreadySignedIn -> {
                // Возвращающийся пользователь — сплэш 1.5с и вперёд
                delay(1500)
                onSignedIn()
            }
            !isFirstLaunch -> {
                // Не первый запуск, но вышел из аккаунта — показываем кнопку
                delay(300)
                btnAlpha.animateTo(1f, animationSpec = tween(500))
            }
            else -> {
                // Первый запуск — показываем кнопку входа
                delay(600)
                btnAlpha.animateTo(1f, animationSpec = tween(500))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080810), Color(0xFF0A0A1A), Color(0xFF0D1020))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha.value)
        ) {
            Text("⚡", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Ticker 24/7",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Тихо о важном",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF00D4FF),
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(sloganAlpha.value)
            )

            // Текст концепции — только первый запуск
            if (isFirstLaunch) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Первый запуск на этом устройстве!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00D4FF),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .alpha(sloganAlpha.value)
                        .padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Загружаем самое важное со всего мира...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(sloganAlpha.value)
                        .padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Можете закрыть приложение —\nвы ничего не пропустите.\nМы вас оповестим.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .alpha(sloganAlpha.value)
                        .padding(horizontal = 32.dp)
                )
            }

            // Кнопка Google Sign-In — появляется только если нужен вход
            if (!alreadySignedIn) {
                Spacer(Modifier.height(52.dp))
                Box(modifier = Modifier.alpha(btnAlpha.value)) {
                    if (signingIn) {
                        CircularProgressIndicator(
                            color = Color(0xFF00D4FF),
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(28.dp))
                                .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(28.dp))
                                .background(Color.White.copy(0.08f))
                                .clickable { signInLauncher.launch(googleClient.signInIntent) }
                                .padding(horizontal = 28.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Google "G" лого
                            Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF4285F4))
                            Text(
                                "Войти через Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    if (errorMsg != null) {
                        Text(
                            errorMsg!!,
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(top = 56.dp)
                        )
                    }
                }
            }
        }

        // MMR Lab® + три точки внизу
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(alpha.value)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (alreadySignedIn) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { i ->
                            val dotA = if (i == 1) pulseAlpha else pulseAlpha * 0.6f
                            Box(
                                Modifier.size(5.dp).alpha(dotA)
                                    .background(Color(0xFF00D4FF),
                                        androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    "Created by MMR Lab®",
                    fontSize = 13.sp,
                    color = Color(0xFF00E676),
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
