package info.dourok.voicebot

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class AudioRecorder(
    private val sampleRate: Int,
    private val channels: Int,
    private val frameSizeMs: Int
) {
    companion object {
        private const val TAG = "AudioRecorder"
    }

    private val channelConfig = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private val frameSize = (sampleRate * frameSizeMs) / 1000
    private val frameBytes = frameSize * channels * 2 // 16-bit PCM
    private val audioChannel = Channel<ByteArray>(capacity = 50)


    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<ByteArray> {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        ).apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                // 初始化 AEC
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(audioSessionId).apply {
                        enabled = true
                        Log.i(TAG, "AEC initialized")
                    }
                } else {
                    Log.w(TAG, "AEC not available on this device")
                }

                if(NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(audioSessionId).apply {
                        enabled = true
                        Log.i(TAG, "NS initialized")
                    }
                } else {
                    Log.w(TAG, "NS not available on this device")
                }

                startRecording()
                Log.i(TAG, "Started recording")
            } else {
                throw IllegalStateException("Failed to initialize AudioRecord")
            }
        }

        Thread {
            val buffer = ByteArray(frameBytes)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, frameBytes) ?: 0
                if (read > 0) {
                    audioChannel.trySend(buffer.copyOf(read)).isSuccess
                }
            }
        }.start()

        return audioChannel.receiveAsFlow()
    }

    fun stopRecording() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        aec?.enabled = false
        aec?.release()
        aec = null
        ns?.enabled = false
        ns?.release()
        ns = null
        audioChannel.close()
        Log.i(TAG, "Stopped recording")
    }
}