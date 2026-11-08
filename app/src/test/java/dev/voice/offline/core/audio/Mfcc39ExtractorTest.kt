package dev.voice.offline.core.audio

import dev.voice.offline.core.audio.Mfcc39Extractor
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class Mfcc39ExtractorTest {
    @Test fun extractsFinite39DimensionalVectors() {
        val pcm = ShortArray(16_000) { t -> (sin(2 * PI * t * 440 / 16000) * 14000).toInt().toShort() }
        val vectors = Mfcc39Extractor().extract(pcm)
        assertTrue(vectors.isNotEmpty())
        assertTrue(vectors.all { row -> row.size == 39 && row.all { !it.isNaN() && !it.isInfinite() } })
        assertTrue(Mfcc39Extractor().extract(ShortArray(100)).isEmpty())
    }
}
