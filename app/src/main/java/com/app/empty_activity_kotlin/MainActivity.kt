package com.app.empty_activity_kotlin
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var editText: EditText
    private lateinit var btnGenerate: Button
    private lateinit var btnDownload: Button
    private lateinit var seekPitch: SeekBar
    private lateinit var seekSpeed: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnDownload = findViewById(R.id.btnDownload)
        seekPitch = findViewById(R.id.seekPitch)
        seekSpeed = findViewById(R.id.seekSpeed)

        tts = TextToSpeech(this, this)
        btnGenerate.isEnabled = false
        btnDownload.isEnabled = false

        btnGenerate.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                applyTtsSettings()
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
        }

        btnDownload.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                applyTtsSettings()
                
                // Save to the public Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "TTS_Audio_${System.currentTimeMillis()}.wav"
                val audioFile = File(downloadsDir, fileName)

                val result = tts.synthesizeToFile(text, null, audioFile, "tts_download")
                
                if (result == TextToSpeech.SUCCESS) {
                    Toast.makeText(this, "Audio saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyTtsSettings() {
        // Convert 0-100 SeekBar progress to 0.1f - 2.0f float for TTS engine
        // 50 progress = 1.0f (Normal)
        var pitch = seekPitch.progress / 50f
        if (pitch < 0.1f) pitch = 0.1f // Prevent 0 pitch which causes errors

        var speed = seekSpeed.progress / 50f
        if (speed < 0.1f) speed = 0.1f

        tts.setPitch(pitch)
        tts.setSpeechRate(speed)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            } else {
                btnGenerate.isEnabled = true
                btnDownload.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
