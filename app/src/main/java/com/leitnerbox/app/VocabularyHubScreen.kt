
package com.leitnerbox.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search

private val HubCard = Color(0xFF101010)
private val HubGreen = Color(0xFFB7FF00)
private val HubText = Color(0xFFF2F5F2)
private val HubMuted = Color(0xFF929892)
private val HubBorder = Color(0xFF252B27)
private val lessonTitles = mapOf(
    6 to "Intensifying adverbs",
    7 to "Everyday verbs 1",
    8 to "Everyday verbs 2",
    9 to "Everyday verbs 3",
    10 to "Synonyms and confusable words 1",
    11 to "Synonyms and confusable words 2",
    12 to "Metaphor",
    13 to "Weather",
    14 to "Travel",
    15 to "Countryside",
    16 to "Towns and cities",
    17 to "People: character and behaviour",
    18 to "People: physical appearance",
    19 to "Families",
    20 to "Relationships",
    21 to "Houses, flats and rooms",
    22 to "Eating and drinking",
    23 to "Films and books",
    24 to "Music",
    25 to "Sport",
    26 to "Health and illness",
    27 to "Using the Internet",
    28 to "Study and learning",
    29 to "Presentations",
    30 to "Work",
    31 to "Business",
    32 to "Academic writing 1",
    33 to "Academic writing 2",
    34 to "Laws and punishments",
    35 to "Crime",
    36 to "News",
    37 to "Money",
    38 to "War and peace",
    39 to "Global problem",
    40 to "Time",
    41 to "Sound",
    42 to "Distance and size",
    43 to "Colour and light",
    44 to "Texture",
    45 to "Taste and smell",
    46 to "Number and frequency",
    47 to "Movement and speed",
    48 to "Change",
    49 to "Ways of speaking",
    50 to "Ways of walking",
    51 to "Starting and finishing",
    52 to "Talking about success and failure",
    53 to "Talking about cause and effect",
    54 to "Remembering and sensing",
    55 to "Agreeing and disagreeing",
    56 to "Talking about beliefs and opinions",
    57 to "Deciding and choosing",
    58 to "Claiming and denying",
    59 to "Liking and disliking",
    60 to "Praising and criticising"
)

private enum class VocabularySection {
    COURSE, MY_LESSON, SAVED
}

