package com.lotterynet.pro.ui.results

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.core.catalog.LotteryLogoBitmapLoader
import com.lotterynet.pro.core.export.NativeBitmapExport.LotteryResultsViewData

data class LotteryResultsViewState(
    val nombreLoteria: String,
    val fecha: String,
    val hora: String,
    val primerPremio: String,
    val segundoPremio: String,
    val tercerPremio: String,
    val logoAssetPath: String? = null,
    val logoUrl: String? = null,
)

@Composable
fun LotteryResultsView(
    state: LotteryResultsViewState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val logo = remember(state.logoAssetPath) {
        LotteryLogoBitmapLoader.load(context, state.logoAssetPath)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF020617),
                        Color(0xFF071A44),
                        Color(0xFF030712),
                    ),
                ),
            )
            .border(1.dp, Color(0x80F59E0B), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer(alpha = 0.34f)
                .background(Color(0x55F59E0B), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer(alpha = 0.24f)
                .background(Color(0x55DC2626), CircleShape),
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(14.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xDDFFFFFF))
                        .border(1.dp, Color(0xAAF59E0B), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logo != null) {
                        Image(
                            bitmap = logo.asImageBitmap(),
                            contentDescription = state.nombreLoteria,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("LN", color = Color(0xFFF8D568), fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "RESULTADOS",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                    )
                    Text(
                        state.nombreLoteria.uppercase(),
                        color = Color(0xFFF8D568),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xAA020617))
                    .border(1.dp, Color(0x99F59E0B), RoundedCornerShape(32.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PosterMeta("FECHA", state.fecha, Modifier.weight(1f))
                PosterMeta("SORTEO", state.hora.ifBlank { "--" }, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PosterPrizeCard("1", "1ER PREMIO", state.primerPremio, Color(0xFFF59E0B), Modifier.weight(1f))
                PosterPrizeCard("2", "2DO PREMIO", state.segundoPremio, Color(0xFF38BDF8), Modifier.weight(1f))
                PosterPrizeCard("3", "3ER PREMIO", state.tercerPremio, Color(0xFFF97316), Modifier.weight(1f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFFF59E0B), Color.Transparent),
                            ),
                        ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "RESULTADOS OFICIALES",
                    color = Color(0xFFF8D568),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PosterMeta(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFFFDE68A), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PosterPrizeCard(
    rank: String,
    label: String,
    number: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(194.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), Color(0xEE020617))))
            .border(2.dp, color, RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brush.radialGradient(listOf(Color.White, color)), CircleShape)
                .border(1.dp, Color(0xFFFFF7AD), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(rank, color = Color(0xFF111827), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(
            number.ifBlank { "--" },
            color = color,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

fun LotteryResultsViewState.toExportData(): LotteryResultsViewData {
    return LotteryResultsViewData(
        nombreLoteria = nombreLoteria,
        fecha = fecha,
        hora = hora,
        primerPremio = primerPremio,
        segundoPremio = segundoPremio,
        tercerPremio = tercerPremio,
        logoAssetPath = logoAssetPath,
    )
}
