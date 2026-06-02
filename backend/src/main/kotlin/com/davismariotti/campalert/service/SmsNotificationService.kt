package com.davismariotti.campalert.service

import com.davismariotti.campalert.model.PhoneNumberStatus
import com.davismariotti.campalert.model.SearchRequest
import com.davismariotti.campalert.model.User
import com.davismariotti.campalert.recreation.Campground
import com.davismariotti.campalert.repository.PhoneNumberRepository
import com.twilio.rest.api.v2010.account.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.twilio.type.PhoneNumber as TwilioPhoneNumber

@Service
class SmsNotificationService(
    private val twilioConfiguration: TwilioConfiguration,
    private val phoneNumberRepository: PhoneNumberRepository,
) : NotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun notify(searchRequest: SearchRequest, campground: Campground, user: User,) {
        val verifiedPhones = phoneNumberRepository.findByUserIdAndStatus(user.id!!, PhoneNumberStatus.VERIFIED)
        if (verifiedPhones.isEmpty()) {
            log.warn(
                "No verified phone numbers for user=${user.id}, skipping notification for request=${searchRequest.id}"
            )
            return
        }
        verifiedPhones.forEach { phoneNumber ->
            val body = buildMessage(searchRequest, campground, phoneNumber.firstMessageSent)
            Message.creator(
                TwilioPhoneNumber(phoneNumber.phone),
                TwilioPhoneNumber(twilioConfiguration.fromNumber),
                body,
            ).create()
            if (!phoneNumber.firstMessageSent) {
                phoneNumberRepository.save(phoneNumber.copy(firstMessageSent = true))
            }
        }
    }

    private fun buildMessage(request: SearchRequest, campground: Campground, firstMessageSent: Boolean,): String {
        val endDay = request.startDay.plusDays(request.nights.toLong())
        val sites = campground.campsites.values.joinToString("\n") { "${it.loop} ${it.site}" }
        val body =
            "${request.name} - ${request.startDay} to $endDay\n" +
                "https://www.recreation.gov/camping/campgrounds/${request.campsiteId}\n" +
                sites
        return if (firstMessageSent) body else "$body\n\nCampAlert — Reply STOP to unsubscribe"
    }
}
