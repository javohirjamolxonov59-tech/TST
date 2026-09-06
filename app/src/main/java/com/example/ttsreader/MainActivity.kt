package com.example.ttsreader

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ttsreader.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var fullWords: List<String> = emptyList()
    private var totalDurationSeconds: Double = 0.0
    private var consumedSecondsBeforeChunk: Double = 0.0
    private var playStartTimeMillis: Long = 0L
    private var wordsPerSecondBase = 2.5
    private var currentRateMultiplier = 1.0f

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateTimeLabel()
            if (tts?.isSpeaking == true) {
                handler.postDelayed(this, 500)
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "Текст пустой", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ttsReady) {
            Toast.makeText(this, "Озвучка ещё загружается, подожди секунду", Toast.LENGTH_SHORT).show()
            return
        }
        fullWords = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        totalDurationSeconds = fullWords.size / (wordsPerSecondBase * currentRateMultiplier)
        consumedSecondsBeforeChunk = 0.0
        playStartTimeMillis = System.currentTimeMillis()

        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_${playStartTimeMillis}")
        binding.btnPause.text = getString(R.string.btn_pause)
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)

        Toast.makeText(this, "Длительность: ${formatTime(totalDurationSeconds)}", Toast.LENGTH_LONG).show()
    }

    private fun currentElapsedSeconds(): Double {
        if (fullWords.isEmpty()) return 0.0
        val chunkElapsed = (System.currentTimeMillis() - playStartTimeMillis) / 1000.0
        return (consumedSecondsBeforeChunk + chunkElapsed).coerceIn(0.0, totalDurationSeconds)
    }

    private fun seek(deltaSeconds: Double) {
        if (fullWords.isEmpty()) {
            Toast.makeText(this, "Сначала запусти озвучку", Toast.LENGTH_SHORT).show()
            return
        }
        val target = (currentElapsedSeconds() + deltaSeconds).coerceIn(0.0, totalDurationSeconds)
        val wordIndex = (target * wordsPerSecondBase * currentRateMultiplier).toInt()
            .coerceIn(0, fullWords.size)

        consumedSecondsBeforeChunk = target
        playStartTimeMillis = System.currentTimeMillis()
        tts?.stop()

        if (wordIndex >= fullWords.size) {
            Toast.makeText(this, "Это конец текста", Toast.LENGTH_SHORT).show()
            handler.removeCallbacks(tickRunnable)
            updateTimeLabel()
            return
        }
        val remaining = fullWords.subList(wordIndex, fullWords.size).joinToString(" ")
        tts?.speak(remaining, TextToSpeech.QUEUE_FLUSH, null, "utt_seek_${playStartTimeMillis}")
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun togglePause() {
        if (fullWords.isEmpty()) {
            Toast.makeText(this, "Нечего ставить на паузу", Toast.LENGTH_SHORT).show()
            return
        }
        if (tts?.isSpeaking == true) {
            consumedSecondsBeforeChunk = currentElapsedSeconds()
            tts?.stop()
            handler.removeCallbacks(tickRunnable)
            binding.btnPause.text = "▶"
        } else {
            val wordIndex = (consumedSecondsBeforeChunk * wordsPerSecondBase * currentRateMultiplier)
                .toInt().coerceIn(0, fullWords.size)
            if (wordIndex >= fullWords.size) {
                Toast.makeText(this, "Текст уже закончен", Toast.LENGTH_SHORT).show()
                return
            }
            val remaining = fullWords.subList(wordIndex, fullWords.size).joinToString(" ")
            playStartTimeMillis = System.currentTimeMillis()
            tts?.speak(remaining, TextToSpeech.QUEUE_FLUSH, null, "utt_resume_${playStartTimeMillis}")
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
            binding.btnPause.text = getString(R.string.btn_pause)
        }
    }

    private fun updateTimeLabel() {
        binding.playbackTimeText.text =
            "${formatTime(currentElapsedSeconds())} / ${formatTime(totalDurationSeconds)}"
    }

    private fun formatTime(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            val result = tts?.setLanguage(Locale("ru"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        consumedSecondsBeforeChunk = totalDurationSeconds
                        handler.removeCallbacks(tickRunnable)
                        updateTimeLabel()
                        binding.btnPause.text = getString(R.string.btn_pause)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        } else {
            Toast.makeText(this, "Не удалось запустить TTS-движок устройства", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        binding.viewPager.adapter = TabsPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_text) else getString(R.string.tab_file)
        }.attach()

        binding.btnPause.setOnClickListener { togglePause() }
        binding.btnBack.setOnClickListener { seek(-10.0) }
        binding.btnForward.setOnClickListener { seek(10.0) }

        binding.seekTone.progress = 50
        binding.seekTone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress / 100f) * 1.0f
                tts?.setPitch(pitch)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekSpeed.progress = 50
        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress / 100f) * 1.0f
                currentRateMultiplier = rate
                tts?.setSpeechRate(rate)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updateTimeLabel()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}