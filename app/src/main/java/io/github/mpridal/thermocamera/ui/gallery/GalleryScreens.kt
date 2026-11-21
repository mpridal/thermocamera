package io.github.mpridal.thermocamera.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.GlassIconButton
import io.github.mpridal.thermocamera.ui.components.GlassLevel
import io.github.mpridal.thermocamera.ui.components.SegmentedControl
import io.github.mpridal.thermocamera.ui.components.ValueTile
import io.github.mpridal.thermocamera.ui.components.celsius
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.components.paletteName
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.theme.NumberStyleSmall
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = ThermoTheme.colors
    val fmt = rememberNumberFormatter()
    val list by vm.snapshots.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var confirmDelete by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    val noProject = stringResource(R.string.project_none)
    fun name(id: Long?) = id?.let { pid -> projects.firstOrNull { it.id == pid }?.name } ?: noProject

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), { if (selected.isNotEmpty()) vm.clearSelection() else onBack() })
            Spacer(Modifier.width(8.dp))
            Text(
                if (selected.isEmpty()) stringResource(R.string.gallery_title) else stringResource(R.string.selected_count, selected.size),
                style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty()) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    GlassChip(stringResource(R.string.action_share), { open = true })
                    DropdownMenu(open, { open = false }, containerColor = c.menu) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_jpg)) }, onClick = { open = false; vm.shareSelectedJpg() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_pdf)) }, onClick = { open = false; vm.shareSelectedPdf() })
                    }
                }
                Spacer(Modifier.width(6.dp))
                GlassChip(stringResource(R.string.action_delete), { confirmDelete = true })
            }
        }
        // Project filter and the project for new snapshots.
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                var open by remember { mutableStateOf(false) }
                val label = when (val f = filter) {
                    ProjectFilter.All -> stringResource(R.string.project_all)
                    ProjectFilter.None -> noProject
                    is ProjectFilter.One -> name(f.id)
                }
                GlassChip("$label ▾", { open = true })
                DropdownMenu(open, { open = false }, containerColor = c.menu) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.project_all)) }, onClick = { open = false; vm.setFilter(ProjectFilter.All) })
                    DropdownMenuItem(text = { Text(noProject) }, onClick = { open = false; vm.setFilter(ProjectFilter.None) })
                    projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { open = false; vm.setFilter(ProjectFilter.One(p.id)) }) }
                }
            }
            Box {
                var open by remember { mutableStateOf(false) }
                GlassChip(stringResource(R.string.project_for_new, name(settings.activeProjectId.takeIf { it >= 0 })) + " ▾", { open = true })
                DropdownMenu(open, { open = false }, containerColor = c.menu) {
                    DropdownMenuItem(text = { Text(noProject) }, onClick = { open = false; vm.setActiveProject(-1) })
                    projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { open = false; vm.setActiveProject(p.id) }) }
                    DropdownMenuItem(text = { Text("+ " + stringResource(R.string.project_new)) }, onClick = { open = false; newProject = true })
                }
            }
        }
        if (list.isEmpty()) {
            Text(stringResource(R.string.gallery_empty), color = c.textMuted, modifier = Modifier.padding(24.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { s ->
                val isSel = s.id in selected
                val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, s.id, s.viewMode, s.palette, s.emissivity) { value = vm.thumbnail(s) }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (isSel) Modifier.border(3.dp, c.accent, RoundedCornerShape(16.dp)) else Modifier)
                        .glass(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { if (selected.isNotEmpty()) vm.toggle(s.id) else onOpen(s.id) },
                            onLongClick = { vm.toggle(s.id) },
                        )
                        .padding(6.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1080f / 1404f).clip(RoundedCornerShape(12.dp)).background(c.root)) {
                        thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Text(
                        s.title.ifBlank { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(s.createdAt)) },
                        color = c.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("▼ ${fmt.t1(s.minC)}  ▲ ${fmt.t1(s.maxC)}", color = c.textMuted, style = NumberStyleSmall)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text, selected.size)) },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteSelected() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (newProject) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newProject = false },
            title = { Text(stringResource(R.string.project_new)) },
            text = { InputField(text, { text = it.take(60) }, stringResource(R.string.project_new)) },
            confirmButton = { TextButton({ if (text.isNotBlank()) vm.createProject(text); newProject = false }) { Text(stringResource(R.string.project_create)) } },
            dismissButton = { TextButton({ newProject = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean = true, modifier: Modifier = Modifier) {
    val c = ThermoTheme.colors
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).glass(RoundedCornerShape(12.dp), GlassLevel.HI).padding(12.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, color = c.textMuted, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SnapshotDetailScreen(vm: SnapshotDetailViewModel, onBack: () -> Unit) {
    val c = ThermoTheme.colors
    val fmt = rememberNumberFormatter()
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val opt by vm.options.collectAsStateWithLifecycle()
    val rendered by vm.rendered.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var confirmDelete by remember { mutableStateOf(false) }
    val snap = s ?: return
    var title by remember(snap.id) { mutableStateOf(snap.title) }
    var note by remember(snap.id) { mutableStateOf(snap.note) }
    var projectId by remember(snap.id) { mutableStateOf(snap.projectId) }
    val noProject = stringResource(R.string.project_none)
    val leave = { vm.save(title, note, projectId); onBack() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), leave)
            Spacer(Modifier.width(8.dp))
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(snap.createdAt)),
                style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(if (opt?.viewMode == ViewMode.SIDE_BY_SIDE) 1456f / 936f else 720f / 936f).clip(RoundedCornerShape(18.dp)).background(c.root)) {
                rendered?.first?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            val r = rendered?.second
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueTile(stringResource(R.string.label_min), r?.let { fmt.t1(it.measurements.min.celsius) } ?: "–", "▼", c.coldText, Modifier.weight(1f))
                ValueTile(stringResource(R.string.label_max), r?.let { fmt.t1(it.measurements.max.celsius) } ?: "–", "▲", c.hotText, Modifier.weight(1f))
                ValueTile("fRsi", r?.fRsi?.let { fmt.two(it) } ?: "–", "◇", if ((r?.fRsi ?: 1f) < snap.fRsiLimit) c.warnTitle else c.text, Modifier.weight(1f))
                ValueTile("ε", fmt.two(opt?.emissivity ?: snap.emissivity), "", c.text, Modifier.weight(1f))
            }

            // Re-render (zadání F4): mode, palette, blend, emissivity, range.
            opt?.let { o ->
                Text(stringResource(R.string.detail_rerender).uppercase(locale), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                SegmentedControl(
                    options = listOf(
                        ViewMode.THERMAL to stringResource(R.string.mode_thermal),
                        ViewMode.OVERLAY to stringResource(R.string.mode_overlay),
                        ViewMode.SIDE_BY_SIDE to stringResource(R.string.mode_side_by_side),
                        ViewMode.PHOTO to stringResource(R.string.mode_photo),
                    ),
                    selected = o.viewMode,
                    onSelect = { vm.setOptions(o.copy(viewMode = it)) },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Palette.entries.forEach { p ->
                        val sel = p == o.palette
                        Box(
                            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
                                .then(if (sel) Modifier.background(c.accent) else Modifier.glass(RoundedCornerShape(20.dp)))
                                .clickable(role = Role.RadioButton) { vm.setOptions(o.copy(palette = p)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(paletteName(p), color = if (sel) c.onAccent else c.text, fontSize = 13.sp) }
                    }
                    val auto = o.range == null
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
                            .then(if (auto) Modifier.background(c.accent) else Modifier.glass(RoundedCornerShape(20.dp)))
                            .clickable(role = Role.Switch) {
                                vm.setOptions(o.copy(range = if (auto) io.github.mpridal.thermocamera.core.TempRange(snap.rangeMin, maxOf(snap.rangeMax, snap.rangeMin + 0.5f)) else null))
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.range_auto), color = if (auto) c.onAccent else c.text, fontSize = 13.sp) }
                }
                if (o.viewMode == ViewMode.OVERLAY) {
                    LabeledSlider(stringResource(R.string.blend), o.blend, 0f..1f, fmt.zero(o.blend * 100) + " %") { vm.setOptions(o.copy(blend = it)) }
                }
                LabeledSlider("ε", o.emissivity, 0.1f..1f, fmt.two(o.emissivity)) { vm.setOptions(o.copy(emissivity = (it * 100).toInt() / 100f)) }
                GlassChip(stringResource(R.string.detail_keep_display), { vm.keepDisplay() }, leading = "✓")
            }

            InputField(title, { title = it.take(80) }, stringResource(R.string.detail_title_hint))
            InputField(note, { note = it.take(1000) }, stringResource(R.string.detail_note_hint), singleLine = false, modifier = Modifier.heightIn(min = 96.dp))
            Box {
                var open by remember { mutableStateOf(false) }
                GlassChip(stringResource(R.string.detail_project) + ": " + (projectId?.let { pid -> projects.firstOrNull { it.id == pid }?.name } ?: noProject) + " ▾", { open = true })
                DropdownMenu(open, { open = false }, containerColor = c.menu) {
                    DropdownMenuItem(text = { Text(noProject) }, onClick = { open = false; projectId = null })
                    projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { open = false; projectId = p.id }) }
                }
            }

            Text(stringResource(R.string.share_as).uppercase(locale), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            ShareButton(stringResource(R.string.share_jpg)) { vm.save(title, note, projectId); vm.share(ShareKind.JPG) }
            ShareButton(stringResource(R.string.share_side_by_side)) { vm.share(ShareKind.SIDE_BY_SIDE) }
            ShareButton(stringResource(R.string.share_pdf)) { vm.save(title, note, projectId); vm.share(ShareKind.PDF) }
            ShareButton(stringResource(R.string.share_csv)) { vm.share(ShareKind.CSV) }
            Spacer(Modifier.size(8.dp))
            GlassChip(stringResource(R.string.action_delete), { confirmDelete = true }, leading = "✕")
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text, 1)) },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    androidx.activity.compose.BackHandler { leave() }
}

@Composable
private fun ShareButton(text: String, onClick: () -> Unit) {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).glass(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("↗", color = c.accent, fontSize = 18.sp)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onDone: (Float) -> Unit) {
    val c = ThermoTheme.colors
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        var local by remember(value) { mutableStateOf(value) }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onDone(local) }, valueRange = range,
            modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(valueText, color = c.text, fontSize = 13.sp)
    }
}
