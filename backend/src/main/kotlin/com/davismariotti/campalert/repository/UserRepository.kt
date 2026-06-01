package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.User
import org.springframework.data.repository.CrudRepository

interface UserRepository : CrudRepository<User, Long> {
    fun findByEmail(email: String): User?
}
