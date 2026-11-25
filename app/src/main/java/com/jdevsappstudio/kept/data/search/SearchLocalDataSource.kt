package com.jdevsappstudio.kept.data.search

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.SearchHit
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.db.SaveWithDetails
import com.jdevsappstudio.kept.data.db.SearchDao
import com.jdevsappstudio.kept.data.library.TypeChips
import com.jdevsappstudio.kept.data.library.toSave
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Full-text search over everything Kept read, on device, offline (k-search). Every call is main-safe. */
interface SearchLocalDataSource {
    /** Paged hits, title hits first then newest (H54). A blank query gives nothing. */
    fun pagedHits(query: SearchQuery, type: SaveType?): Flow<PagingData<SearchHit>>

    /** Type chips over the hits, biggest first ("Place 4 · Event 1 · Recipe 1"). */
    fun observeTypeChips(query: SearchQuery): Flow<List<TypeChip>>
}

class RoomSearchDataSource(private val dao: SearchDao) : SearchLocalDataSource {

    override fun pagedHits(query: SearchQuery, type: SaveType?): Flow<PagingData<SearchHit>> {
        if (query.isBlank) return flowOf(PagingData.empty())
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            dao.paging(query.match, query.titleWord, type?.wire, SearchHit.HIT_START.toString(), SearchHit.HIT_END.toString())
        }.flow.map { page ->
            page.map { SearchHit(SaveWithDetails(it.save, it.tags, it.items).toSave(), it.snippet) }
        }
    }

    override fun observeTypeChips(query: SearchQuery): Flow<List<TypeChip>> {
        if (query.isBlank) return flowOf(emptyList())
        return dao.observeTypeCounts(query.match)
            .map { rows -> TypeChips.from(rows.associate { it.type to it.n }, preferred = emptyList()) }
            .distinctUntilChanged()
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
