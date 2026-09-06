package com.example.ttsreader

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ttsreader.databinding.FragmentFileBinding
import java.io.BufferedReader
import java.io.InputStreamReader

class FileTabFragment : Fragment() {

    private var _binding: FragmentFileBinding? = null
    private val binding get() = _binding!!

    private val books = mutableListOf<Book>()
    private lateinit var adapter: BookAdapter

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BookAdapter(books) { book ->
            val fullText = book.chapters.joinToString("\n\n") { it.text }
            (requireActivity() as MainActivity).speak(fullText)
        }
        binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLibrary.adapter = adapter

        updateEmptyState()

        binding.btnLoadFile.setOnClickListener {
            pickFileLauncher.launch(arrayOf("text/plain", "application/pdf", "application/epub+zip", "*/*"))
        }
    }

    private fun loadFile(uri: Uri) {
        val fileName = getFileName(uri)
        val extension = fileName.substringAfterLast('.', "").lowercase()

        if (extension != "txt") {
            Toast.makeText(
                requireContext(),
                "Формат .$extension будет поддержан в следующем обновлении. Сейчас работает только .txt",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: ""

            if (text.isBlank()) {
                Toast.makeText(requireContext(), "Файл пустой или не удалось прочитать текст", Toast.LENGTH_SHORT).show()
                return
            }

            val book = Book(
                title = fileName,
                filePath = uri.toString(),
                format = extension,
                chapters = listOf(Chapter(title = "Глава 1", text = text))
            )
            books.add(0, book)
            adapter.setBooks(books)
            updateEmptyState()
            Toast.makeText(requireContext(), "Загружено: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось прочитать файл: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "book.txt"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }

    private fun updateEmptyState() {
        binding.emptyLibraryText.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}