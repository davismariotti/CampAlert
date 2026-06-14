package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.EmailVerification
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
class EmailVerificationRepositoryTest : IntegrationTestBase() {
    @Autowired
    private lateinit var emailVerificationRepository: EmailVerificationRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var user: User

    @BeforeEach
    fun createUser() {
        user = userRepository.save(
            User(email = "test@example.com", passwordHash = "hash", emailVerifiedAt = null)
        )
    }

    private fun verification(
        consumedAt: Instant? = null,
        expiresAt: Instant = Instant.now().plus(10, ChronoUnit.MINUTES),
        createdAt: Instant = Instant.now(),
    ) = EmailVerification(
        id = UUID.randomUUID(),
        userId = user.id!!,
        codeHash = "sha256hash",
        createdAt = createdAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )

    @Test
    fun `saves and retrieves a row by id`() {
        val saved = emailVerificationRepository.save(verification())
        val found = emailVerificationRepository.findById(saved.id)
        assertThat(found).isPresent
        assertThat(found.get().userId).isEqualTo(user.id)
    }

    @Test
    fun `findPendingByIdForUpdate returns pending row`() {
        val saved = emailVerificationRepository.save(verification())
        val found = emailVerificationRepository.findPendingByIdForUpdate(saved.id)
        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(saved.id)
    }

    @Test
    fun `findPendingByIdForUpdate returns null for consumed row (replay rejection)`() {
        val saved = emailVerificationRepository.save(verification(consumedAt = Instant.now()))
        assertThat(emailVerificationRepository.findPendingByIdForUpdate(saved.id)).isNull()
    }

    @Test
    fun `findPendingByIdForUpdate returns null for unknown id`() {
        assertThat(emailVerificationRepository.findPendingByIdForUpdate(UUID.randomUUID())).isNull()
    }

    @Test
    fun `consumeAllPendingByUserId consumes all pending rows`() {
        val a = emailVerificationRepository.save(verification())
        val b = emailVerificationRepository.save(verification())
        emailVerificationRepository.save(verification(consumedAt = Instant.now()))

        val count = emailVerificationRepository.consumeAllPendingByUserId(user.id!!, Instant.now())

        assertThat(count).isEqualTo(2)
        assertThat(emailVerificationRepository.findPendingByIdForUpdate(a.id)).isNull()
        assertThat(emailVerificationRepository.findPendingByIdForUpdate(b.id)).isNull()
    }

    @Test
    fun `consumeAllPendingByUserId does not affect other users`() {
        val other = userRepository.save(User(email = "other@example.com", passwordHash = "hash"))
        val otherEv = emailVerificationRepository.save(verification().copy(userId = other.id!!))

        emailVerificationRepository.consumeAllPendingByUserId(user.id!!, Instant.now())

        assertThat(emailVerificationRepository.findPendingByIdForUpdate(otherEv.id)).isNotNull
    }

    @Test
    fun `findLatestByUserId returns most recently created row`() {
        val older = Instant.now().minus(5, ChronoUnit.MINUTES)
        val newer = Instant.now()
        emailVerificationRepository.save(verification(createdAt = older))
        emailVerificationRepository.save(verification(createdAt = newer))

        val latest = emailVerificationRepository.findLatestByUserId(user.id!!)

        assertThat(latest).isNotNull
        assertThat(latest!!.createdAt.truncatedTo(ChronoUnit.MILLIS))
            .isEqualTo(newer.truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    fun `findLatestByUserId returns null when no rows exist`() {
        assertThat(emailVerificationRepository.findLatestByUserId(user.id!!)).isNull()
    }
}
