package com.pixense.app

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.pixense.app.data.paging.DcimPagingSource
import com.pixense.app.data.repository.CameraCaptureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DcimPagingSourceTest {

    @Test
    fun `dcim paging source initializes and handles empty media store`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = CameraCaptureRepository.getInstance(context)
        val pagingSource = DcimPagingSource(repository)

        val params = PagingSource.LoadParams.Refresh<Int>(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )

        val result = pagingSource.load(params)
        assertTrue(result is PagingSource.LoadResult.Page)

        val page = result as PagingSource.LoadResult.Page
        assertEquals(0, page.data.size)
        assertNull(page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun `application configures coil image loader with memory and disk caches`() {
        val app = ApplicationProvider.getApplicationContext<CameraAiApplication>()
        val imageLoader = app.newImageLoader()

        assertNotNull("ImageLoader must not be null", imageLoader)
        assertNotNull("MemoryCache must be configured", imageLoader.memoryCache)
        assertNotNull("DiskCache must be configured", imageLoader.diskCache)
    }
}
