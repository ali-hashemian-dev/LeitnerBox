
package com.leitnerbox.app

import androidx.compose.foundation.BorderStroke
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.CalendarMonth
import kotlin.math.roundToInt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email

private val Background = Color(0xFF090B0A)
private val CardColor = Color(0xFF101010)
private val NeonGreen = Color(0xFFB7FF00)
private val MainText = Color(0xFFF2F5F2)
private val SecondaryText = Color(0xFF929892)
private val BorderColor = Color(0xFF252B27)

private val DefaultIntervals = listOf(1, 2, 4, 7, 14, 30)
private const val SETTINGS_PREFS = "leitnerbox_settings"
private const val INTERVALS_KEY = "review_intervals"
private const val TEST_MODE_KEY = "test_mode"

private const val DAY_MS = 24L * 60L * 60L * 1000L

private fun loadIntervals(context: Context): List<Int> {
    val saved = context.getSharedPreferences(
        SETTINGS_PREFS, Context.MODE_PRIVATE
    ).getString(INTERVALS_KEY, null) ?: return DefaultIntervals

    val parsed = saved.split(",").mapNotNull { it.toIntOrNull() }

    return if (parsed.size == 6 && parsed.all { it in 1..3650 }) {
        parsed
    } else {
        DefaultIntervals
    }
}

private fun saveIntervals(context: Context, intervals: List<Int>) {
    context.getSharedPreferences(
        SETTINGS_PREFS, Context.MODE_PRIVATE
    ).edit().putString(
        INTERVALS_KEY, intervals.joinToString(",")
    ).apply()
}

private fun loadTestMode(context: Context): Boolean {
    return context.getSharedPreferences(
        SETTINGS_PREFS, Context.MODE_PRIVATE
    ).getBoolean(TEST_MODE_KEY, false)
}

private fun saveTestMode(context: Context, enabled: Boolean) {
    context.getSharedPreferences(
        SETTINGS_PREFS, Context.MODE_PRIVATE
    ).edit().putBoolean(TEST_MODE_KEY, enabled).apply()
}

private fun intervalLabel(days: Int): String {
    return if (days == 1) "Every day" else "Every $days days"
}

private fun testIntervalSeconds(boxNumber: Int): Int {
    return if (boxNumber <= 3) 30 else 60
}

private fun formatReviewTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Not scheduled"

    return SimpleDateFormat(
        "dd MMM yyyy, HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun scheduleWordSafely(context: Context, word: Word) {
    try {
        ReviewAlarmScheduler.scheduleWord(context, word)
    } catch (_: Exception) {
        // A scheduling failure must not prevent the app from opening.
    }
}

private fun scheduleAllSafely(context: Context, database: WordDatabase) {
    try {
        ReviewAlarmScheduler.scheduleAll(context, database)
    } catch (_: Exception) {
        // The review screen remains usable even if an alarm cannot be set.
    }
}

/**
 * Recalculates review dates when switching between normal and test mode.
 * Vocabulary and box numbers are not changed.
 */
private fun applyModeSchedule(
    context: Context,
    database: WordDatabase,
    intervals: List<Int>,
    testMode: Boolean
) {
    val now = System.currentTimeMillis()

    database.getAllWords().forEach { word ->
        val nextTime = if (testMode) {
            now
        } else {
            now + intervals[word.boxNumber.coerceIn(1, 6) - 1].toLong() * DAY_MS
        }

        val updated = word.copy(nextReviewTime = nextTime)
        database.updateWord(updated)
        scheduleWordSafely(context, updated)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Background,
                    surface = CardColor,
                    primary = NeonGreen,
                    onPrimary = Background,
                    onBackground = MainText,
                    onSurface = MainText
                )
            ) {
                LeitnerBoxApp()
            }
        }
    }
}

