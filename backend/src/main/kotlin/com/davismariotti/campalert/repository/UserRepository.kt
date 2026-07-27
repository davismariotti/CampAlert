package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u WHERE u.id NOT IN (SELECT gm.id.userId FROM GroupMember gm)")
    fun findUsersWithNoGroup(): List<User>

    @Query(
        "SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR EXISTS (SELECT 1 FROM PhoneNumber p WHERE p.userId = u.id AND p.phone LIKE CONCAT('%', :query, '%'))",
    )
    fun searchUsersByQuery(query: String, pageable: Pageable): Page<User>

    fun countByLastLoginAtAfter(threshold: java.time.Instant): Long
}
