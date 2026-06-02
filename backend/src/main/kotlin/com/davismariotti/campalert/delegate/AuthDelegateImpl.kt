package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AuthApiDelegate
import com.davismariotti.campalert.api.model.AuthResponse
import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import com.davismariotti.campalert.model.User as UserEntity

@Service
class AuthDelegateImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val request: HttpServletRequest,
) : AuthApiDelegate {
    override fun register(registerBody: RegisterBody): ResponseEntity<AuthResponse> {
        if (userRepository.findByEmail(registerBody.email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }
        val user = userRepository.save(
            UserEntity(
                email = registerBody.email,
                passwordHash = passwordEncoder.encode(registerBody.password),
            )
        )
        val auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(registerBody.email, registerBody.password)
        )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        val session = request.getSession(true)
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse(id = user.id!!, email = user.email))
    }

    override fun login(loginBody: LoginBody): ResponseEntity<AuthResponse> {
        val auth = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(loginBody.email, loginBody.password)
            )
        } catch (ex: AuthenticationException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        val session = request.getSession(true)
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)

        val user = userRepository.findByEmail(loginBody.email)!!
        return ResponseEntity.ok(AuthResponse(id = user.id!!, email = user.email))
    }

    @PreAuthorize("isAuthenticated()")
    override fun logout(): ResponseEntity<Unit> {
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("isAuthenticated()")
    override fun getMe(): ResponseEntity<AuthResponse> {
        val auth = SecurityContextHolder.getContext().authentication
        val user = userRepository.findByEmail(auth.name)!!
        return ResponseEntity.ok(AuthResponse(id = user.id!!, email = user.email))
    }
}
