package com.lotterynet.pro.ui.printer

import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterContractsTest {

    @Test
    fun `printer layout stays single column and ordered by operational flow`() {
        val contract = resolvePrinterLayoutContract(
            role = UserRole.ADMIN,
            canPreview = true,
            canEditPrinterPrefs = true,
        )

        assertEquals(1, contract.contentColumns)
        assertTrue(contract.compactControls)
        assertTrue(contract.singleStatusLine)
        assertEquals(
            listOf(
                PrinterSectionId.SUMMARY,
                PrinterSectionId.CONNECTION,
                PrinterSectionId.ACTIONS,
            ),
            contract.sections,
        )
    }

    @Test
    fun `printer technical sections are hidden for non operational roles`() {
        val contract = resolvePrinterLayoutContract(
            role = UserRole.MASTER,
            canPreview = true,
            canEditPrinterPrefs = false,
        )

        assertFalse(contract.sections.contains(PrinterSectionId.CONNECTION))
        assertFalse(contract.sections.contains(PrinterSectionId.PRINTER))
        assertFalse(contract.sections.contains(PrinterSectionId.PREVIEW))
        assertTrue(contract.sections.contains(PrinterSectionId.ACTIONS))
    }

    @Test
    fun `printer connection summary reflects paired bluetooth availability`() {
        val snapshot = buildPrinterConnectionSnapshot(
            role = UserRole.CASHIER,
            bluetoothEnabled = true,
            hasBluetoothPermission = true,
            pairedPrinterCount = 2,
            selectedPrinterLabel = "POS-58MM",
        )

        assertEquals("Lista", snapshot.statusLabel)
        assertTrue(snapshot.detail.contains("2 impresoras"))
        assertEquals("POS-58MM", snapshot.selectedPrinterLabel)
        assertTrue(snapshot.canManageConnection)
    }

    @Test
    fun `printer actions prioritize connection and print without preview image actions`() {
        val actions = resolvePrinterActionOrder(showBackToTicket = true)

        assertEquals(
            listOf(
                PrinterActionId.TEST_CONNECTION,
                PrinterActionId.PRINT,
                PrinterActionId.BACK_TO_TICKET,
            ),
            actions,
        )
    }

    @Test
    fun `printer connection test is first operator action`() {
        val actions = resolvePrinterActionOrder(showBackToTicket = false)

        assertEquals(PrinterActionId.TEST_CONNECTION, actions.first())
        assertTrue(actions.indexOf(PrinterActionId.TEST_CONNECTION) < actions.indexOf(PrinterActionId.PRINT))
    }

    @Test
    fun `printer test and print resolve same bluetooth address`() {
        val paired = listOf(
            PrinterDeviceOption(value = "AA:BB:CC", label = "POS-58"),
            PrinterDeviceOption(value = "DD:EE:FF", label = "Backup"),
        )

        assertEquals(
            "AA:BB:CC",
            resolveEffectiveBluetoothPrinterAddress(selectedPrinterAddress = "", pairedPrinters = paired),
        )
        assertEquals(
            "DD:EE:FF",
            resolveEffectiveBluetoothPrinterAddress(selectedPrinterAddress = "DD:EE:FF", pairedPrinters = paired),
        )
    }

    @Test
    fun `printer role labels use operational wording`() {
        assertEquals("Caja", presentPrinterRoleLabel(UserRole.CASHIER))
        assertEquals("Consulta", presentPrinterRoleLabel(UserRole.UNKNOWN))
    }

    @Test
    fun `thermal size settings are component based instead of generic items`() {
        val labels = buildThermalSizeFields(ThermalPrinterPrefs()).map { it.label }

        assertTrue(labels.contains("Número jugado"))
        assertTrue(labels.contains("Monto"))
        assertTrue(labels.contains("Tipo"))
        assertFalse(labels.contains("Escala items"))
    }

    @Test
    fun `thermal preview does not show technical guidance text`() {
        assertFalse(shouldShowThermalPrinterPreview())
    }

    @Test
    fun `thermal settings are hidden from operational users`() {
        val contract = resolvePrinterLayoutContract(
            role = UserRole.CASHIER,
            canPreview = true,
            canEditPrinterPrefs = true,
        )

        assertFalse(contract.sections.contains(PrinterSectionId.PRINTER))
        assertFalse(resolvePrinterActionOrder(showBackToTicket = false).contains(PrinterActionId.CLASSIC))
        assertFalse(resolvePrinterActionOrder(showBackToTicket = false).contains(PrinterActionId.SAVE))
    }
}
