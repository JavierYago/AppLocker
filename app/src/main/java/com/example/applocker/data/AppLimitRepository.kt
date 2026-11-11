package com.example.applocker.data

import com.example.applocker.data.local.AppLimitDao
import com.example.applocker.data.local.toDomain
import com.example.applocker.data.local.toEntity
import com.example.applocker.domain.model.AppLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppLimitRepository(private val dao: AppLimitDao) {

    fun observeLimits(): Flow<List<AppLimit>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun hasEnabledLimits(): Boolean = dao.countEnabled() > 0

    suspend fun save(limit: AppLimit) = dao.upsert(limit.toEntity())

    suspend fun delete(packageName: String) = dao.delete(packageName)
}
