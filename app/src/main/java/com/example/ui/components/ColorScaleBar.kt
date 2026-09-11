package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ColorPaletteType
import com.example.core.visualization.ColorMapEngine
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ColorScaleBar(
    minVal: Float,
    maxVal: Float,
    palette: ColorPaletteType,
    inverted: Boolean = false,
    unitSymbol: String = "µT",
    modifier: Modifier = Modifier
) {
    val stops = 20
    val gradientColors = List(stops) { i ->
        val t = i.toFloat() / (stops - 1).toFloat()
        ColorMapEngine.getColorForNormalized(t, palette, inverted)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateElevated.copy(alpha = 0.9f))
            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            drawRect(
                brush = Brush.horizontalGradient(gradientColors),
                size = size
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${String.format("%.1f", minVal)} $unitSymbol",
                fontSize = 11.sp,
                color = TextMuted
            )
            Text(
                text = "${String.format("%.1f", (minVal + maxVal) / 2f)}",
                fontSize = 11.sp,
                color = TextMuted
            )
            Text(
                text = "${String.format("%.1f", maxVal)} $unitSymbol",
                fontSize = 11.sp,
                color = TextPrimary
            )
        }
    }
}
