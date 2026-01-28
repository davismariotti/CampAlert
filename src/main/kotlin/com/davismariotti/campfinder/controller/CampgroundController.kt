package com.davismariotti.campfinder.controller

import com.davismariotti.campfinder.recreation.Campground
import com.davismariotti.campfinder.recreation.RecreationApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/")
class CampgroundController(
    val recreationApi: RecreationApi
) {
    @GetMapping("{id}")
    fun getCampground(@PathVariable("id") id: Int): ResponseEntity<Campground> {
        return ResponseEntity(recreationApi.getCampgroundAvailability(id).execute().body(), HttpStatus.OK)
    }
}