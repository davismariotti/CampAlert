package com.davismariotti.campalert.service.email

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/email/webhook")
class EmailWebhookController {
    private val log = LoggerFactory.getLogger(javaClass)

    data class BouncePayload(
        val Type: String = "",
        val Email: String = "",
        val Inactive: Boolean = false,
        val BouncedAt: String = "",
    )

    @PostMapping("/bounce")
    fun bounce(
        @RequestBody payload: BouncePayload
    ): ResponseEntity<Void> {
        log.warn(
            "email.bounce type={} email={} inactive={} bouncedAt={}",
            payload.Type,
            payload.Email,
            payload.Inactive,
            payload.BouncedAt,
        )
        return ResponseEntity.ok().build()
    }
}
