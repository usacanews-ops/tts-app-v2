package com.app.empty_activity_kotlin

import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.widget.*
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
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerVoice: Spinner

    private var availableLocales = mutableListOf<Locale>()
    private var availableVoices = mutableListOf<Voice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnDownload = findViewById(R.id.btnDownload)
        seekPitch = findViewById(R.id.seekPitch)
        seekSpeed = findViewById(R.id.seekSpeed)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        spinnerVoice = findViewById(R.id.spinnerVoice)

        btnGenerate.isEnabled = false
        btnDownload.isEnabled = false

        tts = TextToSpeech(this, this)

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
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "TTS_Audio_${System.currentTimeMillis()}.wav"
                val audioFile = File(downloadsDir, fileName)

                val result = tts.synthesizeToFile(text, null, audioFile, "tts_download")
                if (result == TextToSpeech.SUCCESS) {
                    Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupLanguageSpinner()
            btnGenerate.isEnabled = true
            btnDownload.isEnabled = true
        } else {
            Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLanguageSpinner() {
        try {
            val locales = tts.availableLanguages ?: emptySet()
            availableLocales.clear()
            availableLocales.addAll(locales.sortedBy { it.displayLanguage })

            val languageNames = availableLocales.map { "${it.displayLanguage} (${it.country})" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageNames)
            spinnerLanguage.adapter = adapter

            spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedLocale = availableLocales[position]
                    tts.language = selectedLocale
                    updateVoicesForLanguage(selectedLocale)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load languages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateVoicesForLanguage(locale: Locale) {
        try {
            val allVoices = tts.voices ?: emptySet()
            availableVoices.clear()
            
            // Find all voices that match the currently selected language
            availableVoices.addAll(allVoices.filter { it.locale.language == locale.language })

            val voiceNames = if (availableVoices.isEmpty()) {
                listOf("Default Voice")
            } else {
                // Formatting the technical voice name to be slightly more readable
                availableVoices.map { it.name.substringAfterLast("-").replace("_", " ") }
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames)
            spinnerVoice.adapter = adapter

            spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (availableVoices.isNotEmpty()) {
                        tts.voice = availableVoices[position]
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            // Some older Android devices crash when calling tts.voices
        }
    }

    private fun applyTtsSettings() {
        var pitch = seekPitch.progress / 50f
        if (pitch < 0.1f) pitch = 0.1f

        var speed = seekSpeed.progress / 50f
        if (speed < 0.1f) speed = 0.1f

        tts.setPitch(pitch)
        tts.setSpeechRate(speed)
    }

    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
