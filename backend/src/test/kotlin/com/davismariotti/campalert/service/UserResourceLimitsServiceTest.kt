package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.GlobalProviderAccess
import com.davismariotti.campalert.model.GlobalProviderQuotaDefault
import com.davismariotti.campalert.model.GlobalQuotaDefault
import com.davismariotti.campalert.model.GroupMember
import com.davismariotti.campalert.model.GroupMemberId
import com.davismariotti.campalert.model.GroupProviderAccess
import com.davismariotti.campalert.model.GroupProviderAccessId
import com.davismariotti.campalert.model.GroupQuotaDefault
import com.davismariotti.campalert.model.UserProviderAccess
import com.davismariotti.campalert.model.UserProviderAccessId
import com.davismariotti.campalert.model.UserProviderQuotaOverride
import com.davismariotti.campalert.model.UserProviderQuotaOverrideId
import com.davismariotti.campalert.model.UserQuotaOverride
import com.davismariotti.campalert.provider.Provider
import com.davismariotti.campalert.repository.GlobalProviderAccessRepository
import com.davismariotti.campalert.repository.GlobalProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GlobalQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.GroupProviderAccessRepository
import com.davismariotti.campalert.repository.GroupProviderQuotaDefaultRepository
import com.davismariotti.campalert.repository.GroupQuotaDefaultRepository
import com.davismariotti.campalert.repository.UserProviderAccessRepository
import com.davismariotti.campalert.repository.UserProviderQuotaOverrideRepository
import com.davismariotti.campalert.repository.UserQuotaOverrideRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class UserResourceLimitsServiceTest {
    private val groupMemberRepository = mock(GroupMemberRepository::class.java)
    private val userQuotaOverrideRepository = mock(UserQuotaOverrideRepository::class.java)
    private val userProviderQuotaOverrideRepository = mock(UserProviderQuotaOverrideRepository::class.java)
    private val userProviderAccessRepository = mock(UserProviderAccessRepository::class.java)
    private val groupQuotaDefaultRepository = mock(GroupQuotaDefaultRepository::class.java)
    private val groupProviderQuotaDefaultRepository = mock(GroupProviderQuotaDefaultRepository::class.java)
    private val groupProviderAccessRepository = mock(GroupProviderAccessRepository::class.java)
    private val globalQuotaDefaultRepository = mock(GlobalQuotaDefaultRepository::class.java)
    private val globalProviderQuotaDefaultRepository = mock(GlobalProviderQuotaDefaultRepository::class.java)
    private val globalProviderAccessRepository = mock(GlobalProviderAccessRepository::class.java)

    private val service = UserResourceLimitsService(
        groupMemberRepository,
        userQuotaOverrideRepository,
        userProviderQuotaOverrideRepository,
        userProviderAccessRepository,
        groupQuotaDefaultRepository,
        groupProviderQuotaDefaultRepository,
        groupProviderAccessRepository,
        globalQuotaDefaultRepository,
        globalProviderQuotaDefaultRepository,
        globalProviderAccessRepository,
    )

    private val userId = 1L

    private fun noGroups() {
        `when`(groupMemberRepository.findByIdUserId(userId)).thenReturn(emptyList())
    }

    private fun inGroups(vararg groupIds: Long) {
        `when`(groupMemberRepository.findByIdUserId(userId))
            .thenReturn(groupIds.map { GroupMember(GroupMemberId(userId, it)) })
    }

    private fun noUserQuotaOverride() {
        `when`(userQuotaOverrideRepository.findById(userId)).thenReturn(Optional.empty())
    }

    private fun noGlobalQuotaDefault() {
        `when`(globalQuotaDefaultRepository.findById(GlobalQuotaDefault.SINGLETON_ID)).thenReturn(Optional.empty())
    }

    @Test
    fun `combined quota - per-user override takes precedence over group default`() {
        `when`(userQuotaOverrideRepository.findById(userId)).thenReturn(Optional.of(UserQuotaOverride(userId, 3)))
        inGroups(10L)
        `when`(groupQuotaDefaultRepository.findByGroupIdIn(listOf(10L))).thenReturn(listOf(GroupQuotaDefault(10L, 10)))

        val result = service.effectiveCombinedMaxActive(userId)

        assertEquals(3, result.value)
        assertEquals(UserResourceLimitsService.Source.USER_OVERRIDE, result.source)
    }

    @Test
    fun `combined quota - group default used when no per-user override`() {
        noUserQuotaOverride()
        inGroups(10L)
        `when`(groupQuotaDefaultRepository.findByGroupIdIn(listOf(10L))).thenReturn(listOf(GroupQuotaDefault(10L, 10)))

        val result = service.effectiveCombinedMaxActive(userId)

        assertEquals(10, result.value)
        assertEquals(UserResourceLimitsService.Source.GROUP_DEFAULT, result.source)
    }

    @Test
    fun `combined quota - global default used when no override or group default`() {
        noUserQuotaOverride()
        noGroups()
        `when`(groupQuotaDefaultRepository.findByGroupIdIn(emptyList())).thenReturn(emptyList())
        `when`(globalQuotaDefaultRepository.findById(GlobalQuotaDefault.SINGLETON_ID))
            .thenReturn(Optional.of(GlobalQuotaDefault(GlobalQuotaDefault.SINGLETON_ID, 5)))

        val result = service.effectiveCombinedMaxActive(userId)

        assertEquals(5, result.value)
        assertEquals(UserResourceLimitsService.Source.GLOBAL_DEFAULT, result.source)
    }

    @Test
    fun `combined quota - falls back to hardcoded default if global row is somehow missing`() {
        noUserQuotaOverride()
        noGroups()
        `when`(groupQuotaDefaultRepository.findByGroupIdIn(emptyList())).thenReturn(emptyList())
        noGlobalQuotaDefault()

        val result = service.effectiveCombinedMaxActive(userId)

        assertEquals(5, result.value)
        assertEquals(UserResourceLimitsService.Source.GLOBAL_DEFAULT, result.source)
    }

    @Test
    fun `combined quota - multiple group defaults resolve as most-generous-wins`() {
        noUserQuotaOverride()
        inGroups(10L, 20L)
        `when`(groupQuotaDefaultRepository.findByGroupIdIn(listOf(10L, 20L)))
            .thenReturn(listOf(GroupQuotaDefault(10L, 5), GroupQuotaDefault(20L, 15)))

        val result = service.effectiveCombinedMaxActive(userId)

        assertEquals(15, result.value)
    }

    @Test
    fun `provider quota - ReserveCalifornia defaults to 1 with no overrides`() {
        `when`(userProviderQuotaOverrideRepository.findByIdUserIdAndIdProvider(userId, Provider.RESERVE_CALIFORNIA))
            .thenReturn(null)
        noGroups()
        `when`(groupProviderQuotaDefaultRepository.findByIdGroupIdInAndIdProvider(emptyList(), Provider.RESERVE_CALIFORNIA))
            .thenReturn(emptyList())
        `when`(globalProviderQuotaDefaultRepository.findById(Provider.RESERVE_CALIFORNIA))
            .thenReturn(Optional.of(GlobalProviderQuotaDefault(Provider.RESERVE_CALIFORNIA, 1)))

        val result = service.effectiveProviderMaxActive(userId, Provider.RESERVE_CALIFORNIA)

        assertEquals(1, result.value)
        assertEquals(UserResourceLimitsService.Source.GLOBAL_DEFAULT, result.source)
    }

    @Test
    fun `provider quota - other providers are uncapped by default`() {
        `when`(userProviderQuotaOverrideRepository.findByIdUserIdAndIdProvider(userId, Provider.RECREATION_GOV))
            .thenReturn(null)
        noGroups()
        `when`(groupProviderQuotaDefaultRepository.findByIdGroupIdInAndIdProvider(emptyList(), Provider.RECREATION_GOV))
            .thenReturn(emptyList())
        `when`(globalProviderQuotaDefaultRepository.findById(Provider.RECREATION_GOV)).thenReturn(Optional.empty())

        val result = service.effectiveProviderMaxActive(userId, Provider.RECREATION_GOV)

        assertNull(result.value)
    }

    @Test
    fun `provider quota - per-user override wins over everything`() {
        `when`(userProviderQuotaOverrideRepository.findByIdUserIdAndIdProvider(userId, Provider.RESERVE_CALIFORNIA))
            .thenReturn(UserProviderQuotaOverride(UserProviderQuotaOverrideId(userId, Provider.RESERVE_CALIFORNIA), 4))

        val result = service.effectiveProviderMaxActive(userId, Provider.RESERVE_CALIFORNIA)

        assertEquals(4, result.value)
        assertEquals(UserResourceLimitsService.Source.USER_OVERRIDE, result.source)
    }

    @Test
    fun `provider access - defaults allow RecreationGov and CampLife, deny ReserveCalifornia`() {
        `when`(userProviderAccessRepository.findByIdUserIdAndIdProvider(userId, Provider.RESERVE_CALIFORNIA)).thenReturn(null)
        noGroups()
        `when`(groupProviderAccessRepository.findByIdGroupIdInAndIdProvider(emptyList(), Provider.RESERVE_CALIFORNIA))
            .thenReturn(emptyList())
        `when`(globalProviderAccessRepository.findById(Provider.RESERVE_CALIFORNIA))
            .thenReturn(Optional.of(GlobalProviderAccess(Provider.RESERVE_CALIFORNIA, false)))

        val result = service.isProviderAllowed(userId, Provider.RESERVE_CALIFORNIA)

        assertEquals(false, result.value)
        assertEquals(UserResourceLimitsService.Source.GLOBAL_DEFAULT, result.source)
    }

    @Test
    fun `provider access - per-user override grants access to a normally-disabled provider`() {
        `when`(userProviderAccessRepository.findByIdUserIdAndIdProvider(userId, Provider.RESERVE_CALIFORNIA))
            .thenReturn(UserProviderAccess(UserProviderAccessId(userId, Provider.RESERVE_CALIFORNIA), true))

        val result = service.isProviderAllowed(userId, Provider.RESERVE_CALIFORNIA)

        assertEquals(true, result.value)
        assertEquals(UserResourceLimitsService.Source.USER_OVERRIDE, result.source)
    }

    @Test
    fun `provider access - multiple group defaults resolve as any-grants-wins`() {
        `when`(userProviderAccessRepository.findByIdUserIdAndIdProvider(userId, Provider.RESERVE_CALIFORNIA)).thenReturn(null)
        inGroups(10L, 20L)
        `when`(groupProviderAccessRepository.findByIdGroupIdInAndIdProvider(listOf(10L, 20L), Provider.RESERVE_CALIFORNIA))
            .thenReturn(
                listOf(
                    GroupProviderAccess(GroupProviderAccessId(10L, Provider.RESERVE_CALIFORNIA), false),
                    GroupProviderAccess(GroupProviderAccessId(20L, Provider.RESERVE_CALIFORNIA), true),
                ),
            )

        val result = service.isProviderAllowed(userId, Provider.RESERVE_CALIFORNIA)

        assertEquals(true, result.value)
        assertEquals(UserResourceLimitsService.Source.GROUP_DEFAULT, result.source)
    }
}
