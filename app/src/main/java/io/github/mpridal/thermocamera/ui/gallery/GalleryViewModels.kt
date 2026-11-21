package io.github.mpridal.thermocamera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.data.Settings
import io.github.mpridal.thermocamera.data.SettingsRepository
import io.github.mpridal.thermocamera.data.db.ProjectDao
import io.github.mpridal.thermocamera.data.db.ProjectEntity
import io.github.mpridal.thermocamera.data.db.SnapshotDao
import io.github.mpridal.thermocamera.data.db.SnapshotEntity
import io.github.mpridal.thermocamera.snapshot.RenderOptions
import io.github.mpridal.thermocamera.snapshot.RenderResult
import io.github.mpridal.thermocamera.snapshot.SnapshotExporter
import io.github.mpridal.thermocamera.snapshot.SnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Gallery filter: all, without project, or one project. */
sealed interface ProjectFilter {
    data object All : ProjectFilter
    data object None : ProjectFilter
    data class One(val id: Long) : ProjectFilter
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SnapshotDao,
    private val projectsDao: ProjectDao,
    private val store: SnapshotStore,
    private val exporter: SnapshotExporter,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val projects: StateFlow<List<ProjectEntity>> = projectsDao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filter = MutableStateFlow<ProjectFilter>(ProjectFilter.All)
    val filter: StateFlow<ProjectFilter> = _filter.asStateFlow()

    val snapshots: StateFlow<List<SnapshotEntity>> = combine(dao.all(), _filter) { list, f ->
        when (f) {
            ProjectFilter.All -> list
            ProjectFilter.None -> list.filter { it.projectId == null }
            is ProjectFilter.One -> list.filter { it.projectId == f.id }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val thumbs = LruCache<Long, ImageBitmap>(60)

    fun setFilter(f: ProjectFilter) { _filter.value = f; _selected.value = emptySet() }
    fun toggle(id: Long) { _selected.value = _selected.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { _selected.value = emptySet() }

    fun createProject(name: String) = viewModelScope.launch {
        val id = projectsDao.insert(ProjectEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        settingsRepo.setActiveProject(id)
    }

    fun setActiveProject(id: Long) = viewModelScope.launch { settingsRepo.setActiveProject(id) }

    fun projectName(id: Long?): String = id?.let { pid -> projects.value.firstOrNull { it.id == pid }?.name } ?: ""

    /** Downscaled render.jpg for the grid. */
    suspend fun thumbnail(s: SnapshotEntity): ImageBitmap? {
        thumbs.get(s.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = File(store.dirOf(s), SnapshotStore.RENDER)
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()?.also { thumbs.put(s.id, it) }
        }
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selected.value
        snapshots.value.filter { it.id in ids }.forEach { store.delete(it); thumbs.remove(it.id) }
        _selected.value = emptySet()
    }

    /** Share selected snapshots as one PDF report. */
    fun shareSelectedPdf() = viewModelScope.launch {
        val list = snapshots.value.filter { it.id in _selected.value }.sortedBy { it.createdAt }
        exporter.pdf(list, ::projectName)?.let { context.startActivity(exporter.shareIntent(listOf(it), "application/pdf")) }
    }

    /** Share selected snapshots as JPG images with values. */
    fun shareSelectedJpg() = viewModelScope.launch {
        val files = snapshots.value.filter { it.id in _selected.value }.map { File(store.dirOf(it), SnapshotStore.RENDER) }.filter { it.exists() }
        if (files.isEmpty()) return@launch
        // Copy to exports so the FileProvider can serve them.
        val out = files.map { f -> File(File(context.cacheDir, "exports").apply { mkdirs() }, "thermocamera-" + f.parentFile!!.name + ".jpg").also { f.copyTo(it, overwrite = true) } }
        context.startActivity(exporter.shareIntent(out, "image/jpeg"))
    }
}

@HiltViewModel
class SnapshotDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val dao: SnapshotDao,
    private val projectsDao: ProjectDao,
    private val store: SnapshotStore,
    private val exporter: SnapshotExporter,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: savedState.get<String>("id")?.toLongOrNull() ?: -1L

    val snapshot: StateFlow<SnapshotEntity?> = dao.byId(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val projects: StateFlow<List<ProjectEntity>> = projectsDao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _options = MutableStateFlow<RenderOptions?>(null)
    val options: StateFlow<RenderOptions?> = _options.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val rendered: StateFlow<Pair<ImageBitmap, RenderResult>?> = combine(snapshot.filterNotNull(), _options.filterNotNull()) { s, o -> s to o }
        .mapLatest { (s, o) ->
            withContext(Dispatchers.Default) { exporter.render(s, o, square = 720)?.let { it.bitmap.asImageBitmap() to it } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { snapshot.filterNotNull().first().let { _options.value = RenderOptions.of(it) } }
    }

    fun setOptions(o: RenderOptions) { _options.value = o }

    fun save(title: String, note: String, projectId: Long?) = viewModelScope.launch {
        snapshot.value?.let { dao.update(it.copy(title = title.trim(), note = note.trim(), projectId = projectId)) }
    }

    /** Keeps the re-rendered display (palette, range, mode, blend, emissivity) as the snapshot's settings. */
    fun keepDisplay() = viewModelScope.launch {
        val s = snapshot.value ?: return@launch
        val o = _options.value ?: return@launch
        val r = rendered.value?.second
        dao.update(
            s.copy(
                viewMode = o.viewMode.name, blend = o.blend, palette = o.palette.name, emissivity = o.emissivity,
                rangeMin = r?.range?.min ?: s.rangeMin, rangeMax = r?.range?.max ?: s.rangeMax,
            ),
        )
        // Refresh render.jpg so the gallery and plain sharing show the new look.
        withContext(Dispatchers.Default) {
            exporter.render(s, o)?.let { res ->
                File(store.dirOf(s), SnapshotStore.RENDER).outputStream().use { res.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                res.bitmap.recycle()
            }
        }
    }

    fun share(kind: ShareKind) = viewModelScope.launch {
        val s = snapshot.value ?: return@launch
        _busy.value = true
        try {
            val (file, mime) = when (kind) {
                ShareKind.JPG -> exporter.jpg(s, sideBySide = false) to "image/jpeg"
                ShareKind.SIDE_BY_SIDE -> exporter.jpg(s, sideBySide = true) to "image/jpeg"
                ShareKind.PDF -> exporter.pdf(listOf(s)) { pid -> projects.value.firstOrNull { it.id == pid }?.name ?: "" } to "application/pdf"
                ShareKind.CSV -> exporter.csv(s) to "text/csv"
            }
            file?.let { context.startActivity(exporter.shareIntent(listOf(it), mime)) }
        } finally {
            _busy.value = false
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        snapshot.value?.let { store.delete(it) }
        onDone()
    }
}

enum class ShareKind { JPG, SIDE_BY_SIDE, PDF, CSV }
