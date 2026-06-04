package com.lotterynet.pro.ui.tickets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.WinningPlayDetail
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.render.ticketRenderCacheKey
import com.lotterynet.pro.core.results.PrizeValidationOutcome
import com.lotterynet.pro.ui.common.LotteryNetWindowMode

class TicketOfficialContractsTest {

    @Test
    fun `official ticket startup keeps refreshed winner over stale snapshot`() {
        val snapshot = TicketRecord(
            id = "ticket-1",
            serial = "SER-FAST",
            status = "active",
            plays = listOf(PlayItem(number = "12", playType = "Q", amount = 25.0, lotteryName = "Anguila")),
        )
        val refreshed = snapshot.copy(serial = "SER-SLOW", status = "winner", totalPrize = 500.0)

        val initial = resolveInitialOfficialTicket(snapshot, refreshed)

        assertEquals("SER-SLOW", initial?.serial)
        assertEquals("winner", initial?.status)
        assertEquals(500.0, initial?.totalPrize ?: 0.0, 0.001)
    }

    @Test
    fun `official ticket candidate can recover refreshed winner by serial`() {
        val staleSnapshot = TicketRecord(
            id = "native-1780487188705",
            serial = "LN-D79F72-43362F",
            status = "active",
            totalPrize = 0.0,
        )
        val refreshedWinner = staleSnapshot.copy(
            id = "eb702835-0ae5-4d4f-9a88-af718fef5dee",
            status = "winner",
            totalPrize = 76.0,
        )

        val candidate = findOfficialTicketCandidate(
            tickets = listOf(refreshedWinner),
            ticketId = staleSnapshot.id,
            snapshot = staleSnapshot,
        )
        val initial = resolveInitialOfficialTicket(staleSnapshot, candidate)

        assertEquals("eb702835-0ae5-4d4f-9a88-af718fef5dee", candidate?.id)
        assertEquals("winner", initial?.status)
        assertEquals(76.0, initial?.totalPrize ?: 0.0, 0.001)
    }

    @Test
    fun `official ticket startup keeps winning snapshot when repository is stale`() {
        val snapshot = TicketRecord(id = "ticket-1", status = "winner", totalPrize = 500.0)
        val refreshed = snapshot.copy(status = "active", totalPrize = 0.0)

        val initial = resolveInitialOfficialTicket(snapshot, refreshed)

        assertEquals("winner", initial?.status)
        assertEquals(500.0, initial?.totalPrize ?: 0.0, 0.001)
    }

    @Test
    fun `official ticket startup prefers refreshed plays over empty quick snapshot`() {
        val snapshot = TicketRecord(
            id = "ticket-empty-copy",
            serial = "LN-A50F42-33D384",
            sellerUser = "bancay01",
            status = "active",
            total = 16.0,
            plays = emptyList(),
        )
        val refreshed = snapshot.copy(
            total = 16.0,
            plays = listOf(
                PlayItem(
                    number = "25",
                    playType = "Q",
                    amount = 2.0,
                    lotteryId = "lot-national",
                    lotteryName = "Loteria Nacional",
                ),
                PlayItem(
                    number = "03/30",
                    playType = "SP",
                    amount = 2.0,
                    lotteryId = "lot-national",
                    lotteryName = "Loteria Nacional",
                ),
            ),
        )

        val initial = resolveInitialOfficialTicket(snapshot, refreshed)

        assertEquals(2, initial?.plays?.size)
        assertEquals(listOf("25", "03/30"), initial?.plays?.map { it.number })
        assertEquals(listOf("Loteria Nacional", "Loteria Nacional"), initial?.plays?.map { it.lotteryName })
    }

    @Test
    fun `official ticket startup keeps winner status while borrowing better refreshed plays`() {
        val snapshot = TicketRecord(
            id = "ticket-winner-copy",
            status = "winner",
            total = 16.0,
            totalPrize = 144.0,
            plays = emptyList(),
        )
        val refreshed = snapshot.copy(
            status = "active",
            totalPrize = 0.0,
            plays = listOf(
                PlayItem(
                    number = "25",
                    playType = "Q",
                    amount = 2.0,
                    lotteryId = "lot-national",
                    lotteryName = "Loteria Nacional",
                ),
            ),
        )

        val initial = resolveInitialOfficialTicket(snapshot, refreshed)

        assertEquals("winner", initial?.status)
        assertEquals(144.0, initial?.totalPrize ?: 0.0, 0.001)
        assertEquals(listOf("25"), initial?.plays?.map { it.number })
    }

