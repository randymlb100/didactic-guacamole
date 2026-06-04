package com.lotterynet.pro.ui.admin.redesign.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.ui.admin.redesign.theme.*

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trendPercent: Double? = null,
    sparklinePoints: List<Float>? = null,
    color: Color = FintechGold
) {
    Box(
        modifier = modifier
            .background(SlateCard, RoundedCornerShape(6.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Label
            Text(
                text = label.uppercase(),
                style = FintechTypography.labelSmall,
                color = SlateTextMuted
            )

            // Value & Sparkline Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = value,
                        style = FintechTypography.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = SlateTextInk
                    )

                    if (trendPercent != null) {
                        val isPositive = trendPercent >= 0
                        val trendColor = if (isPositive) SuccessGreen else DangerRed
                        val prefix = if (isPositive) "+" else ""
                        Text(
                            text = "$prefix$trendPercent%",
                            style = FintechTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = trendColor
                        )
                    }
                }

                // Sparkline
                if (!sparklinePoints.isNullOrEmpty() && sparklinePoints.size > 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val sparkColor = if (trendPercent != null && trendPercent < 0) DangerRed else SuccessGreen
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val maxVal = sparklinePoints.maxOrNull() ?: 1f
                            val minVal = sparklinePoints.minOrNull() ?: 0f
                            val delta = (maxVal - minVal).coerceAtLeast(0.1f)

                            val points = sparklinePoints.mapIndexed { idx, v ->
                                val x = idx * (width / (sparklinePoints.size - 1))
                                val y = height - ((v - minVal) / delta * height)
                                Offset(x, y)
                            }

                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = sparkColor,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}