data class LeitnerBox(
    val number: Int,
    val interval: String,
    val cards: Int
)
@Composable
fun LeitnerBoxApp() {
    var selectedTab by remember { mutableStateOf("Home") }
    var selectedBoxNumber by remember { mutableStateOf<Int?>(null) }
    var selectedBoxWord by remember { mutableStateOf<Word?>(null) }

    val context = LocalContext.current

    val database = remember(context) {
        WordDatabase(context.applicationContext)
    }

    var words by remember {
        mutableStateOf(database.getAllWords())
    }

    var intervals by remember {
        mutableStateOf(loadIntervals(context))
    }

    var testMode by remember {
        mutableStateOf(loadTestMode(context))
    }

    var currentTime by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) {
        scheduleAllSafely(context, database)

        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val refreshWords = {
        words = database.getAllWords()
    }

    val boxes = intervals.mapIndexed { index, days ->
        val number = index + 1

        LeitnerBox(
            number = number,
            interval = if (testMode) {
                "Every ${testIntervalSeconds(number)} sec"
            } else {
                intervalLabel(days)
            },
            cards = words.count { it.boxNumber == number }
        )
    }

    val dueCount = words.count {
        it.nextReviewTime <= currentTime
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {

                when (selectedTab) {

                    "Home" -> {
                        val boxNumber = selectedBoxNumber

                        if (boxNumber == null) {
                            HomeScreen(
                                boxes = boxes,
                                dueCount = dueCount,
                                totalWords = words.size,
                                onManageVocabulary = {
                                    selectedTab = "Review"
                                },
                                onBoxClick = { number ->
                                    selectedBoxNumber = number
                                    selectedBoxWord = null
                                }
                            )
                        } else {
                            val boxWords = words.filter {
                                it.boxNumber == boxNumber
                            }

                            val selectedIndex = selectedBoxWord?.let { selected ->
                                boxWords.indexOfFirst {
                                    it.id == selected.id
                                }
                            } ?: -1

                            if (selectedIndex >= 0) {
                                BoxCardDetailScreen(
                                    word = boxWords[selectedIndex],
                                    index = selectedIndex,
                                    total = boxWords.size,
                                    onBack = {
                                        selectedBoxWord = null
                                    },
                                    onPrevious = {
                                        if (selectedIndex > 0) {
                                            selectedBoxWord =
                                                boxWords[selectedIndex - 1]
                                        }
                                    },
                                    onNext = {
                                        if (selectedIndex < boxWords.lastIndex) {
                                            selectedBoxWord =
                                                boxWords[selectedIndex + 1]
                                        }
                                    }
                                )
                            } else {
                                BoxCardsScreen(
                                    boxNumber = boxNumber,
                                    words = boxWords,
                                    onBack = {
                                        selectedBoxNumber = null
                                        selectedBoxWord = null
                                    },
                                    onWordClick = { word ->
                                        selectedBoxWord = word
                                    }
                                )
                            }
                        }
                    }

                    "Review" -> ReviewScreen(
                        words = words,
                        currentTime = currentTime,
                        intervals = intervals,
                        testMode = testMode,
                        onReviewResult = { word, remembered ->

                            val newBox = if (remembered) {
                                (word.boxNumber + 1).coerceAtMost(6)
                            } else {
                                1
                            }

                            val now = System.currentTimeMillis()

                            val delayMs = if (testMode) {
                                testIntervalSeconds(newBox) * 1000L
                            } else {
                                intervals[newBox - 1].toLong() * DAY_MS
                            }

                            val updatedWord = word.copy(
                                boxNumber = newBox,
                                nextReviewTime = now + delayMs
                            )

                            database.updateWord(updatedWord)
                            scheduleWordSafely(context, updatedWord)
                            refreshWords()
                        }
                    )

                    "Vocabulary" -> VocabularyHubScreen(
                        words = words,
                        database = database,
                        onAddWord = { collocation, meaning, example, translation ->

                            val newWord = Word(
                                collocation = collocation,
                                meaning = meaning,
                                example = example,
                                exampleTranslation = translation
                            )

                            val result = database.addWord(newWord)

                            if (result != -1L) {
                                refreshWords()
                                scheduleAllSafely(context, database)
                                true
                            } else {
                                false
                            }
                        },
                        onDeleteWord = { id ->
                            ReviewAlarmScheduler.cancelWord(context, id)
                            database.deleteWord(id)
                            refreshWords()
                        },
                        onUpdateWord = { updatedWord ->

                            val result = database.updateWord(updatedWord)

                            if (result > 0) {
                                scheduleWordSafely(context, updatedWord)
                                refreshWords()
                                true
                            } else {
                                false
                            }
                        },
                        onImportComplete = {
                            refreshWords()
                            scheduleAllSafely(context, database)
                        }
                    )

                    "Statistics" -> StatisticsScreen(
                        words = words,
                        boxes = boxes,
                        dueCount = dueCount
                    )

                    "Settings" -> SettingsScreen(
                        intervals = intervals,
                        testMode = testMode,
                        onToggleTestMode = { enabled ->
                            testMode = enabled
                            saveTestMode(context, enabled)

                            applyModeSchedule(
                                context = context,
                                database = database,
                                intervals = intervals,
                                testMode = enabled
                            )

                            refreshWords()
                        },
                        onSave = { newIntervals ->
                            intervals = newIntervals
                            saveIntervals(context, newIntervals)

                            if (!testMode) {
                                applyModeSchedule(
                                    context,
                                    database,
                                    newIntervals,
                                    false
                                )
                                refreshWords()
                            } else {
                                scheduleAllSafely(context, database)
                            }
                        },
                        onReset = {
                            intervals = DefaultIntervals
                            saveIntervals(context, DefaultIntervals)

                            if (!testMode) {
                                applyModeSchedule(
                                    context,
                                    database,
                                    DefaultIntervals,
                                    false
                                )
                                refreshWords()
                            } else {
                                scheduleAllSafely(context, database)
                            }
                        },
                        onNotificationHelp = {
                            selectedTab = "NotificationHelp"
                        }
                    )

                    "NotificationHelp" -> NotificationHelpScreen(
                        onBack = {
                            selectedTab = "Settings"
                        }
                    )
                }
            }
            if (selectedTab != "NotificationHelp") {
                BottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        selectedBoxNumber = null
                        selectedBoxWord = null
                    }
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Frameless Recall Card Logo
        Canvas(
            modifier = Modifier.size(52.dp)
        ) {
            val sx = size.width / 52f
            val sy = size.height / 52f

            // Back flashcard
            drawRoundRect(
                color = Color(0xFF648A20),
                topLeft = Offset(5f * sx, 4f * sy),
                size = Size(28f * sx, 36f * sy),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    4f * sx,
                    4f * sy
                ),
                style = Stroke(width = 1.8f * sx)
            )

            // Front flashcard
            drawRoundRect(
                color = Color(0xFF101810),
                topLeft = Offset(13f * sx, 9f * sy),
                size = Size(30f * sx, 38f * sy),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    4f * sx,
                    4f * sy
                )
            )

            drawRoundRect(
                color = NeonGreen,
                topLeft = Offset(13f * sx, 9f * sy),
                size = Size(30f * sx, 38f * sy),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    4f * sx,
                    4f * sy
                ),
                style = Stroke(width = 1.8f * sx)
            )

            // Flashcard text
            drawLine(
                color = SecondaryText,
                start = Offset(19f * sx, 19f * sy),
                end = Offset(36f * sx, 19f * sy),
                strokeWidth = 1.8f * sx
            )

            drawLine(
                color = SecondaryText,
                start = Offset(19f * sx, 25f * sy),
                end = Offset(32f * sx, 25f * sy),
                strokeWidth = 1.8f * sx
            )

            // Recall checkmark
            drawLine(
                color = NeonGreen,
                start = Offset(19f * sx, 35f * sy),
                end = Offset(25f * sx, 41f * sy),
                strokeWidth = 2.8f * sx,
                cap = StrokeCap.Round
            )

            drawLine(
                color = NeonGreen,
                start = Offset(25f * sx, 41f * sy),
                end = Offset(37f * sx, 29f * sy),
                strokeWidth = 2.8f * sx,
                cap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "LeitnerBox",
                color = MainText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = "by Ali Hashemian",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
    }
}



