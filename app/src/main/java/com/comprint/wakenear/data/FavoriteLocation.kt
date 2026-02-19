package com.comprint.wakenear.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_locations")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Dao
interface FavoriteLocationDao {
    @Query("SELECT * FROM favorite_locations ORDER BY name ASC")
    fun getAll(): Flow<List<FavoriteLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: FavoriteLocation)

    @Delete
    suspend fun delete(location: FavoriteLocation)
}

@Database(entities = [FavoriteLocation::class], version = 1, exportSchema = false)
abstract class WakeNearDatabase : RoomDatabase() {
    abstract fun favoriteLocationDao(): FavoriteLocationDao

    companion object {
        @Volatile
        private var INSTANCE: WakeNearDatabase? = null

        fun getInstance(context: Context): WakeNearDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WakeNearDatabase::class.java,
                    "wakenear_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
