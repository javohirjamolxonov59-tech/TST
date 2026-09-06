package com.example.ttsreader

/** Одна глава книги — часть текста, которую можно озвучить отдельно */
data class Chapter(
    val title: String,
    val text: String
)

/** Книга, загруженная из файла (txt / pdf / epub / fb2 и т.д.) */
data class Book(
    val title: String,
    val filePath: String,
    val format: String,       // "txt", "pdf", "epub" ...
    val chapters: List<Chapter> = emptyList()
) {
    val chapterCount: Int get() = chapters.size
}
