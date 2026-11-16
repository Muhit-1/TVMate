package com.tvmate.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The signature "tuner dial": a frequency scale with an orange needle. [position] is 0f..1f. */
@Composable
fun TunerDial(
    reading: String,
    caption: String,
    modifier: Modifier = Modifier,
    position: Float = 0.58f,
) {
    Column(
        modifier
            .background(TvMateColors.Ink, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                reading,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                color = Color.White,
            )
            Spacer(Modifier.padding(start = 8.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val minor = 9.dp.toPx()
            var x = 0f
            var i = 0
            while (x <= size.width) {
                val isMajor = i % 5 == 0
                val top = if (isMajor) 0f else size.height * 0.45f
                drawLine(
                    color = Color.White.copy(alpha = if (isMajor) 1f else 0.4f),
                    start = Offset(x, top),
                    end = Offset(x, size.height),
                    strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                )
                x += minor
                i++
            }
            val nx = size.width * position.coerceIn(0f, 1f)
            drawLine(TvMateColors.Orange, Offset(nx, -4.dp.toPx()), Offset(nx, size.height + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawCircle(TvMateColors.Orange.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(nx, size.height / 2), style = Stroke(2.dp.toPx()))
        }
    }
}
