package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PermitItineraryLeg
import com.davismariotti.campalert.model.PermitItineraryTarget
import com.davismariotti.campalert.model.PermitSearchRequest
import com.davismariotti.campalert.model.PermitSearchRequestState
import com.davismariotti.campalert.model.PermitZoneTarget
import com.davismariotti.campalert.model.SearchType
import com.davismariotti.campalert.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class PermitSearchRequestRepositoryTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repository: PermitSearchRequestRepository

    private fun zoneRequest(divisionIds: List<String> = listOf("290", "343"), completed: Boolean = false): PermitSearchRequest {
        val req = PermitSearchRequest(
            permitId = "233261",
            permitName = "Desolation Wilderness",
            groupSize = 4,
            name = "Test Zone Search",
            searchType = SearchType.ZONE,
        )
        val state = PermitSearchRequestState()
        state.permitSearchRequest = req
        state.completed = completed
        req.state = state

        val target = PermitZoneTarget()
        target.permitSearchRequest = req
        target.divisionIds = divisionIds
        target.startDay = LocalDate.of(2026, 7, 10)
        target.endDay = LocalDate.of(2026, 7, 15)
        req.zoneTarget = target

        return req
    }

    private fun itineraryRequest(
        legs: List<PermitItineraryLeg> = listOf(
            PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)),
            PermitItineraryLeg("4675323002", LocalDate.of(2026, 7, 13)),
        ),
    ): PermitSearchRequest {
        val req = PermitSearchRequest(
            permitId = "4675323",
            permitName = "Yellowstone Backcountry",
            groupSize = 2,
            name = "Test Itinerary Search",
            searchType = SearchType.ITINERARY,
        )
        val state = PermitSearchRequestState()
        state.permitSearchRequest = req
        req.state = state

        val target = PermitItineraryTarget()
        target.permitSearchRequest = req
        target.legs = legs
        req.itineraryTarget = target

        return req
    }

    @Test
    fun `saves and reads back zone target division ids as JSON`() {
        val saved = repository.save(zoneRequest(divisionIds = listOf("290", "343")))
        val found = repository.findById(saved.id!!).get()
        assertThat(found.zoneTarget?.divisionIds).containsExactly("290", "343")
        assertThat(found.zoneTarget?.startDay).isEqualTo(LocalDate.of(2026, 7, 10))
        assertThat(found.zoneTarget?.endDay).isEqualTo(LocalDate.of(2026, 7, 15))
        assertThat(found.itineraryTarget).isNull()
    }

    @Test
    fun `saves and reads back itinerary legs with nested dates as JSON`() {
        val legs = listOf(
            PermitItineraryLeg("4675323001", LocalDate.of(2026, 7, 12)),
            PermitItineraryLeg("4675323002", LocalDate.of(2026, 7, 13)),
        )
        val saved = repository.save(itineraryRequest(legs = legs))
        val found = repository.findById(saved.id!!).get()
        assertThat(found.itineraryTarget?.legs).containsExactlyElementsOf(legs)
        assertThat(found.zoneTarget).isNull()
    }

    @Test
    fun `findAllIncomplete filters by state completed`() {
        val active = repository.save(zoneRequest(completed = false))
        val completed = repository.save(zoneRequest(completed = true))
        val incompleteIds = repository.findAllIncomplete().map { it.id }
        assertThat(incompleteIds).contains(active.id).doesNotContain(completed.id)
    }

    @Test
    fun `deleting the parent cascades to state and target`() {
        val saved = repository.save(zoneRequest())
        val id = saved.id!!
        repository.deleteById(id)
        assertThat(repository.findById(id)).isEmpty()
    }
}
