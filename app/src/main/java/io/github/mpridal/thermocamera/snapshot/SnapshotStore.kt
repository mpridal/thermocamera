package io.github.mpridal.thermocamera.snapshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.BuildConfig
import io.github.mpridal.thermocamera.core.CropSquare
import io.github.mpridal.thermocamera.data.db.SnapshotDao
import io.github.mpridal.thermocamera.data.db.SnapshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files of one snapshot (zadání F4) in `files/snapshots/<dir>/`:
 * render.jpg, thermal.f32 (float32 LE, °C before user corrections), thermal.json, visible.jpg,
 * photo.jpg + alignment.json (phone camera). Metadata in Room ([SnapshotEntity]).
 */
@Singleton
class SnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SnapshotDao,
) {
    val root: File get() = File(context.filesDir, "snapshots").apply { mkdirs() }

    fun dirOf(s: SnapshotEntity) = File(root, s.dir)

    /** A fresh, unique folder for a new snapshot. */
    fun newDir(): File {
        val base = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        var d = File(root, base)
        var i = 1
        while (d.exists()) d = File(root, "$base-${i++}")
        return d.apply { mkdirs() }
    }

    /**
     * Writes the files and the database row. [photo] is an already written photo.jpg in [dir]
     * (phone camera), [photoCrop] its thermal square.
     */
    suspend fun save(
        dir: File,
        entity: SnapshotEntity,
        raw: FloatArray,
        width: Int,
        height: Int,
        render: Bitmap,
        visible: Bitmap?,
        visibleCrop: CropSquare?,
        photoCrop: CropSquare?,
        saveToGallery: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        writeThermal(dir, raw, width, height)
        File(dir, RENDER).outputStream().use { render.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        visible?.let { v -> File(dir, VISIBLE).outputStream().use { v.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        writeAlignment(dir, visibleCrop, photoCrop)
        val id = dao.insert(entity.copy(dir = dir.name, hasVisible = visible != null, hasPhoto = File(dir, PHOTO).exists()))
        if (saveToGallery) runCatching { copyToGallery(File(dir, RENDER), "ThermoCamera_${dir.name}.jpg") }
        id
    }

    private fun writeThermal(dir: File, raw: FloatArray, w: Int, h: Int) {
        val bb = ByteBuffer.allocate(raw.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        raw.forEach { bb.putFloat(it) }
        File(dir, THERMAL).writeBytes(bb.array())
        File(dir, THERMAL_JSON).writeText(
            JSONObject()
                .put("formatVersion", 1)
                .put("width", w)
                .put("height", h)
                .put("type", "float32-le")
                .put("unit", "celsius")
                .put("corrections", "none – before emissivity, offset and gain")
                .put("orientation", "as displayed (module orientation applied)")
                .put("app", BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME)
                .toString(2),
        )
    }

    private fun writeAlignment(dir: File, visible: CropSquare?, photo: CropSquare?) {
        fun crop(c: CropSquare) = JSONObject().put("cx", c.cx).put("cy", c.cy).put("side", c.side).put("angleDeg", c.angleDeg)
        fun matrix(c: CropSquare): JSONArray {
            // Maps normalised thermal coordinates (u, v, 1) to image pixels.
            val a = Math.toRadians(c.angleDeg.toDouble())
            val s = c.side
            val m00 = s * Math.cos(a); val m01 = -s * Math.sin(a)
            val m10 = s * Math.sin(a); val m11 = s * Math.cos(a)
            val tx = c.cx - 0.5 * (m00 + m01); val ty = c.cy - 0.5 * (m10 + m11)
            return JSONArray(listOf(m00, m01, tx, m10, m11, ty, 0.0, 0.0, 1.0))
        }
        val o = JSONObject().put("formatVersion", 1)
        visible?.let { o.put("visible", crop(it)).put("thermalToVisible", matrix(it)) }
        photo?.let { o.put("photo", crop(it)).put("thermalToPhoto", matrix(it)) }
        File(dir, ALIGNMENT).writeText(o.toString(2))
    }

    fun readThermal(s: SnapshotEntity): Triple<FloatArray, Int, Int>? = runCatching {
        val d = dirOf(s)
        val meta = JSONObject(File(d, THERMAL_JSON).readText())
        val w = meta.getInt("width"); val h = meta.getInt("height")
        val bb = ByteBuffer.wrap(File(d, THERMAL).readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        Triple(FloatArray(w * h) { bb.float }, w, h)
    }.getOrNull()

    /** Visible image for re-rendering: the phone photo (downscaled) if present, else visible.jpg; with its crop. */
    fun readVisible(s: SnapshotEntity, maxSide: Int = 2048): Pair<Bitmap, CropSquare?>? = runCatching {
        val d = dirOf(s)
        val align = File(d, ALIGNMENT).takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        fun crop(key: String, scale: Float) = align?.optJSONObject(key)?.let {
            CropSquare(it.getDouble("cx").toFloat() * scale, it.getDouble("cy").toFloat() * scale, it.getDouble("side").toFloat() * scale, it.getDouble("angleDeg").toFloat())
        }
        val photo = File(d, PHOTO)
        if (photo.exists() && align?.has("photo") == true) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(photo.path, it) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            bmp to crop("photo", bmp.width.toFloat() / bounds.outWidth)
        } else {
            val v = File(d, VISIBLE).takeIf { it.exists() } ?: return@runCatching null
            (BitmapFactory.decodeFile(v.path) ?: return@runCatching null) to crop("visible", 1f)
        }
    }.getOrNull()

    /** Removes the snapshot's files and row (the user confirmed it in the gallery). */
    suspend fun delete(s: SnapshotEntity) = withContext(Dispatchers.IO) {
        dirOf(s).deleteRecursively()
        dao.delete(s)
    }

    /** Copy of the render into Pictures/ThermoCamera (zadání F4), without storage permission on API 29+. */
    private fun copyToGallery(file: File, name: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ThermoCamera")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ThermoCamera").apply { mkdirs() }
            file.copyTo(File(dir, name), overwrite = true)
        }
    }

    companion object {
        const val RENDER = "render.jpg"
        const val THERMAL = "thermal.f32"
        const val THERMAL_JSON = "thermal.json"
        const val VISIBLE = "visible.jpg"
        const val PHOTO = "photo.jpg"
        const val ALIGNMENT = "alignment.json"
    }
}
