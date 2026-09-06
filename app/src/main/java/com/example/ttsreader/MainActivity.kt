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

    // --- Модель воспроизведения (оценка длительности приблизительная - Android TTS
    //     не даёт точную позицию по всем движкам, поэтому считаем по количеству слов) ---
    private var fullWords: List<String> = emptyList()
    private var totalDurationSeconds: Double = 0.0
    private var consumedSecondsBeforeChunk: Double = 0.0
    private var playStartTimeMillis: Long = 0L
    private var wordsPerSecondBase = 2.5 // ~150 слов/мин
    private var currentRateMultiplier = 1.0f
    private var currentBookId: String? = null
    private var lastChunkCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateTimeLabel()
            if (tts?.isSpeaking == true) {
                handler.postDelayed(this, 500)
            }
        }
    }

    /**
     * Запустить озвучку текста с начала (или с startAtSeconds, если книга уже слушалась).
     * bookId передаётся, чтобы можно было сохранить позицию для конкретной книги.
     */
    fun speak(text: String, bookId: String? = null, startAtSeconds: Double = 0.0) {
        if (text.isBlank()) {
            Toast.makeText(this, "Текст пустой", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ttsReady) {
            Toast.makeText(this, "Озвучка ещё загружается, подожди секунду", Toast.LENGTH_SHORT).show()
            return
        }
        currentBookId = bookId
        fullWords = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        totalDurationSeconds = fullWords.size / (wordsPerSecondBase * currentRateMultiplier)
        consumedSecondsBeforeChunk = startAtSeconds.coerceIn(0.0, totalDurationSeconds)

        val startWordIndex = (consumedSecondsBeforeChunk * wordsPerSecondBase * currentRateMultiplier)
            .toInt().coerceIn(0, fullWords.size)
        playFromWordIndex(startWordIndex)

        Toast.makeText(this, "Длительность: ${formatTime(totalDurationSeconds)}", Toast.LENGTH_LONG).show()
    }

    /** Разбивает длинный текст на куски (Android TTS обрывает слишком длинные строки). */
    private fun buildChunks(text: String, maxChunkChars: Int = 1500): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (w in words) {
            if (current.length + w.length + 1 > maxChunkChars && current.isNotEmpty()) {
                chunks.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(w)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }

    private fun playFromWordIndex(wordIndex: Int) {
        playStartTimeMillis = System.currentTimeMillis()
        tts?.stop()

        if (wordIndex >= fullWords.size) {
            Toast.makeText(this, "Это конец текста", Toast.LENGTH_SHORT).show()
            handler.removeCallbacks(tickRunnable)
            updateTimeLabel()
            return
        }
        val remainingText = fullWords.subList(wordIndex, fullWords.size).joinToString(" ")
        val chunks = buildChunks(remainingText)
        lastChunkCount = chunks.size

        chunks.forEachIndexed { i, chunk ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(chunk, mode, null, "utt_${playStartTimeMillis}_${i}_${chunks.size - 1}")
        }
        binding.btnPause.text = getString(R.string.btn_pause)
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun currentElapsedSeconds(): Double {
        if (fullWords.isEmpty()) return 0.0
        val chunkElapsed = (System.currentTimeMillis() - playStartTimeMillis) / 1000.0
        return (consumedSecondsBeforeChunk + chunkElapsed).coerceIn(0.0, totalDurationSeconds)
    }

    /** Перемотка на deltaSeconds (может быть отрицательным). Кнопки Назад/Вперёд -> ±15 сек. */
    private fun seek(deltaSeconds: Double) {
        if (fullWords.isEmpty()) {
            Toast.makeText(this, "Сначала запусти озвучку", Toast.LENGTH_SHORT).show()
            return
        }
        val target = (currentElapsedSeconds() + deltaSeconds).coerceIn(0.0, totalDurationSeconds)
        consumedSecondsBeforeChunk = target
        val wordIndex = (target * wordsPerSecondBase * currentRateMultiplier).toInt()
            .coerceIn(0, fullWords.size)
        playFromWordIndex(wordIndex)
        persistPositionIfNeeded()
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
            persistPositionIfNeeded()
        } else {
            val wordIndex = (consumedSecondsBeforeChunk * wordsPerSecondBase * currentRateMultiplier)
                .toInt().coerceIn(0, fullWords.size)
            if (wordIndex >= fullWords.size) {
                Toast.makeText(this, "Текст уже закончен", Toast.LENGTH_SHORT).show()
                return
            }
            playFromWordIndex(wordIndex)
        }
    }

    private fun persistPositionIfNeeded() {
        val id = currentBookId ?: return
        LibraryStore.updatePosition(this, id, consumedSecondsBeforeChunk)
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
                    val parts = utteranceId?.split("_") ?: return
                    val idx = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: return
                    val last = parts.getOrNull(parts.size - 1)?.toIntOrNull() ?: return
                    if (idx == last) {
                        runOnUiThread {
                            consumedSecondsBeforeChunk = totalDurationSeconds
                            handler.removeCallbacks(tickRunnable)
                            updateTimeLabel()
                            binding.btnPause.text = getString(R.string.btn_pause)
                            persistPositionIfNeeded()
                        }
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
        binding.btnBack.setOnClickListener { seek(-15.0) }
        binding.btnForward.setOnClickListener { seek(15.0) }

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
        persistPositionIfNeeded()
        handler.removeCallbacks(tickRunnable)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
