package com.example.walactv.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.example.walactv.AppUpdateInfo
import com.example.walactv.ComposeMainFragment
import com.example.walactv.InstalledAppVersion
import com.example.walactv.evaluateAppUpdate
import com.example.walactv.AppUpdateAvailability
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.launch

// ── Login screen ───────────────────────────────────────────────────────────

@Composable
internal fun LoginScreen(fragment: ComposeMainFragment) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(520.dp).background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Iniciar sesion", color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Introduce tu usuario y contraseña para cargar los canales.", color = IptvTextMuted, fontSize = 16.sp)
            LoginField(value = fragment.loginUsername, label = "Usuario", hidden = false) { fragment.loginUsername = it }
            LoginField(value = fragment.loginPassword, label = "Contraseña", hidden = true) { fragment.loginPassword = it }
            fragment.loginError?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
            FocusButton(label = if (fragment.isSigningIn) "Entrando..." else "Entrar", icon = Icons.Outlined.PlayArrow) {
                if (!fragment.isSigningIn) fragment.performSignIn()
            }
        }
    }
}

@Composable
private fun LoginField(value: String, label: String, hidden: Boolean, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = IptvTextMuted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().background(IptvCard, RoundedCornerShape(8.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
            decorationBox = { innerTextField ->
                if (value.isBlank()) Text(if (hidden) "Escribe tu contraseña" else "Escribe tu usuario", color = IptvTextMuted, fontSize = 16.sp)
                innerTextField()
            },
        )
    }
}

// ── Sync screen ────────────────────────────────────────────────────────────

@Composable
internal fun SyncScreen(fragment: ComposeMainFragment) {
    Column(
        modifier = Modifier.fillMaxSize().background(IptvBackground).padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedSyncSpinner(fragment.overallSyncProgress)
        Spacer(modifier = Modifier.height(32.dp))
        if (fragment.contentSyncState == ComposeMainFragment.ContentSyncState.CHECKING) {
            Text("Comprobando actualizaciones...", color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(fragment.currentSyncLabel, color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (fragment.currentSyncCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("${fragment.currentSyncCount.toLocaleString()} elementos", color = IptvTextSecondary, fontSize = 14.sp)
            }
        }
        fragment.contentSyncError?.let {
            Spacer(modifier = Modifier.height(24.dp))
            Text(it, color = IptvLive, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AnimatedSyncSpinner(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "syncSpinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(300), label = "progress")

    Box(modifier = Modifier.size(64.dp).rotate(rotation), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            drawArc(color = IptvSurface, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeWidth))
            val sweep = (animatedProgress * 360f).coerceAtMost(360f)
            if (sweep > 1f) drawArc(color = IptvAccent, startAngle = -90f, sweepAngle = sweep, useCenter = false, style = Stroke(strokeWidth))
        }
    }
}

private fun Int.toLocaleString(): String = toString().reversed().chunked(3).joinToString(".").reversed()

// ── Mandatory update screen ────────────────────────────────────────────────

@Composable
internal fun MandatoryUpdateScreen(fragment: ComposeMainFragment, updateInfo: AppUpdateInfo) {
    val installed = fragment.installedAppVersion
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(700.dp).background(IptvSurface, RoundedCornerShape(12.dp))
                .border(1.dp, IptvFocusBorder, RoundedCornerShape(12.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Actualizacion obligatoria", color = IptvTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Debes instalar la nueva version para seguir usando WalacTV.", color = IptvTextMuted, fontSize = 18.sp)
            installed?.let { SettingsRow("Version instalada", "${it.versionName} (${it.versionCode})") }
            SettingsRow("Version requerida", "${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})")
            if (updateInfo.changelog.isNotBlank()) Text(updateInfo.changelog, color = IptvTextPrimary, fontSize = 16.sp)
            Text(fragment.updateStatusMessage, color = IptvAccent, fontSize = 15.sp)
            fragment.updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
            if (fragment.isUpdateDownloading) Text("La descarga esta en curso. Al terminar se abrira el instalador.", color = IptvTextMuted, fontSize = 14.sp)
            FocusButton(label = if (fragment.isUpdateDownloading) "Descargando..." else "Descargar actualizacion", icon = Icons.Outlined.PlayArrow) {
                if (!fragment.isUpdateDownloading) fragment.startUpdateFlow()
            }
            FocusButton(label = if (fragment.isCheckingUpdates) "Comprobando..." else "Reintentar", icon = Icons.Outlined.History) {
                if (!fragment.isCheckingUpdates) fragment.checkForAppUpdates(showToast = true)
            }
        }
    }
}