    @Test
    fun `official ticket intent snapshot round trips ticket payload`() {
        val ticket = TicketRecord(
            id = "ticket-json",
            serial = "SER-123",
            sellerUser = "cajero1",
            status = "active",
            total = 35.0,
            totalPrize = 1800.0,
            winningDetails = listOf(
                WinningPlayDetail(
                    lotteryName = "Anguila Mediodia",
                    playType = "Q",
                    playedNumber = "06",
                    resultNumber = "06-60-24",
                    hitPosition = "1",
                    amount = 25.0,
                    payoutAmount = 1800.0,
                ),
            ),
            plays = listOf(
                PlayItem(number = "12", playType = "Q", amount = 20.0, lotteryId = "1", lotteryName = "La Primera Día"),
                PlayItem(number = "34", playType = "Q", amount = 15.0, lotteryId = "2", lotteryName = "Anguila Mañana"),
            ),
        )

        val restored = decodeTicketRecordSnapshot(encodeTicketRecordSnapshot(ticket))

        assertEquals(ticket.id, restored?.id)
        assertEquals(ticket.serial, restored?.serial)
        assertEquals(ticket.plays.map { it.number }, restored?.plays?.map { it.number })
        assertEquals(ticket.plays.map { it.lotteryId }, restored?.plays?.map { it.lotteryId })
        assertEquals(ticket.winningDetails.map { it.resultNumber }, restored?.winningDetails?.map { it.resultNumber })
        assertEquals(ticket.winningDetails.map { it.payoutAmount }, restored?.winningDetails?.map { it.payoutAmount })
    }

    @Test
    fun `preview actions group printing before operations and secondary actions`() {
        val groups = resolveTicketPreviewActionGroups(
            showPay = false,
            showVoid = false,
            showDuplicate = true,
        )

        assertEquals(TicketPreviewSection.PRINTING, groups[0].section)
        assertEquals(
            listOf(TicketPreviewAction.THERMAL, TicketPreviewAction.WHATSAPP, TicketPreviewAction.SHARE),
            groups[0].actions,
        )
        assertEquals(TicketPreviewSection.OPERATIONS, groups[1].section)
        assertTrue(groups[1].actions.contains(TicketPreviewAction.DUPLICATE))
        assertEquals(listOf(TicketPreviewAction.SAVE), groups[2].actions)
    }

    @Test
    fun `preview actions merge void and delete into eliminate operation`() {
        val groups = resolveTicketPreviewActionGroups(
            showPay = true,
            showVoid = true,
            showDuplicate = false,
            showDelete = false,
        )

        assertEquals(TicketPreviewSection.PRINTING, groups.first().section)
        assertEquals(TicketPreviewSection.OPERATIONS, groups[1].section)
        assertEquals(listOf(TicketPreviewAction.PAY, TicketPreviewAction.DELETE), groups[1].actions)
        assertFalse(groups[1].actions.contains(TicketPreviewAction.VOID))
    }

    @Test
    fun `ticket eliminate copy warns the action cannot be reverted`() {
        assertEquals("Eliminar", TICKET_ELIMINATE_ACTION_LABEL)
        assertEquals("Eliminar ticket", TICKET_ELIMINATE_CONFIRM_TITLE)
        assertEquals("En verdad eliminarlo? No se puede revertir.", TICKET_ELIMINATE_CONFIRM_TEXT)
    }

    @Test
    fun `preview actions omit empty operation groups`() {
        val groups = resolveTicketPreviewActionGroups(
            showPay = false,
            showVoid = false,
            showDuplicate = false,
        )

        assertEquals(listOf(TicketPreviewSection.PRINTING, TicketPreviewSection.SECONDARY), groups.map { it.section })
    }

    @Test
    fun `official ticket actions keep one visible primary and overflow the rest`() {
        val contract = resolveOfficialTicketActionMenuContract(
            listOf(
                TicketPreviewAction.PAY,
                TicketPreviewAction.DUPLICATE,
                TicketPreviewAction.THERMAL,
                TicketPreviewAction.WHATSAPP,
                TicketPreviewAction.SAVE,
            ),
        )

        assertEquals(TicketPreviewAction.PAY, contract.primaryAction)
        assertEquals(1, contract.visiblePrimaryCount)
        assertEquals(
            listOf(TicketPreviewAction.DUPLICATE, TicketPreviewAction.THERMAL, TicketPreviewAction.WHATSAPP, TicketPreviewAction.SAVE),
            contract.overflowActions,
        )
    }

