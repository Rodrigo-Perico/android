package com.example.voiceprivacy

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var isRecording = false

    private val sampleRate = 44100
    private val channels = 1
    private val bitsPerSample = 16

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            0
        )

        val btn = findViewById<Button>(R.id.btnToggle)

        btn.setOnClickListener {
            if (!isRecording) {
                isRecording = true
                btn.text = "Parar"
                startRecording()
            } else {
                isRecording = false
                btn.text = "Iniciar"
            }
        }
    }

    private fun startRecording() {
        thread {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "voiceprivacy"
            )
            dir.mkdirs()

            val wavFile = File(dir, "output.wav")
            val output = FileOutputStream(wavFile)

            // Header WAV placeholder
            writeWavHeader(output, 0)

            val buffer = ByteArray(bufferSize)
            var totalAudioBytes = 0

            recorder.startRecording()

            while (isRecording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    output.write(buffer, 0, read)
                    totalAudioBytes += read
                }
            }

            recorder.stop()
            recorder.release()
            output.close()

            // Corrige o header com tamanho real
            fixWavHeader(wavFile, totalAudioBytes)
        }
    }

    private fun writeWavHeader(out: FileOutputStream, dataLength: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)

        // RIFF
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // ChunkSize (placeholder)
        writeInt(header, 4, 36 + dataLength)

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        writeInt(header, 16, 16)           // Subchunk1Size
        writeShort(header, 20, 1)          // AudioFormat PCM
        writeShort(header, 22, channels)
        writeInt(header, 24, sampleRate)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, blockAlign)
        writeShort(header, 34, bitsPerSample)

        // data
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        writeInt(header, 40, dataLength)

        out.write(header, 0, 44)
    }

    private fun fixWavHeader(file: File, dataLength: Int) {
        val raf = file.outputStream().channel

        val chunkSize = 36 + dataLength

        val buffer = java.nio.ByteBuffer.allocate(4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // ChunkSize
        raf.position(4)
        buffer.putInt(chunkSize)
        buffer.flip()
        raf.write(buffer)

        // Subchunk2Size
        buffer.clear()
        raf.position(40)
        buffer.putInt(dataLength)
        buffer.flip()
        raf.write(buffer)

        raf.close()
    }

    private fun writeInt(header: ByteArray, offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = ((value shr 8) and 0xff).toByte()
        header[offset + 2] = ((value shr 16) and 0xff).toByte()
        header[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeShort(header: ByteArray, offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
}
