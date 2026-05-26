package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Test

class UsersPayloadCacheTest {
    @Test
    fun `same raw users payload reuses parsed accounts`() {
        val cache = UsersPayloadCache<Pair<List<UserAccount>, List<UserAccount>>>()
        var parseCount = 0

        val first = cache.getOrParse("""{"admins":[],"cajeros":[]}""", emptyList<UserAccount>() to emptyList()) {
            parseCount += 1
            emptyList<UserAccount>() to emptyList()
        }
        val second = cache.getOrParse("""{"admins":[],"cajeros":[]}""", emptyList<UserAccount>() to emptyList()) {
            parseCount += 1
            emptyList<UserAccount>() to emptyList()
        }

        assertEquals(1, parseCount)
        assertSame(first, second)
    }

    @Test
    fun `invalidate forces users payload to parse again`() {
        val cache = UsersPayloadCache<Pair<List<UserAccount>, List<UserAccount>>>()

        val first = cache.getOrParse("""{"admins":[],"cajeros":[]}""", emptyList<UserAccount>() to emptyList()) {
            emptyList<UserAccount>() to emptyList()
        }
        cache.invalidate()
        val second = cache.getOrParse("""{"admins":[],"cajeros":[]}""", emptyList<UserAccount>() to emptyList()) {
            emptyList<UserAccount>() to emptyList()
        }

        assertNotSame(first, second)
    }
}
