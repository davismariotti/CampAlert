package com.davismariotti.campalert.util

import com.davismariotti.campalert.repository.UserRepository
import org.springframework.security.core.context.SecurityContextHolder

fun currentUserId(userRepository: UserRepository): Long {
    val email = SecurityContextHolder.getContext().authentication!!.name
    return userRepository.findByEmail(email)!!.id!!
}