@Composable
fun HomeScreen(
    boxes: List<LeitnerBox>,
    dueCount: Int,
    totalWords: Int,
    onManageVocabulary: () -> Unit,
    onBoxClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Header()

        // Hero section — Unified Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.leitner_hero
                ),
                contentDescription = "Smart Learning, Lasting Results",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }


        TodayReviewCard(
            dueCount = dueCount,
            totalWords = totalWords,
            onClick = onManageVocabulary
        )

        Spacer(Modifier.height(24.dp))



        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Your Leitner Boxes",
                color = MainText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "$totalWords CARDS",
                color = NeonGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        boxes.chunked(3).forEach { rowBoxes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowBoxes.forEach { box ->
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        BoxCard(
                            box = box,
                            onClick = { onBoxClick(box.number) }
                        )
                    }
                }

                repeat(3 - rowBoxes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(9.dp))
        }

        Spacer(Modifier.height(12.dp))
    }
}


@Composable
private fun LearningOverview(
    totalWords: Int,
    dueCount: Int
) {
    val panelShape = RoundedCornerShape(22.dp)
    val metricShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF0D100D),
                shape = panelShape
            )
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = panelShape
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = NeonGreen.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(15.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.size(29.dp)
                ) {
                    val lineWidth = 2.5.dp.toPx()

                    val p1 = androidx.compose.ui.geometry.Offset(
                        size.width * 0.12f,
                        size.height * 0.78f
                    )
                    val p2 = androidx.compose.ui.geometry.Offset(
                        size.width * 0.38f,
                        size.height * 0.52f
                    )
                    val p3 = androidx.compose.ui.geometry.Offset(
                        size.width * 0.59f,
                        size.height * 0.64f
                    )
                    val p4 = androidx.compose.ui.geometry.Offset(
                        size.width * 0.88f,
                        size.height * 0.22f
                    )

                    drawLine(
                        color = NeonGreen,
                        start = p1,
                        end = p2,
                        strokeWidth = lineWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawLine(
                        color = NeonGreen,
                        start = p2,
                        end = p3,
                        strokeWidth = lineWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawLine(
                        color = NeonGreen,
                        start = p3,
                        end = p4,
                        strokeWidth = lineWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    listOf(p1, p2, p3, p4).forEach { point ->
                        drawCircle(
                            color = NeonGreen,
                            radius = 2.5.dp.toPx(),
                            center = point
                        )
                    }
                }
            }

            Spacer(Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Your Learning Overview",
                    color = MainText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Learning at a glance",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color(0xFF141714),
                        shape = metricShape
                    )
                    .border(
                        width = 1.dp,
                        color = BorderColor,
                        shape = metricShape
                    )
                    .padding(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OverviewMetricIcon(
                        isDue = false,
                        color = NeonGreen
                    )

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = NeonGreen.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(50)
                            )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = totalWords.toString(),
                    color = MainText,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = "TOTAL CARDS",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color(0xFF141714),
                        shape = metricShape
                    )
                    .border(
                        width = 1.dp,
                        color = BorderColor,
                        shape = metricShape
                    )
                    .padding(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OverviewMetricIcon(
                        isDue = true,
                        color = Color(0xFFFFD166)
                    )

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = Color(0xFFFFD166),
                                shape = RoundedCornerShape(50)
                            )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = dueCount.toString(),
                    color = MainText,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = "DUE TODAY",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricIcon(
    isDue: Boolean,
    color: Color
) {
    Canvas(
        modifier = Modifier.size(25.dp)
    ) {
        val lineWidth = 1.8.dp.toPx()
        val left = size.width * 0.12f
        val top = size.height * 0.12f
        val right = size.width * 0.88f
        val bottom = size.height * 0.88f

        if (!isDue) {
            drawRoundRect(
                color = color.copy(alpha = 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    left + size.width * 0.12f,
                    top
                ),
                size = androidx.compose.ui.geometry.Size(
                    size.width * 0.68f,
                    size.height * 0.62f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = lineWidth
                )
            )

            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(
                    left,
                    top + size.height * 0.18f
                ),
                size = androidx.compose.ui.geometry.Size(
                    size.width * 0.68f,
                    size.height * 0.68f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = lineWidth
                )
            )

            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(
                    size.width * 0.25f,
                    size.height * 0.43f
                ),
                end = androidx.compose.ui.geometry.Offset(
                    size.width * 0.58f,
                    size.height * 0.43f
                ),
                strokeWidth = lineWidth
            )
        } else {
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(
                    right - left,
                    bottom - top
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = lineWidth
                )
            )

            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(left, size.height * 0.34f),
                end = androidx.compose.ui.geometry.Offset(right, size.height * 0.34f),
                strokeWidth = lineWidth
            )

            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(
                    size.width * 0.32f,
                    top
                ),
                end = androidx.compose.ui.geometry.Offset(
                    size.width * 0.32f,
                    size.height * 0.25f
                ),
                strokeWidth = lineWidth
            )

            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(
                    size.width * 0.68f,
                    top
                ),
                end = androidx.compose.ui.geometry.Offset(
                    size.width * 0.68f,
                    size.height * 0.25f
                ),
                strokeWidth = lineWidth
            )

            drawCircle(
                color = color,
                radius = 2.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    size.width * 0.68f,
                    size.height * 0.63f
                )
            )
        }
    }
}