    @Test
    fun `official ticket compact actions keep all buttons visible`() {
        val actions = listOf(
            TicketPreviewAction.PAY,
            TicketPreviewAction.DUPLICATE,
            TicketPreviewAction.THERMAL,
            TicketPreviewAction.WHATSAPP,
            TicketPreviewAction.SAVE,
            TicketPreviewAction.DELETE,
        )

        assertEquals(actions, resolveOfficialTicketVisibleActions(actions))
    }

    @Test
    fun `official ticket snapshot labels pick straight explicitly`() {
        assertEquals("P3STRAIGHT", officialTicketSnapshotPlayTypeLabel("P3"))
        assertEquals("P4STRAIGHT", officialTicketSnapshotPlayTypeLabel("P4"))
        assertEquals("P3BOX", officialTicketSnapshotPlayTypeLabel("P3BOX"))
        assertEquals("P4BOX", officialTicketSnapshotPlayTypeLabel("P4BOX"))
    }

    @Test
    fun `official ticket print chooses device without opening printer settings`() {
        assertEquals(
            OfficialTicketPrintTarget.BLUETOOTH,
            resolveOfficialTicketPrintTarget(
                integratedAvailable = true,
                selectedBluetoothAddress = "00:11:22:33:44:55",
            ),
        )
        assertEquals(
            OfficialTicketPrintTarget.INTEGRATED,
            resolveOfficialTicketPrintTarget(
                integratedAvailable = true,
                selectedBluetoothAddress = "",
            ),
        )
        assertEquals(
            OfficialTicketPrintTarget.BLUETOOTH,
            resolveOfficialTicketPrintTarget(
                integratedAvailable = false,
                selectedBluetoothAddress = "00:11:22:33:44:55",
            ),
        )
        assertEquals(
            OfficialTicketPrintTarget.NONE,
            resolveOfficialTicketPrintTarget(
                integratedAvailable = false,
                selectedBluetoothAddress = "",
            ),
        )
    }

    @Test
    fun `cashier official ticket actions include pay for winning ticket`() {
        val ticket = TicketRecord(id = "ticket-1", status = "winner", totalPrize = 100.0)

        val groups = resolveTicketPreviewActionGroups(
            permissions = resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.PAY,
                ticket = ticket,
            ),
        )

