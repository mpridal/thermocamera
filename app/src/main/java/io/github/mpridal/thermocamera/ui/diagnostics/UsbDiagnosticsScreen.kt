package io.github.mpridal.thermocamera.ui.diagnostics

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.diagnostics.UsbDiagnostics
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.GlassIconButton
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class UsbDiagUi(val text: String = "", val anyDevice: Boolean = false, val permissionDenied: Boolean = false)

@HiltViewModel
class UsbDiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val raw = mutableMapOf<String, ByteArray>()
    private val pending = ArrayDeque<UsbDevice>()
    private val _ui = MutableStateFlow(UsbDiagUi())
    val ui: StateFlow<UsbDiagUi> = _ui.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!granted) _ui.value = _ui.value.copy(permissionDenied = true)
                    readNext()
                }
                else -> refresh()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onCleared() {
        context.unregisterReceiver(receiver)
    }

    fun refresh() {
        _ui.value = _ui.value.copy(text = UsbDiagnostics.dump(usb, raw), anyDevice = usb.deviceList.isNotEmpty())
    }

    /** Asks for permission where needed, then reads the raw descriptors of every device. */
    fun readDescriptors() {
        _ui.value = _ui.value.copy(permissionDenied = false)
        asked.clear()
        pending.clear()
        pending.addAll(usb.deviceList.values)
        readNext()
    }

    private fun readNext() {
        while (pending.isNotEmpty()) {
            val d = pending.first()
            if (!usb.hasPermission(d)) {
                if (!asked.add(d.deviceName)) {
                    // Already asked in this run and refused – skip the device.
                    pending.removeFirst()
                    continue
                }
                // The permission broadcast calls readNext() again with this device still first.
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
                usb.requestPermission(d, pi)
                return
            }
            pending.removeFirst()
            readAny = true
            usb.openDevice(d)?.let { conn ->
                try {
                    conn.rawDescriptors?.let { raw[d.deviceName] = it }
                } finally {
                    conn.close()
                }
            }
        }
        refresh()
        if (readAny) {
            readAny = false
            io.github.mpridal.thermocamera.diagnostics.DiagnosticsLog.save(context, "usb", _ui.value.text)
        }
    }

    private val asked = mutableSetOf<String>()
    private var readAny = false

    fun exportFile(): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val name = "ht102-usb-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"
        io.github.mpridal.thermocamera.diagnostics.DiagnosticsLog.save(context, "usb", _ui.value.text)
        return File(dir, name).apply { writeText(_ui.value.text) }
    }

    companion object {
        private const val ACTION_PERMISSION = "io.github.mpridal.thermocamera.USB_DIAG_PERMISSION"
    }
}

@Composable
fun UsbDiagnosticsScreen(vm: UsbDiagnosticsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = ThermoTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val subject = stringResource(R.string.diag_share_subject)
    val chooser = stringResource(R.string.diag_share_chooser)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), onBack)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_usb_diagnostics), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(stringResource(R.string.diag_refresh), vm::refresh)
            GlassChip(stringResource(R.string.diag_read_descriptors), vm::readDescriptors)
            GlassChip(stringResource(R.string.diag_copy), {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(subject, ui.text))) }
            })
            GlassChip(stringResource(R.string.diag_export), {
                val file = vm.exportFile()
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, chooser))
            })
        }
        if (!ui.anyDevice) {
            Text(stringResource(R.string.diag_no_devices), color = c.textMuted, modifier = Modifier.padding(16.dp))
        }
        if (ui.permissionDenied) {
            Text(stringResource(R.string.diag_permission_denied), color = c.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Column(
            Modifier.padding(12.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp)
                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
        ) {
            Text(ui.text, color = c.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
