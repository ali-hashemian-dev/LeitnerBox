
package com.leitnerbox.app

data class Word(
    val id: Int = 0,
    val collocation: String,
    val meaning: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val boxNumber: Int = 1,
    val nextReviewTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),

    // Lesson information
    val lessonId: Int = 0,
    val sourceKey: String? = null
)
