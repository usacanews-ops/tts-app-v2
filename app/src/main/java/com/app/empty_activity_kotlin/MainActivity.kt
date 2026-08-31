package com.app.empty_activity_kotlin

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var editText: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGenerate: Button
    private lateinit var btnDownload: Button
    private lateinit var seekPitch: SeekBar
    private lateinit var seekSpeed: SeekBar
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerVoice: Spinner

    private var availableLocales = mutableListOf<Locale>()
    private var availableVoices = mutableListOf<Voice>()

    // Track download progress
    private var expectedDownloads = 0
    private var completedDownloads = 0
    private val pendingFiles = mutableMapOf<String, File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        tvCharCount = findViewById(R.id.tvCharCount)
        progressBar = findViewById(R.id.progressBar)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnDownload = findViewById(R.id.btnDownload)
        seekPitch = findViewById(R.id.seekPitch)
        seekSpeed = findViewById(R.id.seekSpeed)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        spinnerVoice = findViewById(R.id.spinnerVoice)

        btnGenerate.isEnabled = false
        btnDownload.isEnabled = false

        // Feature 3: Live Character Counter with Red Text Warning
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                if (length > 4000) {
                    tvCharCount.setTextColor(Color.RED)
                    tvCharCount.text = "$length chars (Over 4000 limit: Will save as multiple files)"
                } else {
                    tvCharCount.setTextColor(Color.parseColor("#666666"))
                    tvCharCount.text = "$length characters"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        tts = TextToSpeech(this, this)

        btnGenerate.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                applyTtsSettings()
                speakInChunks(text)
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
        }

        btnDownload.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                applyTtsSettings()
                downloadInChunks(text)
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupLanguageSpinner()
            setupProgressListener()
            btnGenerate.isEnabled = true
            btnDownload.isEnabled = true
        } else {
            Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupProgressListener() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Feature 1: Only show progress bar for downloads, not for speaking
                if (utteranceId?.startsWith("DOWNLOAD") == true) {
                    runOnUiThread { progressBar.visibility = View.VISIBLE }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("DOWNLOAD") == true) {
                    // Feature 2 Fix: Safely copy the completed temp file to the public Downloads folder
                    val tempFile = pendingFiles[utteranceId]
                    if (tempFile != null && tempFile.exists()) {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val destFile = File(downloadsDir, tempFile.name)
                        
                        // Copy data and delete the temporary file
                        tempFile.copyTo(destFile, overwrite = true)
                        tempFile.delete()

                        completedDownloads++
                        
                        // Check if all chunks are finished processing
                        if (completedDownloads == expectedDownloads) {
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                btnDownload.isEnabled = true
                                Toast.makeText(this@MainActivity, "Saved $completedDownloads audio file(s) to Downloads!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnDownload.isEnabled = true
                    Toast.makeText(this@MainActivity, "Error processing audio", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun speakInChunks(text: String) {
        val maxLength = TextToSpeech.getMaxSpeechInputLength() - 100 
        val chunks = text.chunked(maxLength)
        
        for (i in chunks.indices) {
            val utteranceId = "SPEAK_$i"
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(chunks[i], queueMode, null, utteranceId)
        }
    }

    private fun downloadInChunks(text: String) {
        val maxLength = TextToSpeech.getMaxSpeechInputLength() - 100
        val chunks = text.chunked(maxLength)
        
        expectedDownloads = chunks.size
        completedDownloads = 0
        pendingFiles.clear()
        
        // Disable button while processing
        btnDownload.isEnabled = false
        val baseName = "TTS_Audio_${System.currentTimeMillis()}"

        if (chunks.size > 1) {
            Toast.makeText(this, "Processing large text... Please wait.", Toast.LENGTH_SHORT).show()
        }

        for (i in chunks.indices) {
            val utteranceId = "DOWNLOAD_$i"
            val fileSuffix = if (chunks.size > 1) "_Part${i + 1}" else ""
            val fileName = "$baseName$fileSuffix.wav"
            
            // Save to app's safe directory first to avoid Android 11+ permission restrictions
            val tempFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            pendingFiles[utteranceId] = tempFile
            
            tts.synthesizeToFile(chunks[i], null, tempFile, utteranceId)
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
        } catch (e: Exception) {}
    }

    private fun updateVoicesForLanguage(locale: Locale) {
        try {
            val allVoices = tts.voices ?: emptySet()
            availableVoices.clear()
            availableVoices.addAll(allVoices.filter { it.locale.language == locale.language })
            availableVoices.sortBy { it.isNetworkConnectionRequired }

            val voiceNames = if (availableVoices.isEmpty()) {
                listOf("Default Speaker")
            } else {
                availableVoices.mapIndexed { index, voice ->
                    val nameLower = voice.name.toLowerCase(Locale.US)
                    val features = voice.features ?: emptySet()
                    
                    val isFemale = nameLower.contains("female") || features.any { it.toLowerCase(Locale.US).contains("female") }
                    val isMale = !isFemale && (nameLower.contains("male") || features.any { it.toLowerCase(Locale.US).contains("male") })
                    
                    val gender = when {
                        isFemale -> "(F)"
                        isMale -> "(M)"
                        else -> ""
                    }
                    val type = if (voice.isNetworkConnectionRequired) "Network" else "Local"
                    "Speaker ${index + 1} $gender - $type".replace("  ", " ").trim()
                }
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
        } catch (e: Exception) {}
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