@Composable
private fun BookHeroIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left page
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(105.dp)
                    .background(
                        color = Color(0xFF182116),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            bottomStart = 8.dp,
                            topEnd = 4.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                if (index == 3) 0.55f else 0.85f
                            )
                            .height(3.dp)
                            .background(
                                color = NeonGreen.copy(
                                    alpha = if (index == 0) 0.9f else 0.4f
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }

            // Right page
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(105.dp)
                    .background(
                        color = Color(0xFF182116),
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            bottomStart = 4.dp,
                            topEnd = 18.dp,
                            bottomEnd = 8.dp
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                if (index == 2) 0.6f else 0.9f
                            )
                            .height(3.dp)
                            .background(
                                color = if (index == 0) {
                                    Color(0xFF28D7D7)
                                } else {
                                    NeonGreen.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
    }
}


@Composable
fun TodayReviewCard(
    dueCount: Int,
    totalWords: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                CardColor,
                RoundedCornerShape(23.dp)
            )
            .padding(19.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = "Today's review",
                tint = NeonGreen,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = "TODAY'S REVIEW",
                color = NeonGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(Modifier.height(9.dp))

        Text(
            text = "Ready to practice?",
            color = MainText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val strokeWidth = 7.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        (size.width - diameter) / 2,
                        (size.height - diameter) / 2
                    )
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = BorderColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    // Decorative ring: no misleading progress percentage.
                    drawArc(
                        color = NeonGreen,
                        startAngle = -90f,
                        sweepAngle = 235f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dueCount.toString(),
                        color = MainText,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "DUE",
                        color = SecondaryText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }


            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = totalWords.toString(),
                        color = MainText,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "total cards",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "English Collocations in Use",
                    color = SecondaryText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen,
                contentColor = Background
            )
        ) {
            Text(
                text = "Start Review   →",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}



@Composable
fun BoxCard(
    box: LeitnerBox,
    onClick: () -> Unit = {}
) {

    val boxColor = when (box.number) {
        1 -> Color(0xFFB7FF00) // Neon Green
        2 -> Color(0xFF00E5C3) // Turquoise
        3 -> Color(0xFF28D7D7) // Cyan
        4 -> Color(0xFF4C9AFF) // Electric Blue
        5 -> Color(0xFF9B7BFF) // Violet
        6 -> Color(0xFFF078C6) // Neon Pink
        else -> NeonGreen
    }

    val cubeImage = when (box.number) {
        1 -> R.drawable.box_1
        2 -> R.drawable.box_2
        3 -> R.drawable.box_3
        4 -> R.drawable.box_4
        5 -> R.drawable.box_5
        6 -> R.drawable.box_6
        else -> R.drawable.box_1
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = Color(0xFF000000),
                shape = RoundedCornerShape(17.dp)
            )
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(17.dp)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Box number and activity indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .background(
                        boxColor.copy(alpha = 0.14f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${box.number}",
                    color = boxColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

        }

        Spacer(Modifier.height(5.dp))

        // Individual 3D cube image
        Image(
            painter = painterResource(id = cubeImage),
            contentDescription = "Box ${box.number} cube",
            modifier = Modifier.size(58.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(5.dp))


        Spacer(Modifier.height(4.dp))

        Text(
            text = box.interval,
            color = boxColor,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(Modifier.height(7.dp))

        Text(
            text = "${box.cards} cards",
            color = SecondaryText,
            fontSize = 10.sp
        )
    }
}
@Composable
fun ReviewScreen(
    words: List<Word>,
    currentTime: Long,
    intervals: List<Int>,
    testMode: Boolean,
    onReviewResult: (Word, Boolean) -> Unit
) {
    val dueWords = words.filter { it.nextReviewTime <= currentTime }
    val currentWord = dueWords.firstOrNull()

    var revealed by remember { mutableStateOf(false) }
    var reviewedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentWord?.id) {
        revealed = false
    }

    val totalSessionCards = dueWords.size + reviewedCount
    val progress = if (totalSessionCards > 0) {
        (reviewedCount.toFloat() / totalSessionCards).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Review",
                    color = MainText,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Your daily learning session",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            }
        }

        // Session progress
        // Session progress
        if (totalSessionCards > 0) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Session progress",
                color = MainText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(
                            BorderColor,
                            RoundedCornerShape(50)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .background(
                                NeonGreen,
                                RoundedCornerShape(50)
                            )
                    )
                }

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "$reviewedCount / $totalSessionCards",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(24.dp))


        if (currentWord == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            NeonGreen.copy(alpha = 0.12f),
                            RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        color = NeonGreen,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "All caught up!",
                    color = MainText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }


            Spacer(Modifier.height(8.dp))

            Text(
                text = "There are no collocations due for review.",
                color = SecondaryText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (reviewedCount > 0) {
                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            CardColor,
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = BorderColor,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SESSION COMPLETED",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = reviewedCount.toString(),
                        color = MainText,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = if (reviewedCount == 1) {
                            "card reviewed in this session"
                        } else {
                            "cards reviewed in this session"
                        },
                        color = SecondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Great work. Keep building your vocabulary!",
                        color = NeonGreen,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val boxNumber = currentWord.boxNumber.coerceIn(1, 6)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        CardColor,
                        RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = BorderColor,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(if (revealed) 18.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "COLLOCATION CARD",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(if (revealed) 12.dp else 18.dp))

                // Lesson and Box labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                NeonGreen.copy(alpha = 0.10f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 7.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentWord.lessonId == 0) {
                                "MY LESSON"
                            } else {
                                "LESSON ${currentWord.lessonId}"
                            },
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                NeonGreen.copy(alpha = 0.10f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 7.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BOX $boxNumber",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(if (revealed) 16.dp else 28.dp))

                Text(
                    text = currentWord.collocation,
                    color = MainText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (revealed) {
                    Spacer(Modifier.height(12.dp))

                    HorizontalDivider(color = BorderColor)

                    Spacer(Modifier.height(10.dp))

                    DetailLine(
                        "MEANING",
                        currentWord.meaning
                    )

                    Spacer(Modifier.height(8.dp))

                    DetailLine(
                        "EXAMPLE",
                        currentWord.example
                    )

                    Spacer(Modifier.height(8.dp))

                    DetailLine(
                        "TRANSLATION",
                        currentWord.exampleTranslation
                    )
                }

                Spacer(Modifier.height(if (revealed) 14.dp else 24.dp))

                if (!revealed) {
                    Button(
                        onClick = { revealed = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Background
                        )
                    ) {
                        Text(
                            text = "Reveal Answer",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            reviewedCount++
                            onReviewResult(currentWord, true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Background
                        )
                    ) {
                        Text(
                            text = "I Remembered",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            reviewedCount++
                            onReviewResult(currentWord, false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MainText
                        )
                    ) {
                        Text("I Forgot")
                    }
                }
            }
        }
    }
}

            @Composable
fun VocabularyScreen(
    words: List<Word>,
    onAddWord: (String, String, String, String) -> Boolean,
    onDeleteWord: (Int) -> Unit,
    onUpdateWord: (Word) -> Boolean
) {
    var collocation by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var editingWord by remember { mutableStateOf<Word?>(null) }

    val filteredWords = words.filter {
        it.collocation.contains(searchQuery, ignoreCase = true) ||
                it.meaning.contains(searchQuery, ignoreCase = true) ||
                it.example.contains(searchQuery, ignoreCase = true) ||
                it.exampleTranslation.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            "Vocabulary",
            color = MainText,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(5.dp))

        Text(
            "${words.size} collocations saved",
            color = SecondaryText,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardColor, RoundedCornerShape(20.dp))
                        .padding(15.dp)
                ) {
                    Text(
                        "ADD A COLLOCATION",
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    AppTextField(collocation, { collocation = it }, "Collocation", true)
                    AppTextField(meaning, { meaning = it }, "Meaning of the collocation")
                    AppTextField(example, { example = it }, "Example sentence")
                    AppTextField(translation, { translation = it }, "Example translation")

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(message, color = NeonGreen, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            when {
                                collocation.isBlank() -> message = "Enter a collocation first."
                                meaning.isBlank() -> message = "Enter the meaning."
                                example.isBlank() -> message = "Enter an example sentence."
                                translation.isBlank() -> message = "Enter the example translation."
                                else -> {
                                    val saved = onAddWord(
                                        collocation.trim(),
                                        meaning.trim(),
                                        example.trim(),
                                        translation.trim()
                                    )
                                    if (saved) {
                                        collocation = ""
                                        meaning = ""
                                        example = ""
                                        translation = ""
                                        message = "Collocation saved successfully."
                                    } else {
                                        message = "Could not save. Please try again."
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Background
                        )
                    ) {
                        Text("Save Collocation", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search vocabulary") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
            }

            if (filteredWords.isEmpty()) {
                item {
                    Text(
                        if (words.isEmpty()) {
                            "No collocations yet. Add your first one above."
                        } else {
                            "No matching collocations found."
                        },
                        color = SecondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(filteredWords, key = { it.id }) { word ->
                WordCard(
                    word = word,
                    onEdit = { editingWord = it },
                    onDelete = { onDeleteWord(word.id) }
                )
            }
        }
    }

    editingWord?.let { wordToEdit ->
        EditWordDialog(
            word = wordToEdit,
            onDismiss = { editingWord = null },
            onSave = { updatedWord ->
                val saved = onUpdateWord(updatedWord)
                if (saved) editingWord = null
                saved
            }
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(13.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences
        ),
        colors = fieldColors()
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MainText,
    unfocusedTextColor = MainText,
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor = NeonGreen,
    unfocusedLabelColor = SecondaryText,
    cursorColor = NeonGreen,
    focusedContainerColor = Background,
    unfocusedContainerColor = Background
)

@Composable
fun WordCard(
    word: Word,
    onEdit: (Word) -> Unit,
    onDelete: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColor, RoundedCornerShape(17.dp))
            .clickable { showDetails = !showDetails }
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    word.collocation,
                    color = NeonGreen,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(word.meaning, color = MainText, fontSize = 13.sp)
            }

            Text(
                if (showDetails) "−" else "+",
                color = NeonGreen,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showDetails) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(12.dp))
            DetailLine("EXAMPLE", word.example)
            Spacer(Modifier.height(10.dp))
            DetailLine("TRANSLATION", word.exampleTranslation)
            Spacer(Modifier.height(12.dp))
            DetailLine("CURRENT BOX", "Box ${word.boxNumber}")
            Spacer(Modifier.height(12.dp))
            DetailLine("NEXT REVIEW", formatReviewTime(word.nextReviewTime))
            Spacer(Modifier.height(6.dp))

            val isDue = word.nextReviewTime <= System.currentTimeMillis()

            Text(
                if (isDue) "Status: Ready for review" else "Status: Scheduled",
                color = if (isDue) NeonGreen else SecondaryText,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onEdit(word) },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonGreen)
                ) {
                    Text("Edit collocation")
                }

                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFFF6B6B)
                    )
                ) {
                    Text("Delete collocation")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardColor,
            title = { Text("Delete collocation?", color = MainText) },
            text = {
                Text(
                    "This collocation will be removed from your vocabulary.",
                    color = SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = MainText)
                }
            }
        )
    }
}

