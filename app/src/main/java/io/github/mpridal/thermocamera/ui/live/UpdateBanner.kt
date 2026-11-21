package io.github.mpridal.thermocamera.ui.live

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import io.github.mpridal.thermocamera.update.UpdateState
import io.github.mpridal.thermocamera.update.Updater

/**
 * Update banner (zadání kap. 7.5, F8): glass card under the top bar – "Version X downloaded",
 * Install. The first install asks Android for "Install unknown apps" with a short explanation.
 */
@Composable
fun UpdateBanner(updater: Updater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val c = ThermoTheme.colors
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    val s = state
    if (s !is UpdateState.Downloaded && s !is UpdateState.Installing) return
    Box(Modifier.safeDrawingPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), elevation = 6.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⬇", color = c.accent, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                if (s is UpdateState.Downloaded) stringResource(R.string.update_downloaded, s.version) else stringResource(R.string.update_installing),
                color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            if (s is UpdateState.Downloaded) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(c.accent)
                        .clickable(role = Role.Button) { if (updater.canInstall()) updater.install() else explain = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.update_install), color = c.onAccent, fontSize = 14.sp) }
            }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            text = { Text(stringResource(R.string.update_unknown_sources)) },
            confirmButton = {
                TextButton({
                    explain = false
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton({ explain = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
