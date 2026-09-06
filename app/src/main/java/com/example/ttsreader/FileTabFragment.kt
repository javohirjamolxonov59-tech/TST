package com.example.ttsreader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ttsreader.databinding.FragmentFileBinding

/**
 * Вкладка "Файл": библиотека загруженных книг (txt/pdf/epub).
 * Открытие/парсинг файлов и экран "Содержание" (главы) подключим отдельным шагом.
 */
class FileTabFragment : Fragment() {

    private var _binding: FragmentFileBinding? = null
    private val binding get() = _binding!!

    private val books = mutableListOf<Book>() // пока пусто — библиотека наполняется при загрузке файлов
    private lateinit var adapter: BookAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BookAdapter(books) { book ->
            // TODO: открыть книгу -> экран "Содержание" со списком глав
            Toast.makeText(requireContext(), "Открыть «${book.title}» → Содержание", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLibrary.adapter = adapter

        updateEmptyState()

        binding.btnLoadFile.setOnClickListener {
            // TODO: открыть системный файловый диалог (txt/pdf/epub), распарсить и добавить в books
            Toast.makeText(requireContext(), "Здесь откроется выбор файла", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        binding.emptyLibraryText.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