@Composable
fun EditWordDialog(
    word: Word,
    onDismiss: () -> Unit,
    onSave: (Word) -> Boolean
) {
    var collocation by remember(word.id) { mutableStateOf(word.collocation) }
    var meaning by remember(word.id) { mutableStateOf(word.meaning) }
    var example by remember(word.id) { mutableStateOf(word.example) }
    var translation by remember(word.id) { mutableStateOf(word.exampleTranslation) }
    var message by remember(word.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = { Text("Edit collocation", color = MainText) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppTextField(collocation, { collocation = it }, "Collocation", true)
                AppTextField(meaning, { meaning = it }, "Meaning of the collocation")
                AppTextField(example, { example = it }, "Example sentence")
                AppTextField(translation, { translation = it }, "Example translation")

                if (message.isNotBlank()) {
                    Text(message, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        collocation.isBlank() -> message = "Enter a collocation first."
                        meaning.isBlank() -> message = "Enter the meaning."
                        example.isBlank() -> message = "Enter an example sentence."
                        translation.isBlank() -> message = "Enter the example translation."
                        else -> {
                            val updatedWord = word.copy(
                                collocation = collocation.trim(),
                                meaning = meaning.trim(),
                                example = example.trim(),
                                exampleTranslation = translation.trim()
                            )
                            if (!onSave(updatedWord)) {
                                message = "Could not save changes. Please try again."
                            }
                        }
                    }
                }
            ) {
                Text("Save changes", color = NeonGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MainText)
            }
        }
    )
}
@Composable
fun DetailLine(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = NeonGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            color = MainText,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatisticsScreen(
    words: List<Word>,
    boxes: List<LeitnerBox>,
    dueCount: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val lessons = androidx.compose.runtime.remember(context) {
        LessonRepository.getLessons(context)
    }

    val courseTotal = lessons.sumOf { it.words.size }

    val courseSaved = words.count { word ->
        word.lessonId != 0 &&
                lessons.any { lesson -> lesson.lessonId == word.lessonId }
    }.coerceAtMost(courseTotal)

    val totalWords = words.size
    val progress = if (courseTotal > 0) {
        courseSaved.toFloat() / courseTotal.toFloat()
    } else {
        0f
    }

    val sortedBoxes = boxes.sortedBy { it.number }
    val boxTotal = sortedBoxes.sumOf { it.cards }

    val chartColors = listOf(
        Color(0xFFB7FF00),
        Color(0xFF39E58C),
        Color(0xFF28D7D7),
        Color(0xFF4C9AFF),
        Color(0xFF9B7BFF),
        Color(0xFFF078C6)
    )

    val backgroundColor = Color(0xFF090B0A)
    val cardColor = Color(0xFF101512)
    val mainText = Color(0xFFF2F5F2)
    val secondaryText = Color(0xFF929892)
    val neonGreen = Color(0xFFB7FF00)
    val borderColor = Color(0xFF29352A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Statistics",
                    color = mainText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Track your vocabulary progress.",
                    color = secondaryText,
                    fontSize = 14.sp
                )
            }

        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Course Progress",
                        color = mainText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        color = neonGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "%,d / %,d collocations".format(
                        courseSaved,
                        courseTotal
                    ),
                    color = neonGreen,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF252B27))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .clip(RoundedCornerShape(50))
                            .background(neonGreen)
                    )
                }

                Text(
                    text = "%,d collocations remaining".format(
                        (courseTotal - courseSaved).coerceAtLeast(0)
                    ),
                    color = secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatisticsMetricCard(
                title = "Saved Collocations",
                value = totalWords.toString(),
                subtitle = "Your collection",
                accent = neonGreen,
                icon = androidx.compose.material.icons.Icons.Outlined.BookmarkBorder,
                modifier = Modifier.weight(1f)
            )

            StatisticsMetricCard(
                title = "Due for Review",
                value = dueCount.toString(),
                subtitle = "Ready to practice",
                accent = Color(0xFF39E58C),
                icon = androidx.compose.material.icons.Icons.Outlined.CalendarMonth,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF101010)
            ),
            border = BorderStroke(1.dp, Color(0xFF252B27))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Vocabulary Distribution",
                        color = mainText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Your progress across six Leitner boxes",
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(190.dp)) {
                        val strokeWidth = 25.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val arcTopLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f
                        )
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = Color(0xFF252B27),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )

                        drawCircle(
                            color = Color(0xFF1C241A),
                            radius = diameter * 0.34f,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        if (boxTotal > 0) {
                            val activeBoxes = sortedBoxes.filter { it.cards > 0 }
                            var cumulative = 0f

                            val stops = mutableListOf<Pair<Float, Color>>()
                            activeBoxes.forEachIndexed { index, box ->
                                val color = chartColors.getOrElse(
                                    box.number - 1
                                ) { neonGreen }

                                val start = cumulative / boxTotal.toFloat()
                                cumulative += box.cards.toFloat()
                                val end = cumulative / boxTotal.toFloat()

                                stops.add(start.coerceIn(0f, 1f) to color)
                                stops.add(end.coerceIn(0f, 1f) to color)
                            }

                            val gradientColors = if (stops.size >= 2) {
                                stops.toTypedArray()
                            } else {
                                arrayOf(
                                    0f to neonGreen,
                                    1f to neonGreen
                                )
                            }

                            drawArc(
                                brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                    colorStops = gradientColors,
                                    center = Offset(
                                        size.width / 2f,
                                        size.height / 2f
                                    )
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Butt
                                )
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = boxTotal.toString(),
                            color = neonGreen,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "IN BOXES",
                            color = secondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp
                        )

                        Text(
                            text = "of $totalWords saved",
                            color = secondaryText,
                            fontSize = 10.sp
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF252B27))

                sortedBoxes.forEach { box ->
                    val color = chartColors.getOrElse(
                        box.number - 1
                    ) { neonGreen }

                    val percentage = if (boxTotal > 0) {
                        (box.cards.toFloat() / boxTotal * 100).roundToInt()
                    } else {
                        0
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(color)
                        )

                        Text(
                            text = "Box ${box.number}",
                            color = mainText,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        )

                        Text(
                            text = "${box.cards} cards",
                            color = secondaryText,
                            fontSize = 12.sp
                        )

                        Text(
                            text = "$percentage%",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(46.dp)
                                .padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101010)
        ),
        border = BorderStroke(
            1.dp,
            Color(0xFF252B27)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(
                        1.dp,
                        accent.copy(alpha = 0.25f),
                        RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = value,
                color = accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = title,
                color = Color(0xFFF2F5F2),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = subtitle,
                color = Color(0xFF929892),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColor, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SecondaryText, fontSize = 13.sp)

        Text(
            value,
            color = NeonGreen,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
fun SettingsScreen(
    intervals: List<Int>,
    testMode: Boolean,
    onToggleTestMode: (Boolean) -> Unit,
    onSave: (List<Int>) -> Unit,
    onReset: () -> Unit,
    onNotificationHelp: () -> Unit
) {
    var values by remember(intervals) {
        mutableStateOf(intervals.map { it.toString() })
    }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text(
            "Settings",
            color = MainText,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Customize your learning schedule.",
            color = SecondaryText,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(22.dp))

        // TEST MODE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101512), RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    Color(0xFF29352A),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TEST MODE",
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Boxes 1–3: 30 seconds. Boxes 4–6: 60 seconds.",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                Switch(
                    checked = testMode,
                    onCheckedChange = onToggleTestMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Background,
                        checkedTrackColor = NeonGreen
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (testMode) {
                    "Test mode is ON. Switching modes reschedules all cards."
                } else {
                    "Normal mode uses your review intervals in days."
                },
                color = if (testMode) NeonGreen else SecondaryText,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        // REVIEW INTERVALS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101512), RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    Color(0xFF29352A),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                "REVIEW INTERVALS",
                color = NeonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Set the number of days before a card is reviewed again in normal mode.",
                color = SecondaryText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            values.forEachIndexed { index, value ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Box ${index + 1}",
                            color = MainText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "Default: ${DefaultIntervals[index]} ${
                                if (DefaultIntervals[index] == 1) "day" else "days"
                            }",
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            if (
                                newValue.length <= 4 &&
                                (newValue.isEmpty() ||
                                        newValue.all { it.isDigit() })
                            ) {
                                values = values.toMutableList().also {
                                    it[index] = newValue
                                }
                                message = ""
                            }
                        },
                        modifier = Modifier.width(104.dp),
                        singleLine = true,
                        label = { Text("Days") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))

                Text(
                    message,
                    color = if (
                        message.contains("saved", ignoreCase = true) ||
                        message.contains("restored", ignoreCase = true)
                    ) {
                        NeonGreen
                    } else {
                        Color(0xFFFF6B6B)
                    },
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val parsed = values.map { it.toIntOrNull() }

                    when {
                        parsed.any { it == null || it !in 1..3650 } -> {
                            message =
                                "Enter a number from 1 to 3650 for every box."
                        }

                        else -> {
                            onSave(parsed.filterNotNull())
                            message = "Review intervals saved."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Background
                )
            ) {
                Text(
                    "Save Settings",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    values = DefaultIntervals.map { it.toString() }
                    onReset()
                    message = "Default intervals restored."
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MainText
                )
            ) {
                Text("Reset to Defaults")
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Switching modes reschedules existing cards but does not change their vocabulary or box numbers. In test mode, cards become available for review immediately.",
            color = SecondaryText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(24.dp))


        // ABOUT LEITNERBOX
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101512), RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    Color(0xFF29352A),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                "ABOUT",
                color = NeonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "LeitnerBox",
                color = MainText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Developed by Ali Hashemian",
                color = SecondaryText,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "LeitnerBox is a vocabulary learning app designed to help learners master collocations from English Collocations in Use: Intermediate using the Leitner spaced-repetition system. It organizes learning into review boxes and sends timely reminders to help learners review collocations at scheduled intervals and strengthen long-term retention.",
                color = MainText,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )


            Spacer(Modifier.height(18.dp))

            Divider(
                color = BorderColor,
                thickness = 1.dp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "CONTACT",
                color = NeonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = "Email",
                    tint = NeonGreen,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        "Get in Touch",
                        color = MainText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "For feedback, suggestions or bug reports.",
                        color = SecondaryText,
                        fontSize = 11.sp
                    )

                    Text(
                        "alihashemian1373@gmail.com",
                        color = NeonGreen,
                        fontSize = 12.sp
                    )
                }
            }


            Divider(
                color = BorderColor,
                thickness = 1.dp
            )

            Spacer(Modifier.height(18.dp))

            Text(
                "HOW LEITNERBOX WORKS",
                color = NeonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )

            Spacer(Modifier.height(18.dp))

            // Step 1
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "01",
                    color = NeonGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Add Your Vocabulary",
                        color = MainText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Choose collocations from the course or add your own vocabulary.",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Step 2
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "02",
                    color = NeonGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Review Your Cards",
                        color = MainText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Test your memory and check whether you remember each collocation.",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Step 3
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "03",
                    color = NeonGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Progress Through 6 Boxes",
                        color = MainText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Remembered cards move to the next box. Forgotten cards return to Box 1.",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            // Step 4
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "04",
                    color = NeonGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Build Long-Term Memory",
                        color = MainText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Review intervals increase as cards progress through the boxes.",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Divider(
                color = BorderColor,
                thickness = 1.dp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNotificationHelp()
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Notification Help",
                        color = MainText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Troubleshoot review reminders.",
                        color = SecondaryText,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "›",
                    color = NeonGreen,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
