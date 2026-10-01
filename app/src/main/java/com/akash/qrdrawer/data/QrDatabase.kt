package com.akash.qrdrawer.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "qr_codes")
data class QrCode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val bank: String = "",
    @ColumnInfo(name = "upi_id") val upiId: String = "",
    /** Decoded QR text. Empty when decoding failed and only [originalImagePath] is shown. */
    val payload: String = "",
    /** ARGB colour used for the card stripe. */
    val color: Int = QR_COLORS.first(),
    val note: String = "",
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "original_image_path") val originalImagePath: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    val hasPayload: Boolean get() = payload.isNotBlank()
}

/** Card colours offered in the editor. */
val QR_COLORS = listOf(
    0xFF2166E8.toInt(),
    0xFF0F8A5F.toInt(),
    0xFFD9480F.toInt(),
    0xFF7B3FE4.toInt(),
    0xFFC2185B.toInt(),
    0xFF00838F.toInt(),
    0xFFB08900.toInt(),
    0xFF455A64.toInt(),
)

@Dao
interface QrDao {
    /** Default QR first, then the user's order. */
    @Query("SELECT * FROM qr_codes ORDER BY is_default DESC, sort_order ASC, id ASC")
    fun observeAll(): Flow<List<QrCode>>

    @Query("SELECT * FROM qr_codes ORDER BY is_default DESC, sort_order ASC, id ASC")
    suspend fun getAll(): List<QrCode>

    @Query("SELECT * FROM qr_codes WHERE id = :id")
    suspend fun get(id: Long): QrCode?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM qr_codes")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(qr: QrCode): Long

    @Update
    suspend fun update(qr: QrCode)

    @Update
    suspend fun updateAll(qrs: List<QrCode>)

    @Query("DELETE FROM qr_codes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE qr_codes SET is_default = 0 WHERE id != :keepId")
    suspend fun clearDefaultExcept(keepId: Long)

    @Transaction
    suspend fun save(qr: QrCode): Long {
        val id = if (qr.id == 0L) {
            insert(qr.copy(sortOrder = maxSortOrder() + 1))
        } else {
            update(qr)
            qr.id
        }
        if (qr.isDefault) clearDefaultExcept(id)
        return id
    }
}

@Database(entities = [QrCode::class], version = 1, exportSchema = true)
abstract class QrDatabase : RoomDatabase() {
    abstract fun qrDao(): QrDao

    companion object {
        @Volatile private var instance: QrDatabase? = null

        fun get(context: Context): QrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, QrDatabase::class.java, "qr_drawer.db")
                .build()
                .also { instance = it }
        }
    }
}
