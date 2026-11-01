
package com.leitnerbox.app

import android.content.Context
import org.json.JSONObject

data class LessonItem(
    val sourceKey: String,
    val collocation: String,
    val meaning: String,
    val example: String,
    val exampleTranslation: String
)

data class Lesson(
    val lessonId: Int,
    val title: String,
    val words: List<LessonItem>
)

object LessonRepository {

    private var cachedLessons: List<Lesson>? = null

    @Synchronized
    fun getLessons(context: Context): List<Lesson> {
        cachedLessons?.let { return it }

        val jsonText = context.assets
            .open("lessons/lessons.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val root = JSONObject(jsonText)
        val lessonsArray = root.getJSONArray("lessons")

        val result = mutableListOf<Lesson>()

        for (i in 0 until lessonsArray.length()) {
            val lessonObject = lessonsArray.getJSONObject(i)
            val wordsArray = lessonObject.getJSONArray("words")
            val words = mutableListOf<LessonItem>()

            for (j in 0 until wordsArray.length()) {
                val wordObject = wordsArray.getJSONObject(j)

                words.add(
                    LessonItem(
                        sourceKey = wordObject.getString("sourceKey"),
                        collocation = wordObject.getString("collocation"),
                        meaning = wordObject.optString("meaning", ""),
                        example = wordObject.optString("example", ""),
                        exampleTranslation = wordObject.optString(
                            "exampleTranslation",
                            ""
                        )
                    )
                )
            }

            result.add(
                Lesson(
                    lessonId = lessonObject.getInt("lessonId"),
                    title = lessonObject.getString("title"),
                    words = words
                )
            )
        }

        val loadedLessons = result.sortedBy { it.lessonId }

        require(loadedLessons.size == root.getInt("lessonCount")) {
            "Lesson count does not match lessons.json."
        }

        require(
            loadedLessons.sumOf { it.words.size } == root.getInt("wordCount")
        ) {
            "Collocation count does not match lessons.json."
        }

        cachedLessons = loadedLessons
        return loadedLessons
    }

    fun clearCache() {
        cachedLessons = null
    }
}