@Composable
private fun ModernNavIcon(
    tab: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val iconColor = if (selected) NeonGreen else SecondaryText

    Canvas(modifier = modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f

        fun p(x: Float, y: Float) =
            androidx.compose.ui.geometry.Offset(x * sx, y * sy)

        fun line(
            x1: Float, y1: Float,
            x2: Float, y2: Float
        ) {
            drawLine(
                color = iconColor,
                start = p(x1, y1),
                end = p(x2, y2),
                strokeWidth = 1.7.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        fun circle(x: Float, y: Float, r: Float) {
            drawCircle(
                color = iconColor,
                radius = r * sx,
                center = p(x, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.7.dp.toPx()
                )
            )
        }

        fun rect(
            left: Float, top: Float,
            right: Float, bottom: Float,
            radius: Float = 2f
        ) {
            drawRoundRect(
                color = iconColor,
                topLeft = p(left, top),
                size = androidx.compose.ui.geometry.Size(
                    (right - left) * sx,
                    (bottom - top) * sy
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    radius * sx,
                    radius * sy
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.7.dp.toPx()
                )
            )
        }

        when (tab) {
            "Home" -> {
                line(3.5f, 10.5f, 12f, 3.8f)
                line(12f, 3.8f, 20.5f, 10.5f)
                line(5.5f, 9.5f, 5.5f, 20f)
                line(5.5f, 20f, 18.5f, 20f)
                line(18.5f, 20f, 18.5f, 9.5f)
                rect(9.5f, 13.5f, 14.5f, 20f, 1f)
            }

            "Review" -> {
                rect(5f, 3.5f, 18f, 19.5f, 2.5f)
                line(8.5f, 8f, 14.5f, 8f)
                line(8.5f, 11.5f, 14.5f, 11.5f)
                line(8f, 20.5f, 18f, 20.5f)
                line(18f, 20.5f, 20f, 18.5f)
                line(20f, 7f, 20f, 18.5f)
            }

            "Vocabulary" -> {
                rect(5f, 4f, 19f, 20f, 2f)
                line(9f, 9f, 15.5f, 9f)
                line(9f, 12.5f, 15.5f, 12.5f)
                line(9f, 16f, 13.5f, 16f)
                line(5f, 7f, 7f, 7f)
            }

            "Statistics" -> {
                line(4f, 20f, 20f, 20f)
                rect(5f, 12f, 8.5f, 19f, 1f)
                rect(10.25f, 7.5f, 13.75f, 19f, 1f)
                rect(15.5f, 4f, 19f, 19f, 1f)
            }

            "Settings" -> {
                circle(12f, 12f, 8f)
                circle(12f, 12f, 3f)
                line(12f, 2.5f, 12f, 4f)
                line(12f, 20f, 12f, 21.5f)
                line(2.5f, 12f, 4f, 12f)
                line(20f, 12f, 21.5f, 12f)
                line(5.3f, 5.3f, 6.4f, 6.4f)
                line(17.6f, 17.6f, 18.7f, 18.7f)
                line(17.6f, 6.4f, 18.7f, 5.3f)
                line(5.3f, 18.7f, 6.4f, 17.6f)
            }
        }
    }
}

@Composable
fun BottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf(
        "Home",
        "Review",
        "Vocabulary",
        "Statistics",
        "Settings"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Background)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 1.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 37.dp)
                        .background(
                            color = if (selected) {
                                NeonGreen.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ModernNavIcon(
                        tab = tab,
                        selected = selected,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = tab,
                    color = if (selected) NeonGreen else SecondaryText,
                    fontSize = 9.sp,
                    fontWeight = if (selected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(5.dp))

                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(
                            color = if (selected) NeonGreen
                            else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
@Composable
fun BoxCardsScreen(
    boxNumber: Int,
    words: List<Word>,
    onBack: () -> Unit,
    onWordClick: (Word) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderColor),
            contentPadding = PaddingValues(
                horizontal = 14.dp,
                vertical = 9.dp
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardColor,
                contentColor = MainText
            )
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Back to Home",
                tint = NeonGreen,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "Back to Home",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF11170B),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF34451E),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "LEITNER BOX",
                        color = SecondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = "Box $boxNumber",
                        color = MainText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            color = Color(0xFF263916),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Flashcards",
                        tint = NeonGreen,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF34451E)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = words.size.toString(),
                    color = NeonGreen,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Flashcards",
                    color = MainText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(16.dp))

        if (words.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No flashcards in this box yet.",
                    color = SecondaryText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(
                    items = words,
                    key = { it.id }
                ) { word ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(17.dp))
                            .background(CardColor)
                            .border(
                                1.dp,
                                BorderColor,
                                RoundedCornerShape(17.dp)
                            )
                            .clickable {
                                onWordClick(word)
                            }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = word.collocation,
                            color = NeonGreen,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = word.meaning,
                            color = MainText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "Tap to view details  →",
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun BoxCardDetailScreen(
    word: Word,
    index: Int,
    total: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderColor),
            contentPadding = PaddingValues(
                horizontal = 14.dp,
                vertical = 9.dp
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardColor,
                contentColor = MainText
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Box",
                tint = NeonGreen,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "Back to Box",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "FLASHCARD DETAILS",
            color = NeonGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = CardColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = BorderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FLASHCARD PROGRESS",
                color = SecondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Text(
                text = "${index + 1} / $total",
                color = NeonGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    CardColor,
                    RoundedCornerShape(22.dp)
                )
                .border(
                    1.dp,
                    BorderColor,
                    RoundedCornerShape(22.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF151D0D),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF34451E),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "COLLOCATION",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = word.collocation,
                    color = NeonGreen,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 33.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = BorderColor)

            DetailLine(
                label = "MEANING",
                value = word.meaning
            )

            HorizontalDivider(color = BorderColor)

            DetailLine(
                label = "EXAMPLE SENTENCE",
                value = word.example
            )

            HorizontalDivider(color = BorderColor)

            DetailLine(
                label = "EXAMPLE TRANSLATION",
                value = word.exampleTranslation
            )

            HorizontalDivider(color = BorderColor)

            DetailLine(
                label = "CURRENT BOX",
                value = "Box ${word.boxNumber}"
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = index > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = NeonGreen,
                    disabledContentColor = SecondaryText
                )
            ) {
                Text("Previous")
            }

            Button(
                onClick = onNext,
                enabled = index < total - 1,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Background,
                    disabledContainerColor = BorderColor,
                    disabledContentColor = SecondaryText
                )
            ) {
                Text(
                    "Next",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
@Composable
fun NotificationHelpScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹",
                color = NeonGreen,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Notification Help",
                color = MainText,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Xiaomi / Redmi / POCO",
            color = NeonGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "1. Open Settings on your phone.\n\n" +
                    "2. Find Apps > Permissions > Background autostart.\n\n" +
                    "3. Enable Background autostart for LeitnerBox.\n\n" +
                    "4. If available, set Battery Saver to No restrictions.",
            color = MainText,
            fontSize = 14.sp,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        HorizontalDivider(color = BorderColor)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "All Android Devices",
            color = NeonGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "1. Allow notifications for LeitnerBox.\n\n" +
                    "2. If reminders arrive late, check battery optimization settings.\n\n" +
                    "3. Make sure background activity is not restricted.",
            color = MainText,
            fontSize = 14.sp,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Note: Settings names and locations may vary depending on your device model and Android version.",
            color = SecondaryText,
            fontSize = 13.sp,
            lineHeight = 21.sp
        )
    }
}
