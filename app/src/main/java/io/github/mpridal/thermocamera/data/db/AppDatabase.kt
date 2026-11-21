package io.github.mpridal.thermocamera.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Named calibration profile (zadání F2): range, emissivity, reflected temperature, offset, gain. */
@Entity(tableName = "calibration_profile")
data class CalibrationProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "range_preset") val rangePreset: String,
    @ColumnInfo(name = "custom_min") val customMin: Float,
    @ColumnInfo(name = "custom_max") val customMax: Float,
    val emissivity: Float,
    val reflected: Float,
    val offset: Float,
    val gain: Float,
)

/** Project grouping snapshots (zadání F4); snapshots without a project belong to "No project". */
@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Metadata of a saved measurement snapshot (zadání F4). The files (render.jpg, thermal.f32,
 * thermal.json, visible.jpg, photo.jpg, alignment.json) are in `snapshots/<dir>/` of app storage.
 */
@Entity(tableName = "snapshot")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "project_id") val projectId: Long?,
    val dir: String,
    val title: String,
    val note: String,
    @ColumnInfo(name = "view_mode") val viewMode: String,
    val blend: Float,
    val palette: String,
    @ColumnInfo(name = "range_preset") val rangePreset: String,
    @ColumnInfo(name = "range_min") val rangeMin: Float,
    @ColumnInfo(name = "range_max") val rangeMax: Float,
    val emissivity: Float,
    val reflected: Float,
    val offset: Float,
    val gain: Float,
    @ColumnInfo(name = "inside_temp") val insideTemp: Float,
    @ColumnInfo(name = "outside_temp") val outsideTemp: Float,
    @ColumnInfo(name = "frsi_limit") val fRsiLimit: Float,
    /** User points as "x,y;x,y" in thermal-grid coordinates. */
    val points: String,
    @ColumnInfo(name = "orientation_rotation") val orientationRotation: Int,
    @ColumnInfo(name = "orientation_mirror") val orientationMirror: Boolean,
    /** MODULE or PHONE – which camera took the normal image. */
    @ColumnInfo(name = "camera_source") val cameraSource: String,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "min_c") val minC: Float,
    @ColumnInfo(name = "max_c") val maxC: Float,
    @ColumnInfo(name = "center_c") val centerC: Float,
    @ColumnInfo(name = "has_visible") val hasVisible: Boolean,
    @ColumnInfo(name = "has_photo") val hasPhoto: Boolean,
)

@Dao
interface CalibrationProfileDao {
    @Query("SELECT * FROM calibration_profile ORDER BY name")
    fun all(): Flow<List<CalibrationProfileEntity>>

    @Insert
    suspend fun insert(p: CalibrationProfileEntity): Long

    @Delete
    suspend fun delete(p: CalibrationProfileEntity)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY created_at DESC")
    fun all(): Flow<List<ProjectEntity>>

    @Insert
    suspend fun insert(p: ProjectEntity): Long
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshot ORDER BY created_at DESC")
    fun all(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshot WHERE id = :id")
    fun byId(id: Long): Flow<SnapshotEntity?>

    @Query("SELECT * FROM snapshot WHERE id = :id")
    suspend fun get(id: Long): SnapshotEntity?

    @Insert
    suspend fun insert(s: SnapshotEntity): Long

    @Update
    suspend fun update(s: SnapshotEntity)

    @Delete
    suspend fun delete(s: SnapshotEntity)
}

@Database(
    entities = [CalibrationProfileEntity::class, ProjectEntity::class, SnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): CalibrationProfileDao
    abstract fun projects(): ProjectDao
    abstract fun snapshots(): SnapshotDao
}
