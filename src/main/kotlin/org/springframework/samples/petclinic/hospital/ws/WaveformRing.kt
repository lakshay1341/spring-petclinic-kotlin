package org.springframework.samples.petclinic.hospital.ws

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, per-admission ring buffer of the most recent waveform samples (ECG/PPG) for LIVE
 * display only. Waveform is watched live, never queried historically, so it is never persisted —
 * durable waveform storage (Postgres/bytea) stays the deferred bet. Bounded per admission; the map
 * entry is evicted when the device socket closes.
 *
 * Each ring also tracks the wall-clock time of its last append so the UI can show NO SIGNAL instead
 * of a frozen trace (a static "live" waveform on a clinical monitor is a patient-safety lie).
 */
@Component
class WaveformRing {

    private val rings = ConcurrentHashMap<Int, Ring>()

    fun append(admissionId: Int, samples: DoubleArray, periodMs: Double) {
        rings.getOrPut(admissionId) { Ring(CAPACITY) }.append(samples, periodMs)
    }

    fun snapshot(admissionId: Int): Snapshot? = rings[admissionId]?.snapshot()

    fun evict(admissionId: Int) {
        rings.remove(admissionId)
    }

    data class Snapshot(val values: DoubleArray, val period: Double, val ageMs: Long)

    // Fixed-array ring guarded by a monitor: written by WS container threads, read by HTTP poll
    // threads. A plain ArrayDeque would tear under concurrent append+read.
    private class Ring(private val capacity: Int) {
        private val buf = DoubleArray(capacity)
        private var size = 0
        private var head = 0
        private var period = 0.0
        private var lastAt = 0L

        @Synchronized
        fun append(samples: DoubleArray, periodMs: Double) {
            period = periodMs
            for (v in samples) {
                buf[head] = v
                head = (head + 1) % capacity
                if (size < capacity) size++
            }
            lastAt = System.currentTimeMillis()
        }

        @Synchronized
        fun snapshot(): Snapshot {
            val out = DoubleArray(size)
            val start = (head - size + capacity) % capacity
            for (i in 0 until size) out[i] = buf[(start + i) % capacity]
            return Snapshot(out, period, System.currentTimeMillis() - lastAt)
        }
    }

    companion object {
        // ponytail: ~4s of 256 Hz ECG. Bump if a faster waveform needs a longer visible window.
        private const val CAPACITY = 1024
    }
}
