package com.danielsalas.auto_music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LyricLine(val timeMs: Long, val text: String)

@Composable
fun LyricsView(
    lyrics: String?,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val parsedLyrics = remember(lyrics) { parseLyrics(lyrics) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val activeLineIndex = remember(currentPositionMs, parsedLyrics) {
        parsedLyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            scope.launch {
                listState.animateScrollToItem(activeLineIndex)
            }
        }
    }

    if (parsedLyrics.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No hay letra disponible",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(parsedLyrics) { index, line ->
                val isActive = index == activeLineIndex
                Text(
                    text = line.text,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 24.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = if (isActive) 24.sp else 18.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = 32.sp
                    ),
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun parseLyrics(lyrics: String?): List<LyricLine> {
    if (lyrics == null) return emptyList()
    val lines = mutableListOf<LyricLine>()
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
    
    lyrics.lines().forEach { line ->
        val match = regex.find(line)
        if (match != null) {
            val min = match.groupValues[1].toLong()
            val sec = match.groupValues[2].toLong()
            val ms = match.groupValues[3].toLong()
            val text = match.groupValues[4].trim()
            val timeMs = min * 60 * 1000 + sec * 1000 + ms * (if (match.groupValues[3].length == 2) 10 else 1)
            lines.add(LyricLine(timeMs, text))
        } else if (line.isNotBlank() && !line.startsWith("[")) {
            // Simple plain lyrics fallback
            lines.add(LyricLine(0L, line))
        }
    }
    return lines
}
