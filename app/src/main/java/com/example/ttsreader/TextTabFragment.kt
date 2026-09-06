package com.example.ttsreader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.ttsreader.databinding.FragmentTextBinding

/**
 * Вкладка "Текст": пользователь вставляет текст и нажимает "Старт" для озвучки.
 * Сама озвучка (TextToSpeech) будет подключена на следующем шаге —
 * пока здесь только интерфейс.
 */
class TextTabFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStart.setOnClickListener {
            val text = binding.editText.text.toString()
            if (text.isBlank()) {
                Toast.makeText(requireContext(), "Сначала вставьте текст", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: подключить TextToSpeech.speak(text, ...)
                Toast.makeText(requireContext(), "Озвучка запустится здесь", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
