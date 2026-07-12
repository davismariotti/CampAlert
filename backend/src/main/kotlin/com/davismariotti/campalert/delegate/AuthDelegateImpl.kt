package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AuthApiDelegate
import com.davismariotti.campalert.api.model.AuthResponse
import com.davismariotti.campalert.api.model.ChangePasswordBody
import com.davismariotti.campalert.api.model.ErrorResponse
import com.davismariotti.campalert.api.model.ForgotPasswordBody
import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.api.model.RegisterResponse
import com.davismariotti.campalert.api.model.ResendVerificationBody
import com.davismariotti.campalert.api.model.ResetPasswordBody
import com.davismariotti.campalert.api.model.UpdateMeBody
import com.davismariotti.campalert.api.model.VerificationStatus
import com.davismariotti.campalert.api.model.VerifyEmailBody
import com.davismariotti.campalert.notification.PasswordChangedNotification
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.EmailVerificationService.VerifyResult
import com.davismariotti.campalert.service.email.PasswordResetService
import com.davismariotti.campalert.service.email.PasswordResetService.ResetResult
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.service.redis.ForgotPasswordRateLimiter
import com.davismariotti.notifications.SimpleRecipient
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
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
    private val response: HttpServletResponse,
    private val rememberMeServices: RememberMeServices,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    private val userDetailsService: UserDetailsServiceImpl,
    private val sessionRevocationService: SessionRevocationService,
    private val rememberMeTokenRepository: PersistentTokenRepository,
    private val notificationService: NotificationService,
    private val forgotPasswordRateLimiter: ForgotPasswordRateLimiter,
    @Value("\${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthApiDelegate {
    override fun register(registerBody: RegisterBody): ResponseEntity<RegisterResponse> {
        if (userRepository.findByEmail(registerBody.email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }
        val user = userRepository.save(
            UserEntity(
                email = registerBody.email,
                passwordHash = passwordEncoder.encode(registerBody.password)!!,
                timezone = registerBody.timezone ?: "America/Los_Angeles",
            ),
        )
        val verificationId = emailVerificationService.issueVerification(user.id!!, user.email)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            RegisterResponse(
                verificationId = verificationId,
                verificationStatus = VerificationStatus.PENDING_VERIFICATION,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun login(loginBody: LoginBody): ResponseEntity<AuthResponse> {
        val auth = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(loginBody.email, loginBody.password),
            )
        } catch (ex: AuthenticationException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        val user = userRepository.findByEmail(loginBody.email)!!
        if (user.emailVerifiedAt == null) {
            val verificationId = emailVerificationService.ensureVerificationForLogin(user.id!!, user.email)
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                    ErrorResponse(
                        message = "Email not verified",
                        code = "EMAIL_NOT_VERIFIED",
                        verificationId = verificationId,
                    ),
                )
                as ResponseEntity<AuthResponse>
        }

        val userDetails = auth.principal as UserDetails
        establishSession(userDetails)

        if (loginBody.rememberMe == true) {
            rememberMeServices.loginSuccessForced(request, response, auth)
        }

        return ResponseEntity.ok(user.toAuthResponse())
    }

    @PreAuthorize("isAuthenticated()")
    override fun logout(): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication!!
        rememberMeServices.logout(request, response, auth)
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("isAuthenticated()")
    override fun getMe(): ResponseEntity<AuthResponse> {
        val auth = SecurityContextHolder.getContext().authentication!!
        val user = userRepository.findByEmail(auth.name)!!
        return ResponseEntity.ok(user.toAuthResponse())
    }

    @PreAuthorize("isAuthenticated()")
    override fun updateMe(updateMeBody: UpdateMeBody): ResponseEntity<AuthResponse> {
        val auth = SecurityContextHolder.getContext().authentication!!
        val user = userRepository.findByEmail(auth.name)!!
        val updated = userRepository.save(
            user.copy(
                timezone = updateMeBody.timezone ?: user.timezone,
                pushoverUserKey = updateMeBody.pushoverUserKey ?: user.pushoverUserKey,
                pushoverApiToken = updateMeBody.pushoverApiToken ?: user.pushoverApiToken,
                pushoverOverrideEnabled = updateMeBody.pushoverOverrideEnabled ?: user.pushoverOverrideEnabled,
            ),
        )
        return ResponseEntity.ok(updated.toAuthResponse())
    }

    override fun resendVerification(resendVerificationBody: ResendVerificationBody): ResponseEntity<Unit> {
        emailVerificationService.resendVerification(resendVerificationBody.email)
        return ResponseEntity.accepted().build()
    }

    @Suppress("UNCHECKED_CAST")
    override fun verifyEmail(verifyEmailBody: VerifyEmailBody): ResponseEntity<AuthResponse> {
        val outcome = emailVerificationService.consumeVerification(verifyEmailBody.verificationId, verifyEmailBody.code)
        return when (outcome.result) {
            VerifyResult.SUCCESS -> {
                val user = outcome.user!!
                val userDetails = userDetailsService.loadUserByUsername(user.email)
                establishSession(userDetails)
                ResponseEntity.ok(user.toAuthResponse())
            }
            VerifyResult.ATTEMPTS_EXCEEDED ->
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse(message = "Attempt limit reached", code = "VERIFICATION_CODE_ATTEMPTS_EXCEEDED"))
                    as ResponseEntity<AuthResponse>
            VerifyResult.WRONG_CODE ->
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse(message = "Invalid verification code", code = "VERIFICATION_CODE_INVALID"))
                    as ResponseEntity<AuthResponse>
            VerifyResult.INVALID_OR_EXPIRED ->
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse(message = "Verification link is invalid or expired", code = "VERIFICATION_INVALID_OR_EXPIRED"))
                    as ResponseEntity<AuthResponse>
        }
    }

    private fun establishSession(userDetails: UserDetails) {
        val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        val session = request.getSession(true)
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
    }

    @Suppress("UNCHECKED_CAST")
    @PreAuthorize("isAuthenticated()")
    override fun changePassword(changePasswordBody: ChangePasswordBody): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication!!
        val user = userRepository.findByEmail(auth.name)!!

        if (!passwordEncoder.matches(changePasswordBody.currentPassword, user.passwordHash)) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse(message = "Current password is incorrect"))
                as ResponseEntity<Unit>
        }
        if (changePasswordBody.newPassword == changePasswordBody.currentPassword) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse(message = "New password must differ from current password"))
                as ResponseEntity<Unit>
        }

        userRepository.save(user.copy(passwordHash = passwordEncoder.encode(changePasswordBody.newPassword)!!))

        val currentSessionId = request.getSession(false)?.id
        if (currentSessionId != null) {
            sessionRevocationService.revokeOtherSessionsFor(auth.name, currentSessionId)
        } else {
            sessionRevocationService.revokeAllSessionsFor(auth.name)
        }
        rememberMeTokenRepository.removeUserTokens(auth.name)

        notificationService.sendAsync(PasswordChangedNotification(frontendBaseUrl), SimpleRecipient(email = user.email))

        return ResponseEntity.noContent().build()
    }

    @Suppress("UNCHECKED_CAST")
    override fun forgotPassword(forgotPasswordBody: ForgotPasswordBody): ResponseEntity<Unit> {
        if (!forgotPasswordRateLimiter.tryAcquire(request.remoteAddr)) {
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse(message = "Too many requests, please try again later"))
                as ResponseEntity<Unit>
        }
        passwordResetService.forgotPassword(forgotPasswordBody.email)
        return ResponseEntity.accepted().build()
    }

    @Suppress("UNCHECKED_CAST")
    override fun resetPassword(resetPasswordBody: ResetPasswordBody): ResponseEntity<Unit> =
        when (passwordResetService.consumeReset(resetPasswordBody.resetId, resetPasswordBody.token, resetPasswordBody.newPassword)) {
            ResetResult.SUCCESS -> ResponseEntity.noContent().build()
            ResetResult.INVALID_OR_EXPIRED ->
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse(message = "Reset link is invalid or expired", code = "RESET_INVALID_OR_EXPIRED"))
                    as ResponseEntity<Unit>
            ResetResult.PASSWORD_TOO_WEAK ->
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse(message = "Password does not meet requirements", code = "RESET_PASSWORD_TOO_WEAK"))
                    as ResponseEntity<Unit>
            ResetResult.PASSWORD_SAME_AS_CURRENT ->
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse(message = "New password must differ from current password", code = "RESET_PASSWORD_SAME_AS_CURRENT"))
                    as ResponseEntity<Unit>
        }

    private fun UserEntity.toAuthResponse() =
        AuthResponse(
            id = id!!,
            email = email,
            timezone = timezone,
            verificationStatus = VerificationStatus.VERIFIED,
            pushoverUserKey = pushoverUserKey,
            pushoverApiToken = pushoverApiToken,
            pushoverOverrideEnabled = pushoverOverrideEnabled,
        )
}
