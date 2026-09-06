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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Вкладка "Файл": выбор файла, реальное чтение текста, библиотека (сохраняется между запусками).
 * Поддерживаются: .txt, .epub, .docx (базово), .pdf (с определением сканов без текста).
 */
class FileTabFragment : Fragment() {

    private var _binding: FragmentFileBinding? = null
    private val binding get() = _binding!!

    private val books = mutableListOf<Book>()
    private lateinit var adapter: BookAdapter
    private var pdfBoxInitialized = false

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
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

        adapter = BookAdapter(books) { book -> playBook(book) }
        binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLibrary.adapter = adapter

        // Загружаем сохранённую библиотеку (переживает перезапуск приложения)
        books.clear()
        books.addAll(LibraryStore.loadBooks(requireContext()))
        adapter.setBooks(books)
        updateEmptyState()

        binding.btnLoadFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }
    }

    private fun playBook(book: Book) {
        val text = LibraryStore.readTextFromInternalStorage(book.localTextPath)
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Не удалось прочитать сохранённый текст книги", Toast.LENGTH_SHORT).show()
            return
        }
        (requireActivity() as MainActivity).speak(text, bookId = book.id, startAtSeconds = book.lastPositionSeconds)
    }

    private fun loadFile(uri: Uri) {
        val fileName = getFileName(uri)
        val extension = fileName.substringAfterLast('.', "").lowercase()

        try {
            val chapters: List<Chapter> = when (extension) {
                "txt" -> {
                    val text = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    } ?: ""
                    if (text.isBlank()) emptyList() else listOf(Chapter("Глава 1", text))
                }
                "epub" -> parseEpub(uri)
                "docx" -> parseDocx(uri)
                "pdf" -> parsePdf(uri)
                else -> {
                    Toast.makeText(
                        requireContext(),
                        "Формат .$extension пока не поддерживается (работают .txt, .pdf, .epub, .docx)",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            if (chapters.isEmpty()) {
                Toast.makeText(requireContext(), "Не удалось найти текст в файле", Toast.LENGTH_SHORT).show()
                return
            }

            val fullText = chapters.joinToString("\n\n") { it.text }
            val newBook = Book(
                title = fileName,
                filePath = uri.toString(),
                format = extension,
                chapters = chapters,
                localTextPath = "" // временно, заполним ниже
            )
            val savedPath = LibraryStore.saveTextToInternalStorage(requireContext(), newBook.id, fullText)
            val finalBook = newBook.copy(localTextPath = savedPath)

            books.add(0, finalBook)
            LibraryStore.saveBooks(requireContext(), books)
            adapter.setBooks(books)
            updateEmptyState()
            Toast.makeText(requireContext(), "Загружено: $fileName (${chapters.size} гл.)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось прочитать файл: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Реальное извлечение текста из PDF постранично, с определением сканов без текстового слоя. */
    private fun parsePdf(uri: Uri): List<Chapter> {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(requireContext())
            pdfBoxInitialized = true
        }
        val chapters = mutableListOf<Chapter>()
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                val totalPages = document.numberOfPages
                val stripper = PDFTextStripper()
                var extractedChars = 0
                for (page in 1..totalPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(document).trim()
                    extractedChars += pageText.length
                    if (pageText.isNotBlank()) {
                        chapters.add(Chapter(title = "Страница $page", text = pageText))
                    }
                }
                if (extractedChars < totalPages * 5) {
                    // Почти нет текста на страницу в среднем -> вероятно это скан-изображение
                    Toast.makeText(
                        requireContext(),
                        "Похоже, это скан-изображение без текстового слоя — для него нужен OCR, пока не поддерживается",
                        Toast.LENGTH_LONG
                    ).show()
                    return emptyList()
                }
            }
        }
        return chapters
    }

    /** Базовое извлечение текста из DOCX: word/document.xml внутри zip, теги <w:t>. */
    private fun parseDocx(uri: Uri): List<Chapter> {
        var xml = ""
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        xml = zip.bufferedReader(Charsets.UTF_8).readText()
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        if (xml.isBlank()) return emptyList()

        val text = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .joinToString(" ") { it.groupValues[1] }
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (text.isBlank()) emptyList() else listOf(Chapter("Документ", text))
    }

    /** Базовое извлечение текста из EPUB: читаем xhtml/html файлы внутри zip, убираем теги. */
    private fun parseEpub(uri: Uri): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                val htmlEntries = mutableListOf<Pair<String, String>>()
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory && (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))) {
                        val rawHtml = zip.bufferedReader(Charsets.UTF_8).readText()
                        htmlEntries.add(entry.name to rawHtml)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                htmlEntries.sortedBy { it.first }.forEachIndexed { index, (_, html) ->
                    val plainText = stripHtml(html)
                    if (plainText.isNotBlank()) {
                        chapters.add(Chapter(title = "Глава ${index + 1}", text = plainText))
                    }
                }
            }
        }
        return chapters
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getFileName(uri: Uri): String {
        var name = "book"
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
