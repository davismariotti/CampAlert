package com.davismariotti.campfinder.repository

import com.davismariotti.campfinder.model.SearchRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@Disabled("schema creation in Testcontainers context needs investigation")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.jpa.properties.hibernate.ddl-auto=create-drop"])
class SearchRequestRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Autowired
    lateinit var repository: SearchRequestRepository

    @Test
    fun `saves and reads back loops as JSON`() {
        val saved = repository.save(searchRequest(loops = listOf("wildcat", "coast")))
        val found = repository.findById(saved.id!!).get()
        assertThat(found.loops).containsExactly("wildcat", "coast")
    }

    @Test
    fun `update does not throw SQL grammar exception`() {
        val saved = repository.save(searchRequest(loops = listOf("wildcat")))
        val updated = repository.save(saved.copy(completed = true))
        assertThat(updated.completed).isTrue()
        assertThat(updated.loops).containsExactly("wildcat")
    }

    @Test
    fun `saves and reads back null loops`() {
        val saved = repository.save(searchRequest(loops = null))
        assertThat(repository.findById(saved.id!!).get().loops).isNull()
    }

    @Test
    fun `findByCompleted filters correctly`() {
        repository.save(searchRequest(completed = false))
        repository.save(searchRequest(completed = true))
        assertThat(repository.findByCompleted(false)).hasSize(1)
        assertThat(repository.findByCompleted(true)).hasSize(1)
    }

    private fun searchRequest(
        loops: List<String>? = listOf("wildcat"),
        completed: Boolean = false
    ) = SearchRequest(
        startDay = LocalDate.of(2026, 7, 4),
        nights = 2,
        groupSize = 4,
        campsiteId = 233359,
        loops = loops,
        name = "Test",
        completed = completed
    )
}
