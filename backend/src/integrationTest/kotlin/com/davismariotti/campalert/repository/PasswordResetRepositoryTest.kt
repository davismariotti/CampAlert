package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PasswordReset
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Transactional
class PasswordResetRepositoryTest : IntegrationTestBase() {
    @Autowired
    private lateinit var passwordResetRepository: PasswordResetRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var user: User

    @BeforeEach
    fun createUser() {
        user = userRepository.save(
            User(email = "test@example.com", passwordHash = "hash", emailVerifiedAt = Instant.now())
        )
    }

    private fun reset(
        consumedAt: Instant? = null,
        expiresAt: Instant = Instant.now().plus(15, ChronoUnit.MINUTES),
        createdAt: Instant = Instant.now(),
    ) = PasswordReset(
        id = UUID.randomUUID(),
        userId = user.id!!,
        tokenHash = "sha256tokenhash",
        createdAt = createdAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )

    @Test
    fun `saves and retrieves a row by id`() {
        val saved = passwordResetRepository.save(reset())
        val found = passwordResetRepository.findById(saved.id)
        assertThat(found).isPresent
        assertThat(found.get().userId).isEqualTo(user.id)
    }

    @Test
    fun `findPendingByIdForUpdate returns pending row`() {
        val saved = passwordResetRepository.save(reset())
        val found = passwordResetRepository.findPendingByIdForUpdate(saved.id)
        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(saved.id)
    }

    @Test
    fun `findPendingByIdForUpdate returns null for consumed row (replay rejection)`() {
        val saved = passwordResetRepository.save(reset(consumedAt = Instant.now()))
        assertThat(passwordResetRepository.findPendingByIdForUpdate(saved.id)).isNull()
    }

    @Test
    fun `findPendingByIdForUpdate returns null for unknown id`() {
        assertThat(passwordResetRepository.findPendingByIdForUpdate(UUID.randomUUID())).isNull()
    }

    @Test
    fun `consumeAllPendingByUserId consumes all pending rows`() {
        val a = passwordResetRepository.save(reset())
        val b = passwordResetRepository.save(reset())
        passwordResetRepository.save(reset(consumedAt = Instant.now()))

        val count = passwordResetRepository.consumeAllPendingByUserId(user.id!!, Instant.now())

        assertThat(count).isEqualTo(2)
        assertThat(passwordResetRepository.findPendingByIdForUpdate(a.id)).isNull()
        assertThat(passwordResetRepository.findPendingByIdForUpdate(b.id)).isNull()
    }

    @Test
    fun `consumeAllPendingByUserId does not affect other users`() {
        val other = userRepository.save(User(email = "other@example.com", passwordHash = "hash"))
        val otherReset = passwordResetRepository.save(reset().copy(userId = other.id!!))

        passwordResetRepository.consumeAllPendingByUserId(user.id!!, Instant.now())

        assertThat(passwordResetRepository.findPendingByIdForUpdate(otherReset.id)).isNotNull
    }

    @Test
    fun `findLatestByUserId returns most recently created row`() {
        val older = Instant.now().minus(5, ChronoUnit.MINUTES)
        val newer = Instant.now()
        passwordResetRepository.save(reset(createdAt = older))
        passwordResetRepository.save(reset(createdAt = newer))

        val latest = passwordResetRepository.findLatestByUserId(user.id!!)

        assertThat(latest).isNotNull
        assertThat(latest!!.createdAt.truncatedTo(ChronoUnit.MILLIS))
            .isEqualTo(newer.truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    fun `findLatestByUserId returns null when no rows exist`() {
        assertThat(passwordResetRepository.findLatestByUserId(user.id!!)).isNull()
    }
}
