package com.example.ttsreader

import java.util.UUID

/** Одна глава книги — часть текста, которую можно озвучить отдельно */
data class Chapter(
    val title: String,
    val text: String
)

/** Книга, загруженная из файла (txt / pdf / epub / docx) */
data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val filePath: String,
    val format: String,
    val chapters: List<Chapter> = emptyList(),
    val localTextPath: String,           // где на устройстве хранится извлечённый текст (переживает перезапуск)
    var lastPositionSeconds: Double = 0.0
) {
    val chapterCount: Int get() = chapters.size
}
