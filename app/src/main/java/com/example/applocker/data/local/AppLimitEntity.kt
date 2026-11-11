package com.example.applocker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.applocker.domain.model.AppLimit

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int,
    val alertThresholdsMinutes: List<Int>,
    val enabled: Boolean,
)

fun AppLimitEntity.toDomain() = AppLimit(
    packageName = packageName,
    dailyLimitMinutes = dailyLimitMinutes,
    alertThresholdsMinutes = alertThresholdsMinutes,
    enabled = enabled,
)

fun AppLimit.toEntity() = AppLimitEntity(
    packageName = packageName,
    dailyLimitMinutes = dailyLimitMinutes,
    alertThresholdsMinutes = alertThresholdsMinutes,
    enabled = enabled,
)

/** Room no sabe guardar listas: los umbrales se persisten como "30,10". */
class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        value.split(',').mapNotNull { it.trim().toIntOrNull() }
}
