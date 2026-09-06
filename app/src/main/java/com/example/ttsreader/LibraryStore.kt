package com.example.ttsreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Простое хранилище библиотеки книг в SharedPreferences (JSON).
 * Сам текст книги хранится отдельным файлом во внутренней памяти приложения (localTextPath),
 * а не как content:// ссылка — так книга остаётся доступна и после перезапуска телефона.
 */
object LibraryStore {

    private const val PREFS = "tts_reader_library"
    private const val KEY_BOOKS = "books_json"

    fun saveBooks(context: Context, books: List<Book>) {
        val array = JSONArray()
        books.forEach { book ->
            val obj = JSONObject()
            obj.put("id", book.id)
            obj.put("title", book.title)
            obj.put("filePath", book.filePath)
            obj.put("format", book.format)
            obj.put("localTextPath", book.localTextPath)
            obj.put("lastPositionSeconds", book.lastPositionSeconds)
            obj.put("chapterCount", book.chapterCount)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKS, array.toString())
            .apply()
    }

    fun loadBooks(context: Context): MutableList<Book> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BOOKS, null)
            ?: return mutableListOf()
        val result = mutableListOf<Book>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val localPath = obj.getString("localTextPath")
                val file = File(localPath)
                if (!file.exists()) continue // текст был удалён - пропускаем битую запись

                val chapterCount = obj.optInt("chapterCount", 1)
                // Полный текст читаем по требованию (при воспроизведении), тут только метаданные + один "виртуальный" список глав для счётчика
                val placeholderChapters = List(chapterCount) { Chapter("Глава ${it + 1}", "") }

                result.add(
                    Book(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        filePath = obj.getString("filePath"),
                        format = obj.getString("format"),
                        chapters = placeholderChapters,
                        localTextPath = localPath,
                        lastPositionSeconds = obj.optDouble("lastPositionSeconds", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            return mutableListOf()
        }
        return result
    }

    fun updatePosition(context: Context, bookId: String, seconds: Double) {
        val books = loadBooks(context)
        val updated = books.map { if (it.id == bookId) it.copy(lastPositionSeconds = seconds) else it }
        saveBooks(context, updated)
    }

    /** Сохраняет извлечённый текст книги во внутреннюю память и возвращает путь к файлу. */
    fun saveTextToInternalStorage(context: Context, bookId: String, fullText: String): String {
        val dir = File(context.filesDir, "books")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$bookId.txt")
        file.writeText(fullText, Charsets.UTF_8)
        return file.absolutePath
    }

    fun readTextFromInternalStorage(localTextPath: String): String {
        val file = File(localTextPath)
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }
}
