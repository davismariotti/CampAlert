package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class SearchRequestRepositoryTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repository: SearchRequestRepository

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

    private fun searchRequest(loops: List<String>? = listOf("wildcat"), completed: Boolean = false) =
        SearchRequest(
            startDay = LocalDate.of(2026, 7, 4),
            nights = 2,
            groupSize = 4,
            campsiteId = 233359,
            loops = loops,
            name = "Test",
            completed = completed
        )
}