@Composable
fun VocabularyHubScreen(
    words: List<Word>,
    database: WordDatabase,
    onAddWord: (String, String, String, String) -> Boolean,
    onDeleteWord: (Int) -> Unit,
    onUpdateWord: (Word) -> Boolean,
    onImportComplete: () -> Unit
) {
    var section by remember {
        mutableStateOf(VocabularySection.COURSE)
    }

    var selectedLessonId by remember {
        mutableIntStateOf(0)
    }

    var globalSearch by remember {
        mutableStateOf("")
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val allLessons = remember(context) {
        LessonRepository.getLessons(context)
    }

    val globalSearchResults = remember(allLessons, globalSearch) {
        if (globalSearch.isBlank()) {
            emptyList()
        } else {
            allLessons.flatMap { lesson ->
                lesson.words
                    .filter { item ->
                        item.collocation.contains(
                            globalSearch.trim(),
                            ignoreCase = true
                        ) || item.meaning.contains(
                            globalSearch.trim(),
                            ignoreCase = true
                        )
                    }
                    .map { item ->
                        lesson.lessonId to item
                    }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Vocabulary",
            color = HubText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        if (
            section == VocabularySection.COURSE &&
            selectedLessonId != 0
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    selectedLessonId = 0
                    globalSearch = ""
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = HubGreen
                ),
                border = BorderStroke(
                    1.dp,
                    HubGreen.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to lessons",
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Lessons",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Show the main tabs only on the Vocabulary hub.
        if (selectedLessonId == 0) {
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HubTab(
                    title = "Lessons",
                    selected = section == VocabularySection.COURSE,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        section = VocabularySection.COURSE
                    }
                )

                HubTab(
                    title = "My Lesson",
                    selected = section == VocabularySection.MY_LESSON,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        section = VocabularySection.MY_LESSON
                    }
                )

                HubTab(
                    title = "Saved",
                    selected = section == VocabularySection.SAVED,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        section = VocabularySection.SAVED
                    }
                )
            }
        }

        // Place global search below the tabs, only on the Lessons screen.
        if (
            section == VocabularySection.COURSE &&
            selectedLessonId == 0
        ) {
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = globalSearch,
                onValueChange = { globalSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Search all lessons...")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search lessons",
                        tint = HubMuted
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = hubTextFieldColors()
            )
        }

        Spacer(Modifier.height(16.dp))

        when (section) {
            VocabularySection.COURSE -> {
                if (selectedLessonId != 0) {
                    CourseLessonsContent(
                        words = words,
                        database = database,
                        onImportComplete = onImportComplete,
                        selectedLessonId = selectedLessonId,
                        onSelectedLessonChange = {
                            selectedLessonId = it
                        }
                    )
                } else if (globalSearch.isBlank()) {
                    CourseLessonsContent(
                        words = words,
                        database = database,
                        onImportComplete = onImportComplete,
                        selectedLessonId = selectedLessonId,
                        onSelectedLessonChange = {
                            selectedLessonId = it
                        }
                    )
                } else {
                    if (globalSearchResults.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No matching collocations found",
                                color = HubText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "Try another word or phrase.",
                                color = HubMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Text(
                            text = "${globalSearchResults.size} results found",
                            color = HubGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = globalSearchResults,
                                key = {
                                    "${it.first}_${it.second.sourceKey}"
                                }
                            ) { result ->
                                val lessonId = result.first
                                val item = result.second

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = HubCard
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        HubBorder
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Text(
                                            text = item.collocation,
                                            color = HubGreen,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (item.meaning.isNotBlank()) {
                                            Spacer(Modifier.height(5.dp))

                                            Text(
                                                text = item.meaning,
                                                color = HubText,
                                                fontSize = 13.sp
                                            )
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        Text(
                                            text = "Lesson $lessonId",
                                            color = HubMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VocabularySection.MY_LESSON -> {
                MyLessonContent(
                    words = words.filter {
                        it.lessonId == 0
                    },
                    onAddWord = onAddWord,
                    onDeleteWord = onDeleteWord,
                    onUpdateWord = onUpdateWord
                )
            }

            VocabularySection.SAVED -> {
                SavedVocabularyContent(
                    words = words,
                    onDeleteWord = onDeleteWord,
                    onUpdateWord = onUpdateWord
                )
            }
        }
    }
}

@Composable
private fun HubTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) HubGreen else HubCard
        ),
        border = if (selected) null else BorderStroke(1.dp, HubBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (selected) Color.Black else HubMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
@Composable
fun CourseLessonsContent(
    words: List<Word>,
    database: WordDatabase,
    onImportComplete: () -> Unit,
    selectedLessonId: Int,
    onSelectedLessonChange: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lessons = remember(context) {
        LessonRepository.getLessons(context)
    }

    var search by remember { mutableStateOf("") }
    val selectedKeys = remember { mutableStateListOf<String>() }
    var message by remember { mutableStateOf("") }

    val selectedLesson = lessons.firstOrNull {
        it.lessonId == selectedLessonId
    }

    if (selectedLesson == null) {
        val totalCourseWords = lessons.sumOf { it.words.size }

        val importedCourseWords = words.count { word ->
            word.lessonId != 0 &&
                    lessons.any { lesson -> lesson.lessonId == word.lessonId }
        }.coerceAtMost(totalCourseWords)

        val remainingCourseWords =
            (totalCourseWords - importedCourseWords).coerceAtLeast(0)

        val courseProgress = if (totalCourseWords > 0) {
            importedCourseWords.toFloat() / totalCourseWords.toFloat()
        } else {
            0f
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Your Lessons",
                    color = HubText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

            }

            items(lessons, key = { it.lessonId }) { lesson ->
                val importedCount = words.count {
                    it.lessonId == lesson.lessonId
                }.coerceAtMost(lesson.words.size)

                val total = lesson.words.size
                val remaining = (total - importedCount).coerceAtLeast(0)

                val progress = if (total > 0) {
                    importedCount.toFloat() / total.toFloat()
                } else {
                    0f
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectedLessonChange(lesson.lessonId)
                            search = ""
                            selectedKeys.clear()
                            message = ""
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = HubCard
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HubBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lesson ${lesson.lessonId}",
                                    color = HubText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    text = lessonTitles[lesson.lessonId] ?: "",
                                    color = HubGreen,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = "$importedCount / $total added",
                                    color = HubMuted,
                                    fontSize = 13.sp
                                )
                            }

                            Text(
                                text = if (remaining == 0) {
                                    "Completed"
                                } else {
                                    "${(progress * 100).toInt()}%"
                                },
                                color = HubGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        HubProgressBar(progress = progress)

                        Spacer(Modifier.height(7.dp))

                        Text(
                            text = if (remaining == 0) {
                                "All collocations added"
                            } else {
                                "$remaining collocations remaining"
                            },
                            color = HubMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    } else {
        val importedKeys = remember(words, selectedLessonId) {
            words.filter { it.lessonId == selectedLessonId }
                .mapNotNull { it.sourceKey }
                .toSet()
        }

        val availableItems = selectedLesson.words.filter { item ->
            item.sourceKey !in importedKeys &&
                    (
                            search.isBlank() ||
                                    item.collocation.contains(search, ignoreCase = true) ||
                                    item.meaning.contains(search, ignoreCase = true)
                            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Lesson ${selectedLesson.lessonId}",
                color = HubText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${selectedLesson.words.size - importedKeys.size} collocations remaining",
                color = HubMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(10.dp))


            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Search collocations...")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = HubMuted
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = hubTextFieldColors()
            )


            Spacer(Modifier.height(8.dp))

            Text(
                text = "Select up to 10 collocations per batch.",
                color = HubMuted,
                fontSize = 12.sp
            )

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = HubGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableItems, key = { it.sourceKey }) { item ->
                    val checked = item.sourceKey in selectedKeys

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) {
                                    selectedKeys.remove(item.sourceKey)
                                } else if (selectedKeys.size < 10) {
                                    selectedKeys.add(item.sourceKey)
                                    message = ""
                                } else {
                                    message = "You can select up to 10 at a time."
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) {
                                Color(0xFF18200A)
                            } else {
                                HubCard
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (checked) HubGreen else HubBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.collocation,
                                    color = HubText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )

                                if (item.meaning.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.meaning,
                                        color = HubMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Text(
                                text = if (checked) "✓" else "+",
                                color = HubGreen,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    var added = 0

                    selectedLesson.words
                        .filter { it.sourceKey in selectedKeys }
                        .forEach { item ->
                            val result = database.addLessonWordIfAbsent(
                                Word(
                                    collocation = item.collocation,
                                    meaning = item.meaning,
                                    example = item.example,
                                    exampleTranslation = item.exampleTranslation,
                                    lessonId = selectedLesson.lessonId,
                                    sourceKey = item.sourceKey
                                )
                            )

                            if (result != -1L) added++
                        }

                    selectedKeys.clear()

                    message = if (added > 0) {
                        "$added collocations added to Saved Vocabulary."
                    } else {
                        "No new collocations were added."
                    }

                    onImportComplete()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedKeys.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF30352B),
                    disabledContentColor = HubMuted
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Add Selected (${selectedKeys.size}/10)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CourseProgressCard(
    imported: Int,
    total: Int,
    remaining: Int,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141A0C)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF35451B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Overall Course Progress",
                color = HubText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$imported / $total",
                    color = HubGreen,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = HubGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            HubProgressBar(progress = progress)

            Spacer(Modifier.height(8.dp))

            Text(
                text = "$remaining collocations remaining across the course",
                color = HubMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HubProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .then(
                Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = HubBorder
                )
            ) {}

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.CenterStart)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp),
                    shape = RoundedCornerShape(50),
                    colors = CardDefaults.cardColors(
                        containerColor = HubGreen
                    )
                ) {}
            }
        }
    }
}

@Composable
private fun MyLessonContent(
    words: List<Word>,
    onAddWord: (String, String, String, String) -> Boolean,
    onDeleteWord: (Int) -> Unit,
    onUpdateWord: (Word) -> Boolean
) {
    var collocation by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "My Lesson",
                color = HubGreen,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Add and manage your own collocations.",
                color = HubMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            HubInput("Collocation *", collocation) { collocation = it }
            HubInput("Meaning", meaning) { meaning = it }
            HubInput("Example sentence", example) { example = it }
            HubInput("Example translation", translation) { translation = it }

            Button(
                onClick = {
                    val success = onAddWord(
                        collocation.trim(),
                        meaning.trim(),
                        example.trim(),
                        translation.trim()
                    )

                    message = if (success) {
                        collocation = ""
                        meaning = ""
                        example = ""
                        translation = ""
                        "Collocation saved."
                    } else {
                        "Could not save. Check whether this collocation already exists."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = collocation.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("Save Collocation", fontWeight = FontWeight.Bold)
            }

            if (message.isNotBlank()) {
                Text(message, color = HubGreen, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "My Collocations (${words.size})",
                color = HubText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(words, key = { it.id }) { word ->
            HubSavedWordCard(
                word = word,
                onDelete = { onDeleteWord(word.id) },
                onUpdate = onUpdateWord
            )
        }
    }
}

@Composable
private fun SavedVocabularyContent(
    words: List<Word>,
    onDeleteWord: (Int) -> Unit,
    onUpdateWord: (Word) -> Boolean
) {
    var search by remember { mutableStateOf("") }
    var selectedLessonId by remember { mutableIntStateOf(-1) }
    var selectedBox by remember { mutableIntStateOf(0) }

    var lessonMenuExpanded by remember { mutableStateOf(false) }
    var boxMenuExpanded by remember { mutableStateOf(false) }

    val lessonOptions = listOf(-1, 0) + (6..60).toList()

    val selectedLessonLabel = when (selectedLessonId) {
        -1 -> "All Lessons"
        0 -> "My Lesson"
        else -> "Lesson $selectedLessonId"
    }

    val selectedBoxLabel = if (selectedBox == 0) {
        "All Boxes"
    } else {
        "Box $selectedBox"
    }

    val filtered = words.filter { word ->
        val query = search.trim()

        val matchesSearch = query.isBlank() ||
                word.collocation.contains(query, ignoreCase = true) ||
                word.meaning.contains(query, ignoreCase = true) ||
                word.example.contains(query, ignoreCase = true) ||
                word.exampleTranslation.contains(query, ignoreCase = true)

        val matchesLesson = selectedLessonId == -1 ||
                word.lessonId == selectedLessonId

        val matchesBox = selectedBox == 0 ||
                word.boxNumber == selectedBox

        matchesSearch && matchesLesson && matchesBox
    }

    val hasActiveFilters = search.isNotBlank() ||
            selectedLessonId != -1 ||
            selectedBox != 0

    Column(modifier = Modifier.fillMaxSize()) {

        Text(
            text = "Saved Vocabulary (${words.size})",
            color = HubGreen,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))


        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search saved vocabulary") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search saved vocabulary",
                    tint = HubMuted
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = hubTextFieldColors()
        )


        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Lesson filter
            Box(modifier = Modifier.weight(1f)) {

                OutlinedButton(
                    onClick = {
                        lessonMenuExpanded = true
                        boxMenuExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 10.dp
                    )
                ) {
                    Text(
                        text = "▤  $selectedLessonLabel  ▾",
                        color = HubGreen,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                DropdownMenu(
                    expanded = lessonMenuExpanded,
                    onDismissRequest = {
                        lessonMenuExpanded = false
                    },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    lessonOptions.forEach { lessonId ->
                        val label = when (lessonId) {
                            -1 -> "All Lessons"
                            0 -> "My Lesson"
                            else -> "Lesson $lessonId"
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (selectedLessonId == lessonId) {
                                        HubGreen
                                    } else {
                                        HubText
                                    }
                                )
                            },
                            onClick = {
                                selectedLessonId = lessonId
                                lessonMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Box filter
            Box(modifier = Modifier.weight(1f)) {

                OutlinedButton(
                    onClick = {
                        boxMenuExpanded = true
                        lessonMenuExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 10.dp
                    )
                ) {
                    Text(
                        text = "▦  $selectedBoxLabel  ▾",
                        color = HubGreen,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                DropdownMenu(
                    expanded = boxMenuExpanded,
                    onDismissRequest = {
                        boxMenuExpanded = false
                    }
                ) {
                    (0..6).forEach { boxNumber ->
                        val label = if (boxNumber == 0) {
                            "All Boxes"
                        } else {
                            "Box $boxNumber"
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (selectedBox == boxNumber) {
                                        HubGreen
                                    } else {
                                        HubText
                                    }
                                )
                            },
                            onClick = {
                                selectedBox = boxNumber
                                boxMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing ${filtered.size} of ${words.size} collocations",
                color = HubMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            if (hasActiveFilters) {
                TextButton(
                    onClick = {
                        search = ""
                        selectedLessonId = -1
                        selectedBox = 0
                        lessonMenuExpanded = false
                        boxMenuExpanded = false
                    }
                ) {
                    Text(
                        text = "Clear Filters",
                        color = HubGreen,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = HubCard
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HubBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No collocations found",
                                color = HubText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "Try changing your search or filters.",
                                color = HubMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { word ->
                    HubSavedWordCard(
                        word = word,
                        onDelete = { onDeleteWord(word.id) },
                        onUpdate = onUpdateWord
                    )
                }
            }
        }
    }
}
@Composable
private fun HubInput(
    label: String,
    value: String,
    readOnly: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = label != "Example sentence" &&
                label != "Example translation",
        shape = RoundedCornerShape(12.dp),
        colors = hubTextFieldColors()
    )
}


@Composable
private fun hubTextFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = HubText,
        unfocusedTextColor = HubText,
        focusedBorderColor = HubGreen,
        unfocusedBorderColor = HubBorder,
        focusedLabelColor = HubGreen,
        unfocusedLabelColor = HubMuted,
        cursorColor = HubGreen
    )

@Composable
private fun HubSavedWordCard(
    word: Word,
    onDelete: () -> Unit,
    onUpdate: (Word) -> Boolean
) {
    var editing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HubCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HubBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = word.collocation,
                color = HubText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            if (word.meaning.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(word.meaning, color = HubMuted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (word.lessonId == 0) {
                        "My Lesson"
                    } else {
                        "Lesson ${word.lessonId}"
                    },
                    color = HubGreen,
                    fontSize = 11.sp
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "Box ${word.boxNumber}",
                    color = HubMuted,
                    fontSize = 12.sp
                )
            }

            if (word.example.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(word.example, color = HubText, fontSize = 12.sp)
            }

            if (word.exampleTranslation.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    word.exampleTranslation,
                    color = HubMuted,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { editing = true }) {
                    Text("Edit", color = HubGreen)
                }

                TextButton(onClick = onDelete) {
                    Text("Delete", color = Color(0xFFFF7777))
                }
            }
        }
    }

    if (editing) {
        HubEditWordDialog(
            word = word,
            onDismiss = { editing = false },
            onSave = { updated ->
                val saved = onUpdate(updated)
                if (saved) editing = false
                saved
            }
        )
    }
}

@Composable
private fun HubEditWordDialog(
    word: Word,
    onDismiss: () -> Unit,
    onSave: (Word) -> Boolean
) {
    var collocation by remember(word.id) {
        mutableStateOf(word.collocation)
    }
    var meaning by remember(word.id) {
        mutableStateOf(word.meaning)
    }
    var example by remember(word.id) {
        mutableStateOf(word.example)
    }
    var translation by remember(word.id) {
        mutableStateOf(word.exampleTranslation)
    }
    var message by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HubCard,
        title = {
            Text("Edit Collocation", color = HubText)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HubInput(
                    label = if (word.lessonId == 0) {
                        "Collocation *"
                    } else {
                        "Collocation (Course Word)"
                    },
                    value = collocation,
                    onValueChange = { collocation = it },
                    readOnly = word.lessonId != 0
                )

                HubInput(
                    label = "Meaning",
                    value = meaning,
                    onValueChange = { meaning = it }
                )

                HubInput(
                    label = "Example sentence",
                    value = example,
                    onValueChange = { example = it }
                )

                HubInput(
                    label = "Example translation",
                    value = translation,
                    onValueChange = { translation = it }
                )

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = Color(0xFFFF7777),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (
                        word.lessonId == 0 &&
                        collocation.isBlank()
                    ) {
                        message = "Collocation is required."
                    } else {
                        val updatedWord = word.copy(
                            collocation = if (word.lessonId == 0) {
                                collocation.trim()
                            } else {
                                word.collocation
                            },
                            meaning = meaning.trim(),
                            example = example.trim(),
                            exampleTranslation = translation.trim()
                        )

                        val success = onSave(updatedWord)

                        if (!success) {
                            message = "Could not update this collocation."
                        }
                    }
                }
            ) {
                Text("Save", color = HubGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HubMuted)
            }
        }
    )
}
