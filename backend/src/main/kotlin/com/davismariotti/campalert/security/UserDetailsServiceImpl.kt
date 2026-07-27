package com.davismariotti.campalert.security

import com.davismariotti.campalert.repository.GroupAuthorityRepository
import com.davismariotti.campalert.repository.GroupMemberRepository
import com.davismariotti.campalert.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val groupAuthorityRepository: GroupAuthorityRepository,
) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("No user with email: $email")
        val groupIds = groupMemberRepository.findByIdUserId(user.id!!).map { it.id.groupId }
        // A user with no group memberships (e.g. GroupSeeder hasn't run yet) gets zero authorities
        // and fails closed on every hasAuthority(...) check rather than being granted a fake permission.
        val authorities = groupAuthorityRepository
            .findByIdGroupIdIn(groupIds)
            .map { SimpleGrantedAuthority(it.id.authority) }
        return User
            .withUsername(user.email)
            .password(user.passwordHash)
            .authorities(authorities)
            .build()
    }
}
