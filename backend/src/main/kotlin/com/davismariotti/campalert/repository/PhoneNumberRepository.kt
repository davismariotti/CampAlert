package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PhoneNumber
import com.davismariotti.campalert.model.PhoneNumberStatus
import org.springframework.data.repository.CrudRepository

interface PhoneNumberRepository : CrudRepository<PhoneNumber, Long> {
    fun findByUserId(userId: Long): List<PhoneNumber>

    fun findByPhone(phone: String): PhoneNumber?

    fun findByUserIdAndStatus(userId: Long, status: PhoneNumberStatus): List<PhoneNumber>

    fun countByUserIdAndStatus(userId: Long, status: PhoneNumberStatus): Long
}