        assertTrue(groups.any { group -> TicketPreviewAction.PAY in group.actions })
        assertTrue(groups.none { group ->
            group.actions.any { it in setOf(TicketPreviewAction.VOID, TicketPreviewAction.DELETE, TicketPreviewAction.DUPLICATE) }
        })
    }

    @Test
    fun `cashier official ticket actions include pay for spanish winner status`() {
        val ticket = TicketRecord(id = "ticket-spanish-winner", status = "GANADOR", totalPrize = 100.0)

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.CASHIER,
            mode = TicketOfficialMode.PAY,
            ticket = ticket,
        )
        val payout = resolveTicketPayoutContract(ticket, cashierPayoutLimit = 0.0)
        val groups = resolveTicketPreviewActionGroups(permissions = permissions)

        assertTrue(permissions.showPay)
        assertTrue(payout.canPay)
        assertTrue(groups.any { group -> TicketPreviewAction.PAY in group.actions })
    }

    @Test
    fun `pay lookup includes spanish winner status from server`() {
        val ticket = TicketRecord(id = "lookup-spanish-winner", status = "GANADOR")

        assertTrue(isLookupPayableTicket(ticket))
    }

    @Test
    fun `cashier repeat ticket mode exposes duplicate action`() {
        val ticket = TicketRecord(id = "ticket-repeat", status = "active")

        val groups = resolveTicketPreviewActionGroups(
            permissions = resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.DUPLICATE,
                ticket = ticket,
            ),
        )

        assertTrue(groups.any { group -> TicketPreviewAction.DUPLICATE in group.actions })
        assertTrue(groups.none { group -> TicketPreviewAction.VOID in group.actions })
    }

    @Test
    fun `cashier duplicate mode keeps pay option when scanned ticket has prize`() {
        val ticket = TicketRecord(id = "ticket-win-repeat", status = "winner", totalPrize = 250.0)

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.CASHIER,
            mode = TicketOfficialMode.DUPLICATE,
            ticket = ticket,
        )
        val groups = resolveTicketPreviewActionGroups(permissions = permissions)

        assertTrue(permissions.showDuplicate)
        assertTrue(permissions.showPay)
        assertTrue(groups.any { group -> TicketPreviewAction.DUPLICATE in group.actions })
        assertTrue(groups.any { group -> TicketPreviewAction.PAY in group.actions })
    }

    @Test
    fun `paid ticket cannot be paid twice`() {
        val ticket = TicketRecord(id = "ticket-paid", status = "paid", totalPrize = 100.0)

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.CASHIER,
            mode = TicketOfficialMode.PAY,
            ticket = ticket,
        )
        val payout = resolveTicketPayoutContract(ticket, cashierPayoutLimit = 0.0)

        assertFalse(permissions.showPay)
        assertFalse(payout.canPay)
        assertTrue(payout.alreadyPaid)
    }

    @Test
    fun `legacy paid ticket cannot be paid twice`() {
        val ticket = TicketRecord(id = "ticket-paid-legacy", status = "premio_pagado", totalPrize = 100.0)

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.CASHIER,
            mode = TicketOfficialMode.PAY,
            ticket = ticket,
        )
        val payout = resolveTicketPayoutContract(ticket, cashierPayoutLimit = 0.0)

        assertFalse(permissions.showPay)
        assertFalse(payout.canPay)
        assertTrue(payout.alreadyPaid)
    }

    @Test
    fun `ticket with detected prize can be marked paid even before winner status`() {
        val ticket = TicketRecord(id = "ticket-prize", status = "active", total = 25.0, totalPrize = 500.0)

        val paid = resolveTicketAfterPayoutValidation(ticket)

        assertEquals("paid", paid.status)
        assertEquals(500.0, paid.totalPrize, 0.001)
    }

    @Test
    fun `payout voucher share text exposes paid proof without pending language`() {
        val ticket = TicketRecord(
            id = "LN-E8E25B-E01626",
            serial = "LN-E8E25B-E01626",
            status = "paid",
            sellerUser = "bancay06",
            total = 139.0,
            totalPrize = 139.0,
        )

        val text = buildTicketPayoutVoucherShareText(ticket, bancaName = "Banca juan")

        assertTrue(text.contains("Voucher de pago"))
        assertTrue(text.contains("LN-E8E25B-E01626"))
        assertTrue(text.contains("Pagado"))
        assertTrue(text.contains("139"))
        assertFalse(text.contains("pendiente", ignoreCase = true))
    }

    @Test
    fun `payout voucher share text does not use sale total as prize`() {
        val ticket = TicketRecord(
            id = "LN-E8E25B-E01626",
            serial = "LN-E8E25B-E01626",
            status = "paid",
            sellerUser = "bancay06",
            total = 139.0,
            totalPrize = 0.0,
        )

        val text = buildTicketPayoutVoucherShareText(ticket, bancaName = "Banca juan")

        assertTrue(text.contains("Pendiente de confirmar"))
        assertFalse(text.contains("Premio: $139"))
    }

    @Test
    fun `official ticket local validation cannot remove confirmed pending prize`() {
        val current = TicketRecord(id = "ticket-win", status = "winner", total = 25.0, totalPrize = 500.0)
        val canonical = current.copy(status = "active", totalPrize = 500.0)
        val localNoPrize = current.copy(status = "active", totalPrize = 0.0)

        val protected = resolveOfficialTicketAfterLocalValidation(
            current = current,
            canonical = canonical,
            validation = PrizeValidationOutcome(
                ticket = localNoPrize,
                totalPrize = 0.0,
                matchCount = 0,
                didValidate = true,
            ),
        )

        assertEquals("winner", protected.status)
        assertEquals(500.0, protected.totalPrize, 0.001)
    }

    @Test
    fun `paid ticket sync uses ticket admin owner so other devices receive payout status`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-device",
            username = "cajero01",
            adminId = "session-admin",
            adminUser = "admin01",
        )
        val ticket = TicketRecord(
            id = "ticket-paid",
            adminId = "ticket-admin",
            sellerId = "cashier-device",
            status = "paid",
            totalPrize = 500.0,
        )

        assertEquals("ticket-admin", resolveTicketPayoutSyncOwnerKey(session, ticket))
    }

    @Test
    fun `delete and void refresh every admin owner key used by realtime listeners`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-device",
            username = "nicola01",
            adminId = "ADM-C5FFB0",
            adminUser = "nicola01",
        )
        val ticket = TicketRecord(
            id = "ticket-delete",
            adminId = "legacy-admin-id",
            adminUser = "nicola01",
            sellerId = "banca01",
            status = "active",
        )

        assertEquals(
            listOf("legacy-admin-id", "banca01", "ADM-C5FFB0", "admin-device", "nicola01"),
            resolveTicketRealtimeSyncOwnerKeys(session, ticket),
        )
    }

    @Test
    fun `delete and void owner keys fall back to operational owner when ticket lacks admin id`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-device",
            username = "banca01",
            adminId = "ADM-C5FFB0",
            adminUser = "nicola01",
        )
        val ticket = TicketRecord(
            id = "ticket-delete",
            sellerId = "cashier-device",
            sellerUser = "banca01",
            status = "active",
        )

        assertEquals(
            listOf("cashier-device", "ADM-C5FFB0", "banca01", "nicola01"),
            resolveTicketRealtimeSyncOwnerKeys(session, ticket),
        )
    }

    @Test
    fun `official ticket refresh listens to admin code and username aliases`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-device",
            username = "podero02",
            adminId = "ADM-C5FFB0",
            adminUser = "podero02",
        )
        val ticket = TicketRecord(
            id = "native-1780487188705",
            serial = "LN-D79F72-43362F",
            adminId = "ADM-C5FFB0",
            adminUser = "podero02",
            sellerUser = "podero02",
            status = "active",
        )

        val ownerKeys = resolveTicketRealtimeSyncOwnerKeys(session, ticket)

        assertTrue(ownerKeys.contains("ADM-C5FFB0"))
        assertTrue(ownerKeys.contains("podero02"))
        assertTrue(ownerKeys.contains("admin-device"))
    }

    @Test
    fun `winning ticket payout payload uses stable ids from the sold ticket`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-device",
            username = "cajero01",
            adminId = "session-admin",
            adminUser = "admin01",
        )
        val ticket = TicketRecord(
            id = "ticket-win",
            adminId = "ticket-admin-id",
            adminUser = "admin01",
            sellerId = "cashier-ticket-id",
            sellerUser = "cajero01",
            status = "winner",
            totalPrize = 500.0,
        )

        val request = resolveTicketPayoutBackendRequest(session, ticket)

        assertEquals("cajero01", request.actorKey)
        assertEquals("ticket-admin-id", request.adminKey)
        assertEquals("ticket-admin-id", request.ownerKey)
        assertEquals("cashier-ticket-id", request.cashierKey)
        assertEquals("ticket-win", request.localTicketId)
        assertEquals("ticket-win", request.clientRequestId)
    }

    @Test
    fun `ticket backend actions use operational username instead of auth uid`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "5e9553d2-72b2-484e-8b85-095fbce6f2a4",
            username = "nicola01",
            adminId = "ADM-163C38",
            adminUser = "nicola01",
        )

        assertEquals("nicola01", resolveTicketBackendActorKey(session))
    }

    @Test
    fun `cashier payout limit blocks status changes`() {
        val ticket = TicketRecord(id = "ticket-win", status = "winner", totalPrize = 5000.0)

        val payout = resolveTicketPayoutContract(ticket, cashierPayoutLimit = 1000.0)

        assertFalse(payout.canPay)
        assertTrue(payout.blockedByLimit)
        assertEquals("Pago bloqueado por tope del cajero", payout.message)
    }

    @Test
    fun `cashier payout limit ignores sale total when prize amount is pending`() {
        val ticket = TicketRecord(id = "ticket-win", status = "winner", total = 139.0, totalPrize = 0.0)

        val payout = resolveTicketPayoutContract(ticket, cashierPayoutLimit = 100.0)

        assertTrue(payout.canPay)
        assertFalse(payout.blockedByLimit)
        assertEquals("Ticket listo para confirmar pago en servidor", payout.message)
    }

    @Test
    fun `official ticket visual uses larger result style balls`() {
        val visual = resolveOfficialTicketPlayVisualContract(partCount = 3, hasLongPart = false)

        assertTrue(visual.ballRadiusPx >= 36f)
        assertTrue(visual.rowHeightPx >= 190)
    }

    @Test
    fun `voided admin ticket exposes delete option without void step`() {
        val ticket = TicketRecord(id = "ticket-void", status = "voided")

        val groups = resolveTicketPreviewActionGroups(
            permissions = resolveOfficialTicketPermissions(
                role = UserRole.ADMIN,
                mode = TicketOfficialMode.SEARCH,
                ticket = ticket,
            ),
        )

        assertTrue(groups.any { group -> TicketPreviewAction.DELETE in group.actions })
        assertTrue(groups.none { group -> TicketPreviewAction.VOID in group.actions })
    }

    @Test
    fun `admin can delete tickets without cashier time limit`() {
        val oldTicket = TicketRecord(
            id = "old-ticket",
            status = "active",
            createdAtEpochMs = 1_000L,
        )

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.ADMIN,
            mode = TicketOfficialMode.SEARCH,
            ticket = oldTicket,
            nowEpochMs = 10_000_000L,
        )
        val voidedPermissions = resolveOfficialTicketPermissions(
            role = UserRole.ADMIN,
            mode = TicketOfficialMode.SEARCH,
            ticket = oldTicket.copy(status = "voided"),
            nowEpochMs = 10_000_000L,
        )

        assertFalse(permissions.showVoid)
        assertTrue(permissions.showDelete)
        assertFalse(voidedPermissions.showVoid)
        assertTrue(voidedPermissions.showDelete)
    }

    @Test
    fun `admin delete stays visible for paid winning ticket`() {
        val ticket = TicketRecord(
            id = "paid-win-ticket",
            status = "paid",
            totalPrize = 750.0,
        )
        val basePermissions = resolveOfficialTicketPermissions(
            role = UserRole.ADMIN,
            mode = TicketOfficialMode.SEARCH,
            ticket = ticket,
        )
        val currentPermissions = resolveOfficialTicketPermissions(
            role = UserRole.CASHIER,
            mode = TicketOfficialMode.SEARCH,
            ticket = ticket,
        )

        assertTrue(basePermissions.showDelete)
        assertTrue(
            shouldShowOfficialTicketDeleteAction(
                basePermissions = basePermissions,
                currentPermissions = currentPermissions,
                currentTicketVoided = false,
            ),
        )
    }

    @Test
    fun `cashier can only delete own active ticket during first two minutes`() {
        val ownTicket = TicketRecord(
            id = "own-ticket",
            sellerId = "cashier-1",
            status = "active",
            createdAtEpochMs = 100_000L,
        )
        val foreignTicket = ownTicket.copy(id = "foreign", sellerId = "cashier-2")

        assertTrue(
            resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.VOID,
                ticket = ownTicket,
                actorId = "cashier-1",
                actorUser = "cajero1",
                nowEpochMs = 219_999L,
            ).showDelete,
        )
        assertFalse(
            resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.VOID,
                ticket = ownTicket,
                actorId = "cashier-1",
                actorUser = "cajero1",
                nowEpochMs = 220_001L,
            ).showDelete,
        )
        assertFalse(
            resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.VOID,
                ticket = foreignTicket,
                actorId = "cashier-1",
                actorUser = "cajero1",
                nowEpochMs = 120_000L,
            ).showDelete,
        )
    }

    @Test
    fun `supervisor direct ticket screen remains read only`() {
        val ticket = TicketRecord(id = "winner", status = "winner", totalPrize = 500.0)

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.SUPERVISOR,
            mode = TicketOfficialMode.SEARCH,
            ticket = ticket,
        )

        assertFalse(permissions.showPay)
        assertFalse(permissions.showDuplicate)
        assertFalse(permissions.showVoid)
        assertFalse(permissions.showDelete)
    }

    @Test
    fun `master direct ticket screen remains read only`() {
        val ticket = TicketRecord(id = "active", status = "active")

        val permissions = resolveOfficialTicketPermissions(
            role = UserRole.MASTER,
            mode = TicketOfficialMode.SEARCH,
            ticket = ticket,
        )

        assertFalse(permissions.showPay)
        assertFalse(permissions.showDuplicate)
        assertFalse(permissions.showVoid)
        assertFalse(permissions.showDelete)
    }

    @Test
    fun `cashier cannot void when ticket lottery already closed`() {
        val ownTicket = TicketRecord(
            id = "closed-ticket",
            sellerId = "cashier-1",
            sellerUser = "cajero1",
            status = "active",
            createdAtEpochMs = 100_000L,
        )

        assertFalse(
            resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.VOID,
                ticket = ownTicket,
                actorId = "cashier-1",
                actorUser = "cajero1",
                nowEpochMs = 120_000L,
                hasClosedLottery = true,
            ).showDelete,
        )
    }

    @Test
    fun `official ticket bitmap rendering is not repeated as raw composition work`() {
        assertEquals(false, shouldRenderOfficialTicketBitmapDirectlyInComposition())
    }

    @Test
    fun `official ticket uses snapshot instead of visual bitmap preview`() {
        val policy = resolveOfficialTicketBitmapPreviewPolicy(LotteryNetWindowMode.POS)

        assertFalse(policy.renderInComposition)
        assertFalse(shouldShowOfficialTicketVisualPreview())
    }

    @Test
    fun `official ticket visual preview is capped for pos scroll`() {
        val policy = resolveOfficialTicketBitmapPreviewPolicy(LotteryNetWindowMode.POS)

        assertEquals(false, policy.renderInComposition)
        assertTrue(policy.maxHeightDp <= 520)
    }

    @Test
    fun `official ticket share uses render cache when content is unchanged`() {
        val ticket = TicketRecord(id = "T-1", status = "active", total = 10.0)

        assertEquals(
            ticketRenderCacheKey(ticket, "Banca", ""),
            resolveOfficialTicketRenderCacheKey(ticket, "Banca", ""),
        )
    }

    @Test
    fun `cashier active ticket does not expose delete option`() {
        val ticket = TicketRecord(id = "ticket-active", status = "active")

        val groups = resolveTicketPreviewActionGroups(
            permissions = resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.SEARCH,
                ticket = ticket,
            ),
        )

        assertTrue(groups.none { group -> TicketPreviewAction.DELETE in group.actions })
    }

    @Test
    fun `cashier owner active ticket exposes eliminate action during two minute window`() {
        val ticket = TicketRecord(
            id = "ticket-active-owner",
            sellerId = "cashier-1",
            sellerUser = "cajero1",
            status = "active",
            createdAtEpochMs = 100_000L,
        )

        val groups = resolveTicketPreviewActionGroups(
            permissions = resolveOfficialTicketPermissions(
                role = UserRole.CASHIER,
                mode = TicketOfficialMode.SEARCH,
                ticket = ticket,
                actorId = "cashier-1",
                actorUser = "cajero1",
                nowEpochMs = 219_999L,
            ),
        )

        assertTrue(groups.any { group -> TicketPreviewAction.DELETE in group.actions })
    }

    @Test
    fun `official ticket organizes many lotteries in two columns on pos screens`() {
        val grid = resolveTicketLotteryBadgeGrid(
            lotteryCount = 4,
            windowMode = LotteryNetWindowMode.POS,
        )

        assertEquals(2, grid.columns)
        assertEquals(6, grid.maxVisible)
    }

    @Test
    fun `duplicate ticket draft uses operator selected lottery instead of original lottery`() {
        val source = TicketRecord(
            id = "T-1",
            plays = listOf(
                PlayItem(
                    lotteryId = "old",
                    lotteryName = "Vieja",
                    playType = "Q",
                    number = "12",
                    amount = 25.0,
                ),
            ),
        )
        val selected = listOf(
            LotteryCatalogItem(
                id = "new",
                name = "Nueva Abierta",
                type = "dominicana",
                baseDrawTime = "8:00 PM",
                baseCloseTime = "7:55 PM",
                colorHex = "#123456",
                logoAssetPath = "lot-logos/13.png",
            ),
        )

        val draft = buildDuplicateSaleDraftFromTicket(source, selected)

        assertEquals(listOf("new"), draft.draft.selectedLotteryIds)
        assertEquals("new", draft.stagedRows.single().lotteryId)
        assertEquals("Nueva Abierta", draft.stagedRows.single().lotteryName)
        assertEquals("12", draft.stagedRows.single().number)
        assertEquals(25.0, draft.stagedRows.single().amount, 0.001)
    }

    @Test
    fun `duplicate lottery menu only lists open options`() {
        val open = DuplicateLotteryOption("open", "Abierta", null, "8:00 PM", isClosed = false)
        val closed = DuplicateLotteryOption("closed", "Cerrada", null, "2:00 PM", isClosed = true)

        assertEquals(listOf(open), resolveDuplicateSelectableLotteries(listOf(open, closed)))
    }

    @Test
    fun `duplicate lottery menu orders by draw time so morning draws stay before night draws`() {
        val night = DuplicateLotteryOption("14", "Anguila 9PM", null, "9:00 PM", isClosed = false)
        val morning8 = DuplicateLotteryOption("29", "Anguilla 8AM", null, "8:00 AM", isClosed = false)
        val morning9 = DuplicateLotteryOption("30", "Anguilla 9AM", null, "9:00 AM", isClosed = false)

        assertEquals(
            listOf("29", "30", "14"),
            resolveDuplicateSelectableLotteries(listOf(night, morning9, morning8)).map { it.id },
        )
    }

    @Test
    fun `ticket output uses assigned cashier name before admin banca`() {
        val ticket = TicketRecord(id = "T-1", sellerId = "cashier-1", sellerUser = "cajero01")
        val cashier = UserAccount(
            id = "cashier-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            displayName = "Banca Juan",
            banca = "Banca Admin",
        )

        assertEquals("Banca Juan", resolveTicketOutputBancaName(ticket, "Banca Admin", listOf(cashier)))
    }

    @Test
    fun `ticket output falls back to admin banca when cashier has no assigned name`() {
        val ticket = TicketRecord(id = "T-1", sellerId = "cashier-1", sellerUser = "cajero01")
        val cashier = UserAccount(id = "cashier-1", user = "cajero01", role = UserRole.CASHIER)

        assertEquals("Banca Admin", resolveTicketOutputBancaName(ticket, "Banca Admin", listOf(cashier)))
    }

    @Test
    fun `official ticket seller label resolves admin alias from account directory`() {
        val ticket = TicketRecord(
            id = "T-ADMIN",
            sellerId = "ADM-PODER",
            sellerUser = "podero02",
            adminId = "ADM-PODER",
            adminUser = "podero02",
            role = UserRole.ADMIN,
        )
        val admin = UserAccount(
            id = "ADM-PODER",
            user = "podero02",
            role = UserRole.ADMIN,
            banca = "Poderoso",
        )

        assertEquals(
            "Poderoso",
            com.lotterynet.pro.core.operations.resolveTicketActorLabel(
                ticket,
                com.lotterynet.pro.core.operations.buildUserActorLabelLookup(listOf(admin)),
                fallback = "Sin usuario",
            ),
        )
    }

    @Test
    fun `official ticket view state is local compact and complete`() {
        val ticket = TicketRecord(
            id = "T-LOCAL",
            serial = "A-0001",
            status = "winner",
            sellerUser = "caja1",
            createdAtEpochMs = 1_777_072_400_000L,
            plays = listOf(
                PlayItem("12", "Q", 25.0, lotteryId = "anguila", lotteryName = "Anguila"),
                PlayItem("12-34", "P", 10.0, lotteryId = "real", lotteryName = "Loteria Real"),
            ),
            total = 35.0,
            totalPrize = 500.0,
        )

        val state = buildOfficialTicketViewState(
            ticket = ticket,
            bancaName = "Mi Banca",
            mode = TicketOfficialMode.SEARCH,
            securityCode = "SEC-123",
            logoUri = "content://local/logo.png",
        )

        assertEquals("Ticket oficial", state.title)
        assertEquals("Mi Banca", state.bancaName)
        assertEquals("A-0001", state.serial)
        assertEquals("SEC-123", state.securityCode)
        assertEquals("caja1", state.operatorLabel)
        assertEquals("$ 500", state.totalLabel)
        assertEquals("$ 500", state.prizeLabel)
        assertTrue(state.drawValidLabel.contains("válido para el sorteo", ignoreCase = true))
        assertEquals(2, state.lotteryGroups.size)
        assertEquals(listOf("Anguila", "Loteria Real"), state.lotteryGroups.map { it.lotteryName })
        assertTrue(state.usesLocalLogo)
    }

    @Test
    fun `paid winning ticket view state keeps prize as primary total`() {
        val ticket = TicketRecord(
            id = "T-PAID-WIN",
            serial = "A-PAID",
            status = "paid",
            total = 72.0,
            totalPrize = 7_200.0,
            plays = listOf(
                PlayItem("88", "Q", 100.0, lotteryId = "loteka", lotteryName = "Loteka"),
            ),
        )

        val state = buildOfficialTicketViewState(
            ticket = ticket,
            bancaName = "Mi Banca",
            mode = TicketOfficialMode.PAY,
            securityCode = "SEC-PAID",
            logoUri = "",
        )

        assertEquals("$ 7,200", state.totalLabel)
        assertEquals("Premio", state.primaryAmountLabel)
        assertEquals("Venta $ 72", state.primaryAmountSupporting)
        assertEquals("$ 7,200", state.prizeLabel)
    }
}
