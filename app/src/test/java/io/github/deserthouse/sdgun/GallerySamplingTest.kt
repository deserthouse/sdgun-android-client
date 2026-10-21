package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.feature.media.computeSampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** AV-07：长图/大图采样纯函数（旧条件要求宽高同时够大，1000×30000 永不采样——复审反例） */
class GallerySamplingTest {

    @Test
    fun `small image is not sampled`() {
        assertEquals(1, computeSampleSize(800, 600, maxDimen = 2048, maxPixels = 8_000_000))
        assertEquals(1, computeSampleSize(2048, 2048, maxDimen = 2048, maxPixels = 8_000_000))
    }

    @Test
    fun `oversized long image is sampled - the review counterexample`() {
        // 1000×30000：旧条件 1000/2<1024 永不采样 → 解码 30M 像素长图
        val s = computeSampleSize(1000, 30000, maxDimen = 2048, maxPixels = 8_000_000)
        assertEquals(16, s) // 62×1875，最长边入 2048 界
    }

    @Test
    fun `high resolution landscape is sampled`() {
        assertEquals(2, computeSampleSize(4000, 3000, maxDimen = 2048, maxPixels = 8_000_000))
    }

    @Test
    fun `pixel budget bounds midsize images`() {
        // 3000×3000=9M 像素超 8M 预算 → 采样一次到 1500×1500
        assertEquals(2, computeSampleSize(3000, 3000, maxDimen = 2048, maxPixels = 8_000_000))
    }

    @Test
    fun `cover target uses tighter bound`() {
        // 封面 512 档：800×600 也要采样到 ≤512
        assertEquals(2, computeSampleSize(800, 600, maxDimen = 512, maxPixels = 8_000_000))
    }

    @Test
    fun `extreme input terminates at cap 64`() {
        assertEquals(64, computeSampleSize(100000, 100000, maxDimen = 2048, maxPixels = 8_000_000))
    }
}
