package com.mirlanmamytov.ticker247.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.mirlanmamytov.ticker247.util.UserLocale

/**
 * Экран первого запуска:
 * - Google Sign-In для YouTube интеграции
 * - Показывает определённый регион/язык
 * - Кнопка "Пропустить" — войти без аккаунта
 */
@Composable
fun SignInScreen(
    onSignedIn: (GoogleSignInAccount?) -> Unit  // null = skip
) {
    val context    = LocalContext.current
    val userCtx    = remember { UserLocale.get() }
    var isLoading  by remember { mutableStateOf(false) }
    var errorText  by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                onSignedIn(account)
            } catch (e: ApiException) {
                errorText = "Не удалось войти (${e.statusCode})"
            }
        }
    }

    // Фон с градиентом
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0A0A1A), Color(0xFF0D1A2E)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(40.dp))

            // Логотип
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡", fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ticker 24/7",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Новости · Валюта · Крипта",
                    fontSize = 15.sp,
                    color = Color(0xFF00D4FF).copy(0.8f)
                )
            }

            // Регион определён
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.07f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📍 Определено автоматически", fontSize = 12.sp, color = Color.White.copy(0.5f))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            regionLabel(userCtx.region),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Язык: ${languageLabel(userCtx.language)}  ·  ${(userCtx.localNewsRatio() * 100).toInt()}% локальных новостей",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    "Войдите через Google чтобы включить\nYouTube каналы в вашу ленту",
                    fontSize = 14.sp,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // Кнопки
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorText?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFFFF6B4A))
                }

                // Google Sign-In
                Button(
                    onClick = {
                        isLoading = true
                        errorText = null
                        launcher.launch(googleClient.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF1A1A2E),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Войти через Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1A2E)
                        )
                    }
                }

                // Пропустить
                TextButton(
                    onClick = { onSignedIn(null) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "Пропустить →",
                        fontSize = 14.sp,
                        color = Color.White.copy(0.45f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Created by MMR® Lab",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.2f)
                )
            }
        }
    }
}

private fun regionLabel(region: UserLocale.Region): String = when (region) {
    UserLocale.Region.KYRGYZSTAN    -> "🇰🇬 Кыргызстан"
    UserLocale.Region.CIS           -> "🇷🇺 СНГ"
    UserLocale.Region.CENTRAL_ASIA  -> "🌏 Центральная Азия"
    UserLocale.Region.EUROPE        -> "🇪🇺 Европа"
    UserLocale.Region.MIDDLE_EAST   -> "🌍 Ближний Восток"
    UserLocale.Region.NORTH_AMERICA -> "🌎 Северная Америка"
    UserLocale.Region.EAST_ASIA     -> "🌏 Восточная Азия"
    UserLocale.Region.OTHER         -> "🌐 Мир"
}

private fun languageLabel(lang: String): String = when (lang) {
    "ru" -> "Русский"
    "ky" -> "Кыргызча"
    "en" -> "English"
    "de" -> "Deutsch"
    "ar" -> "العربية"
    "tr" -> "Türkçe"
    "zh" -> "中文"
    "fr" -> "Français"
    "es" -> "Español"
    else -> lang.uppercase()
}

private fun UserLocale.UserContext.localNewsRatio() =
    UserLocale.run { this@localNewsRatio.localNewsRatio() }
