package com.lotterynet.pro.core.export

import com.lotterynet.pro.core.perf.PosPerformanceBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapExportSpecTest {
    @Test
    fun `low ram export caps requested width`() {
        val spec = NativeBitmapExport.resolveBitmapExportSpec(
            isLowRamDevice = true,
            requestedWidth = 1_600,
        )

        assertEquals(PosPerformanceBudget.LOW_RAM_BITMAP_MAX_WIDTH_PX, spec.width)
    }

    @Test
    fun `normal export keeps requested width`() {
        val spec = NativeBitmapExport.resolveBitmapExportSpec(
            isLowRamDevice = false,
            requestedWidth = 1_600,
        )

        assertEquals(1_600, spec.width)
    }

    @Test
    fun `low ram bitmap dimensions preserve aspect ratio`() {
        val size = NativeBitmapExport.resolveScaledBitmapExportSize(
            isLowRamDevice = true,
            sourceWidth = 1_440,
            sourceHeight = 2_880,
        )

        assertEquals(PosPerformanceBudget.LOW_RAM_BITMAP_MAX_WIDTH_PX, size.width)
        assertEquals(2_520, size.height)
    }

    @Test
    fun `normal bitmap dimensions are unchanged`() {
        val size = NativeBitmapExport.resolveScaledBitmapExportSize(
            isLowRamDevice = false,
            sourceWidth = 1_440,
            sourceHeight = 2_880,
        )

        assertEquals(1_440, size.width)
        assertEquals(2_880, size.height)
    }

    @Test
    fun `results whatsapp row reserves a large local logo block`() {
        val layout = NativeBitmapExport.resolveResultsWhatsAppRowLayout()

        assertTrue(layout.logoWidth >= 220f)
        assertTrue(layout.logoHeight >= 130f)
        assertTrue(layout.contentLeft >= layout.logoLeft + layout.logoWidth + 36f)
        assertTrue(layout.rowHeight >= 232)
    }

    @Test
    fun `cashier generic whatsapp result balls use green accent`() {
        assertEquals(-15293622, NativeBitmapExport.resolveResultsWhatsAppBallColor())
    }

    @Test
    fun `finance report image uses compact mockup sections`() {
        assertEquals(
            listOf("Resumen", "Transparencia", "Detalle", "Compartir"),
            NativeBitmapExport.financeReportImageSegmentLabels(),
        )
        assertEquals(
            listOf("Resumen compacto", "Transparencia", "Desglose por dia"),
            NativeBitmapExport.financeReportImageSectionLabels(periodReport = true),
        )
        assertEquals(
            listOf("Resumen compacto", "Clasificacion", "Alertas", "Cierre"),
            NativeBitmapExport.financeReportImageSectionLabels(periodReport = false),
        )
    }

    @Test
    fun `finance report image names benefit and loss by sign`() {
        assertEquals("Beneficio", NativeBitmapExport.financeReportImageResultLabel(10.0))
        assertEquals("Neutro", NativeBitmapExport.financeReportImageResultLabel(0.0))
        assertEquals("Pérdida", NativeBitmapExport.financeReportImageResultLabel(-0.01))
    }
}
