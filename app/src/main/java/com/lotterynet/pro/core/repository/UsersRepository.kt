package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.UserAccount

interface UsersRepository {
    fun getAdmins(): List<UserAccount>
    fun getSupervisors(): List<UserAccount> = emptyList()
    fun getCashiers(): List<UserAccount>
    fun findByIdOrUser(idOrUser: String): UserAccount?
    fun saveUsers(admins: List<UserAccount>, cashiers: List<UserAccount>)
    fun saveUsers(admins: List<UserAccount>, supervisors: List<UserAccount>, cashiers: List<UserAccount>) {
        saveUsers(admins, cashiers)
    }
}
