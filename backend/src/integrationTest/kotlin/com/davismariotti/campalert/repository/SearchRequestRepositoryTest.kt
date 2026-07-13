package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.RecreationGovSearchRequestDetails
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.SearchRequestState
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
        assertThat(found.recreationGovDetails?.loops).containsExactly("wildcat", "coast")
    }

    @Test
    fun `update does not throw SQL grammar exception`() {
        val saved = repository.save(searchRequest(loops = listOf("wildcat")))
        saved.state.completed = true
        val updated = repository.save(saved)
        assertThat(updated.state.completed).isTrue()
        assertThat(updated.recreationGovDetails?.loops).containsExactly("wildcat")
    }

    @Test
    fun `saves and reads back null loops`() {
        val saved = repository.save(searchRequest(loops = null))
        assertThat(repository.findById(saved.id!!).get().recreationGovDetails).isNull()
    }

    @Test
    fun `findAllByCompleted filters correctly`() {
        repository.save(searchRequest(completed = false))
        repository.save(searchRequest(completed = true))
        assertThat(repository.findAllByCompleted(false)).hasSize(1)
        assertThat(repository.findAllByCompleted(true)).hasSize(1)
    }

    private fun searchRequest(loops: List<String>? = listOf("wildcat"), completed: Boolean = false): SearchRequest {
        val req = SearchRequest(
            startDay = LocalDate.of(2026, 7, 4),
            nights = 2,
            groupSize = 4,
            campsiteId = 233359,
            name = "Test",
        )
        val st = SearchRequestState()
        st.searchRequest = req
        st.completed = completed
        req.state = st
        if (loops != null) {
            val details = RecreationGovSearchRequestDetails()
            details.searchRequest = req
            details.loops = loops
            req.recreationGovDetails = details
        }
        return req
    }
}
