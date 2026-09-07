package com.pixense.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pixense.app.data.model.CameraPhoto
import com.pixense.app.data.repository.CameraCaptureRepository

class DcimPagingSource(
    private val repository: CameraCaptureRepository
) : PagingSource<Int, CameraPhoto>() {

    override fun getRefreshKey(state: PagingState<Int, CameraPhoto>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(anchorPage.data.size)
                ?: anchorPage?.nextKey?.minus(anchorPage.data.size)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CameraPhoto> {
        val offset = params.key ?: 0
        val loadSize = params.loadSize.coerceAtLeast(PAGE_SIZE)

        return try {
            // Request loadSize + 1 to determine if more items exist
            val photosWithExtra = repository.queryDcimPhotosPaged(offset = offset, limit = loadSize + 1)
            val hasMore = photosWithExtra.size > loadSize
            val photos = if (hasMore) photosWithExtra.take(loadSize) else photosWithExtra

            LoadResult.Page(
                data = photos,
                prevKey = if (offset == 0) null else (offset - loadSize).coerceAtLeast(0),
                nextKey = if (hasMore) offset + photos.size else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
