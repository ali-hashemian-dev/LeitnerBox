
package com.leitnerbox.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WordDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "leitnerbox.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_WORDS = "words"

        private const val COL_ID = "id"
        private const val COL_COLLOCATION = "collocation"
        private const val COL_MEANING = "meaning"
        private const val COL_EXAMPLE = "example"
        private const val COL_EXAMPLE_TRANSLATION = "example_translation"
        private const val COL_BOX_NUMBER = "box_number"
        private const val COL_NEXT_REVIEW_TIME = "next_review_time"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_LESSON_ID = "lesson_id"
        private const val COL_SOURCE_KEY = "source_key"

        private const val SOURCE_KEY_INDEX = "index_words_source_key"
    }

    override fun onCreate(db: SQLiteDatabase) {
        createWordsTable(db, TABLE_WORDS)
        createSourceKeyIndex(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        createSourceKeyIndex(db)
    }

    private fun createWordsTable(
        db: SQLiteDatabase,
        tableName: String
    ) {
        db.execSQL(
            """
            CREATE TABLE $tableName (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_COLLOCATION TEXT NOT NULL,
                $COL_MEANING TEXT NOT NULL DEFAULT '',
                $COL_EXAMPLE TEXT NOT NULL DEFAULT '',
                $COL_EXAMPLE_TRANSLATION TEXT NOT NULL DEFAULT '',
                $COL_BOX_NUMBER INTEGER NOT NULL DEFAULT 1,
                $COL_NEXT_REVIEW_TIME INTEGER NOT NULL DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_LESSON_ID INTEGER NOT NULL DEFAULT 0,
                $COL_SOURCE_KEY TEXT
            )
            """.trimIndent()
        )
    }

    private fun createSourceKeyIndex(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS $SOURCE_KEY_INDEX
            ON $TABLE_WORDS ($COL_SOURCE_KEY)
            WHERE $COL_SOURCE_KEY IS NOT NULL
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.beginTransaction()

            try {
                createWordsTable(db, "words_new")

                db.execSQL(
                    """
                    INSERT INTO words_new (
                        id,
                        collocation,
                        meaning,
                        example,
                        box_number,
                        next_review_time,
                        created_at
                    )
                    SELECT
                        id,
                        COALESCE(collocation, ''),
                        COALESCE(meaning, ''),
                        COALESCE(example, ''),
                        COALESCE(box_number, 1),
                        COALESCE(next_review_time, 0),
                        COALESCE(created_at, 0)
                    FROM words
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE words")
                db.execSQL("ALTER TABLE words_new RENAME TO words")

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } else if (oldVersion < 3) {
            db.execSQL(
                """
                ALTER TABLE $TABLE_WORDS
                ADD COLUMN $COL_LESSON_ID INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE $TABLE_WORDS
                ADD COLUMN $COL_SOURCE_KEY TEXT
                """.trimIndent()
            )
        }

        createSourceKeyIndex(db)
    }

    fun addWord(word: Word): Long {
        val values = ContentValues().apply {
            put(COL_COLLOCATION, word.collocation.trim())
            put(COL_MEANING, word.meaning.trim())
            put(COL_EXAMPLE, word.example.trim())
            put(COL_EXAMPLE_TRANSLATION, word.exampleTranslation.trim())
            put(COL_BOX_NUMBER, word.boxNumber)
            put(COL_NEXT_REVIEW_TIME, word.nextReviewTime)
            put(COL_CREATED_AT, word.createdAt)
            put(COL_LESSON_ID, word.lessonId)

            if (word.sourceKey == null) {
                putNull(COL_SOURCE_KEY)
            } else {
                put(COL_SOURCE_KEY, word.sourceKey)
            }
        }

        return writableDatabase.insert(TABLE_WORDS, null, values)
    }

    fun addLessonWordIfAbsent(word: Word): Long {
        require(word.lessonId in 6..60) {
            "Lesson ID must be between 6 and 60."
        }

        require(!word.sourceKey.isNullOrBlank()) {
            "Lesson words must have a source key."
        }

        val values = ContentValues().apply {
            put(COL_COLLOCATION, word.collocation.trim())
            put(COL_MEANING, word.meaning.trim())
            put(COL_EXAMPLE, word.example.trim())
            put(COL_EXAMPLE_TRANSLATION, word.exampleTranslation.trim())
            put(COL_BOX_NUMBER, word.boxNumber)
            put(COL_NEXT_REVIEW_TIME, word.nextReviewTime)
            put(COL_CREATED_AT, word.createdAt)
            put(COL_LESSON_ID, word.lessonId)
            put(COL_SOURCE_KEY, word.sourceKey)
        }

        return writableDatabase.insertWithOnConflict(
            TABLE_WORDS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getAllWords(): List<Word> {
        val words = mutableListOf<Word>()

        readableDatabase.query(
            TABLE_WORDS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                words.add(cursorToWord(cursor))
            }
        }

        return words
    }

    fun getWordsForLesson(lessonId: Int): List<Word> {
        val words = mutableListOf<Word>()

        readableDatabase.query(
            TABLE_WORDS,
            null,
            "$COL_LESSON_ID = ?",
            arrayOf(lessonId.toString()),
            null,
            null,
            "$COL_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                words.add(cursorToWord(cursor))
            }
        }

        return words
    }

    fun isSourceKeyImported(sourceKey: String): Boolean {
        readableDatabase.query(
            TABLE_WORDS,
            arrayOf(COL_ID),
            "$COL_SOURCE_KEY = ?",
            arrayOf(sourceKey),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun deleteWord(id: Int): Int {
        return writableDatabase.delete(
            TABLE_WORDS,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun updateWord(word: Word): Int {
        val values = ContentValues().apply {
            put(COL_COLLOCATION, word.collocation.trim())
            put(COL_MEANING, word.meaning.trim())
            put(COL_EXAMPLE, word.example.trim())
            put(COL_EXAMPLE_TRANSLATION, word.exampleTranslation.trim())
            put(COL_BOX_NUMBER, word.boxNumber)
            put(COL_NEXT_REVIEW_TIME, word.nextReviewTime)
            put(COL_CREATED_AT, word.createdAt)
            put(COL_LESSON_ID, word.lessonId)

            if (word.sourceKey == null) {
                putNull(COL_SOURCE_KEY)
            } else {
                put(COL_SOURCE_KEY, word.sourceKey)
            }
        }

        return writableDatabase.update(
            TABLE_WORDS,
            values,
            "$COL_ID = ?",
            arrayOf(word.id.toString())
        )
    }

    fun searchWords(query: String): List<Word> {
        val words = mutableListOf<Word>()
        val searchQuery = "%${query.trim()}%"

        readableDatabase.query(
            TABLE_WORDS,
            null,
            """
            $COL_COLLOCATION LIKE ?
            OR $COL_MEANING LIKE ?
            OR $COL_EXAMPLE LIKE ?
            OR $COL_EXAMPLE_TRANSLATION LIKE ?
            """.trimIndent(),
            arrayOf(
                searchQuery,
                searchQuery,
                searchQuery,
                searchQuery
            ),
            null,
            null,
            "$COL_CREATED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                words.add(cursorToWord(cursor))
            }
        }

        return words
    }

    fun getWordCount(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_WORDS",
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun getLessonWordCount(lessonId: Int): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_WORDS WHERE $COL_LESSON_ID = ?",
            arrayOf(lessonId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun cursorToWord(
        cursor: android.database.Cursor
    ): Word {
        return Word(
            id = cursor.getInt(
                cursor.getColumnIndexOrThrow(COL_ID)
            ),
            collocation = cursor.getString(
                cursor.getColumnIndexOrThrow(COL_COLLOCATION)
            ),
            meaning = cursor.getString(
                cursor.getColumnIndexOrThrow(COL_MEANING)
            ),
            example = cursor.getString(
                cursor.getColumnIndexOrThrow(COL_EXAMPLE)
            ),
            exampleTranslation = cursor.getString(
                cursor.getColumnIndexOrThrow(COL_EXAMPLE_TRANSLATION)
            ),
            boxNumber = cursor.getInt(
                cursor.getColumnIndexOrThrow(COL_BOX_NUMBER)
            ),
            nextReviewTime = cursor.getLong(
                cursor.getColumnIndexOrThrow(COL_NEXT_REVIEW_TIME)
            ),
            createdAt = cursor.getLong(
                cursor.getColumnIndexOrThrow(COL_CREATED_AT)
            ),
            lessonId = cursor.getInt(
                cursor.getColumnIndexOrThrow(COL_LESSON_ID)
            ),
            sourceKey = cursor.getString(
                cursor.getColumnIndexOrThrow(COL_SOURCE_KEY)
            )
        )
    }
}
