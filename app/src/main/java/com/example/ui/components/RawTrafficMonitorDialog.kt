package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.communication.TrafficDirection
import com.example.core.communication.TrafficLogEntry
import com.example.core.communication.TrafficMonitorHub
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoGreenSignal
import com.example.ui.theme.GeoRedPositive
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RawTrafficMonitorDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPaused by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<TrafficLogEntry>() }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(isPaused) {
        // Load existing logs
        logs.clear()
        logs.addAll(TrafficMonitorHub.getRecentLogs().reversed())

        if (!isPaused) {
            TrafficMonitorHub.trafficLogs.collectLatest { entry ->
                logs.add(0, entry)
                if (logs.size > 200) {
                    logs.removeLast()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ElevatedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = GeoCyan,
                    contentColor = SlateDarkBackground
                )
            ) {
                Text("Close Monitor")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val allHex = logs.joinToString("\n") {
                        "${dateFormat.format(Date(it.timestamp))} [${it.direction}] (${it.length}B) ${it.hexString} | ${it.asciiString}"
                    }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Raw Traffic Logs", allHex))
                    Toast.makeText(context, "All ${logs.size} packets copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GeoCyan)
                Spacer(Modifier.width(6.dp))
                Text("Copy Log", color = GeoCyan)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertifcal if false else Alignment.CenterVertically
            ) {
                Column {
                    Text("Raw Hex Traffic Monitor", color = TextPrimary, fontSize = 16.sp)
                    Text("Protocol reverse engineering & packet inspection", color = TextMuted, fontSize = 11.sp)
                }
                Row {
                    IconButton(
                        onClick = { isPaused = !isPaused }
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) GeoAmber else GeoGreenSignal
                        )
                    }
                    IconButton(
                        onClick = {
                            TrafficMonitorHub.clearLogs()
                            logs.clear()
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Packet Count: ${logs.size}", color = TextSecondary, fontSize = 11.sp)
                    Text(if (isPaused) "PAUSED" else "LIVE STREAMING", color = if (isPaused) GeoAmber else GeoGreenSignal, fontSize = 11.sp)
                }

                Spacer(Modifier.height(6.dp))

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Waiting for scanner byte packets...", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateDarkBackground)
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs, key = { it.id }) { item ->
                            TrafficItemCard(item, dateFormat)
                        }
                    }
                }
            }
        },
        containerColor = SlateCardSurface,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun TrafficItemCard(
    item: TrafficLogEntry,
    dateFormat: SimpleDateFormat
) {
    val dirColor = if (item.direction == TrafficDirection.RX) GeoCyan else GeoAmber
    val timeStr = dateFormat.format(Date(item.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateCardSurface, RoundedCornerShape(4.dp))
            .border(0.5.dp, SlateCardBorder, RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.direction.name,
                    color = dirColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timeStr,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${item.length} bytes",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }

            Text(
                text = if (item.isChecksumValid) "CRC OK" else "RAW",
                color = if (item.isChecksumValid) GeoGreenSignal else TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(3.dp))

        // Hex representation
        Text(
            text = item.hexString,
            color = Color(0xFF80D8FF),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )

        // ASCII translation
        if (item.asciiString.isNotBlank()) {
            Text(
                text = "ASCII: ${item.asciiString}",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (item.parsedDescription.isNotBlank()) {
            Text(
                text = "Parsed: ${item.parsedDescription}",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
