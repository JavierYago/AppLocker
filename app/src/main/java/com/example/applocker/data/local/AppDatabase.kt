package com.example.applocker.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Query("SELECT COUNT(*) FROM app_limits WHERE enabled = 1")
    suspend fun countEnabled(): Int

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Database(entities = [AppLimitEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "applocker.db").build()
    }
}
