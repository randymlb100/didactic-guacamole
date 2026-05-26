package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.repository.NativeBootstrapRepository

class NativeBootstrapRepositoryImpl(
    private val usersBootstrapper: NativeUsersBootstrapper,
) : NativeBootstrapRepository {
    override fun bootstrapUsers(): BootstrapResult = usersBootstrapper.bootstrap()
}
