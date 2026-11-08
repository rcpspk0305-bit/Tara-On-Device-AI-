package dev.voice.offline.core.audio

import kotlin.math.*

/**
 * Optional acoustic-model adapter: 13 MFCC + 13 Δ + 13 ΔΔ per frame.
 * Input: 16kHz mono PCM16. Not used by the default Vosk text-intent pipeline;
 * Vosk already performs its own acoustic feature extraction internally.
 * For direct acoustic classification, train/evaluate a compatible model first.
 */
class Mfcc39Extractor {
    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME = 400 // 25 ms
        const val HOP = 160 // 10 ms
        const val FFT = 512
        const val MEL_BANDS = 26
        const val COEFFS = 13
    }
    fun extract(pcm: ShortArray): List<FloatArray> {
        if (pcm.size < FRAME) return emptyList()
        val filterbank = melFilters()
        val base = ArrayList<FloatArray>()
        val spectrumReal = DoubleArray(FFT)
        val spectrumImag = DoubleArray(FFT)
        for (start in 0..pcm.size - FRAME step HOP) {
            spectrumReal.fill(0.0)
            spectrumImag.fill(0.0)
            for (i in 0 until FRAME) {
                val sample = pcm[start + i].toDouble() / 32768.0
                val previous = if (i == 0 && start == 0) 0.0
                    else pcm[start + i - 1].toDouble() / 32768.0
                val emphasis = sample - 0.97 * previous
                val window = 0.54 - 0.46 * cos(2.0 * PI * i / (FRAME - 1))
                spectrumReal[i] = emphasis * window
            }
            fft(spectrumReal, spectrumImag)
            val power = DoubleArray(FFT / 2 + 1) { index ->
                (spectrumReal[index].pow(2) + spectrumImag[index].pow(2)) / FFT
            }
            val melLog = DoubleArray(MEL_BANDS) { band ->
                var sum = 0.0
                for (bin in power.indices) sum += power[bin] * filterbank[band][bin]
                ln(sum.coerceAtLeast(1e-12))
            }
            base += FloatArray(COEFFS) { coefficient ->
                var value = 0.0
                for (band in 0 until MEL_BANDS) {
                    value += melLog[band] * cos(PI * coefficient * (band + 0.5) / MEL_BANDS)
                }
                value.toFloat()
            }
        }
        val delta = derivatives(base)
        val delta2 = derivatives(delta)
        return base.indices.map { i -> FloatArray(39).also { out ->
            base[i].copyInto(out, 0)
            delta[i].copyInto(out, 13)
            delta2[i].copyInto(out, 26)
        } }
    }

    private fun derivatives(values: List<FloatArray>): List<FloatArray> = values.indices.map { t ->
        FloatArray(COEFFS) { k ->
            var sum = 0f
            for (n in 1..2) {
                val before = values[(t - n).coerceAtLeast(0)][k]
                val after = values[(t + n).coerceAtMost(values.lastIndex)][k]
                sum += n * (after - before)
            }
            sum / 10f // 2 * Σ(n²), n = 1..2
        }
    }

    private fun melFilters(): Array<DoubleArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
        val low = hzToMel(20.0)
        val high = hzToMel(7600.0)
        val bins = IntArray(MEL_BANDS + 2) { i ->
            (((FFT + 1) * melToHz(low + (high - low) * i / (MEL_BANDS + 1))) / SAMPLE_RATE)
                .toInt().coerceIn(0, FFT / 2)
        }
        return Array(MEL_BANDS) { band ->
            val lower = bins[band]
            val center = bins[band + 1]
            val upper = bins[band + 2]
            DoubleArray(FFT / 2 + 1) { bin ->
                when {
                    bin in lower until center && center > lower ->
                        (bin - lower).toDouble() / (center - lower)
                    bin in center until upper && upper > center ->
                        (upper - bin).toDouble() / (upper - center)
                    else -> 0.0
                }
            }
        }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val a = real[i]; real[i] = real[j]; real[j] = a
                val b = imag[i]; imag[i] = imag[j]; imag[j] = b
            }
        }
        var size = 2
        while (size <= n) {
            val angle = -2.0 * PI / size
            val wr = cos(angle)
            val wi = sin(angle)
            var block = 0
            while (block < n) {
                var ur = 1.0
                var ui = 0.0
                for (k in 0 until size / 2) {
                    val even = block + k
                    val odd = even + size / 2
                    val tr = ur * real[odd] - ui * imag[odd]
                    val ti = ur * imag[odd] + ui * real[odd]
                    real[odd] = real[even] - tr
                    imag[odd] = imag[even] - ti
                    real[even] += tr
                    imag[even] += ti
                    val nextR = ur * wr - ui * wi
                    ui = ur * wi + ui * wr
                    ur = nextR
                }
                block += size
            }
            size *= 2
        }
    }
}
