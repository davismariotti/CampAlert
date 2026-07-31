package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.AuthApiDelegate
import com.davismariotti.campalert.api.model.AuthResponse
import com.davismariotti.campalert.api.model.ChangePasswordBody
import com.davismariotti.campalert.api.model.ForgotPasswordBody
import com.davismariotti.campalert.api.model.LoginBody
import com.davismariotti.campalert.api.model.Permission
import com.davismariotti.campalert.api.model.RegisterBody
import com.davismariotti.campalert.api.model.RegisterResponse
import com.davismariotti.campalert.api.model.RegistrationConfigResponse
import com.davismariotti.campalert.api.model.ResendVerificationBody
import com.davismariotti.campalert.api.model.ResetPasswordBody
import com.davismariotti.campalert.api.model.UpdateMeBody
import com.davismariotti.campalert.api.model.VerificationStatus
import com.davismariotti.campalert.api.model.VerifyEmailBody
import com.davismariotti.campalert.exception.BadRequestException
import com.davismariotti.campalert.exception.ConflictException
import com.davismariotti.campalert.exception.RateLimitExceededException
import com.davismariotti.campalert.exception.UnauthorizedException
import com.davismariotti.campalert.model.PlatformSettings
import com.davismariotti.campalert.notification.PasswordChangedNotification
import com.davismariotti.campalert.repository.PlatformSettingsRepository
import com.davismariotti.campalert.repository.UserRepository
import com.davismariotti.campalert.security.GroupMembershipService
import com.davismariotti.campalert.security.RememberMeServices
import com.davismariotti.campalert.security.UserDetailsServiceImpl
import com.davismariotti.campalert.service.SessionRevocationService
import com.davismariotti.campalert.service.email.EmailNotVerifiedException
import com.davismariotti.campalert.service.email.EmailVerificationException
import com.davismariotti.campalert.service.email.EmailVerificationService
import com.davismariotti.campalert.service.email.EmailVerificationService.VerifyResult
import com.davismariotti.campalert.service.email.PasswordResetException
import com.davismariotti.campalert.service.email.PasswordResetService
import com.davismariotti.campalert.service.email.PasswordResetService.ResetResult
import com.davismariotti.campalert.service.invite.InviteException
import com.davismariotti.campalert.service.invite.InviteService
import com.davismariotti.campalert.service.notification.NotificationService
import com.davismariotti.campalert.service.redis.ForgotPasswordRateLimiter
import com.davismariotti.campalert.service.turnstile.TurnstileService
import com.davismariotti.notifications.SimpleRecipient
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
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
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
    private val turnstileService: TurnstileService,
    private val groupMembershipService: GroupMembershipService,
    private val inviteService: InviteService,
    private val platformSettingsRepository: PlatformSettingsRepository,
    @Value("\${campfinder.email.frontend-base-url}") private val frontendBaseUrl: String,
    @Lazy private val self: AuthDelegateImpl,
) : AuthApiDelegate {
    override fun getRegistrationConfig(): ResponseEntity<RegistrationConfigResponse> {
        val inviteOnly = platformSettingsRepository
            .findById(PlatformSettings.SINGLETON_ID)
            .map { it.inviteOnlyEnabled }
            .orElse(false)
        return ResponseEntity.ok(RegistrationConfigResponse(inviteOnly))
    }

    override fun register(registerBody: RegisterBody): ResponseEntity<RegisterResponse> {
        turnstileService.verify(registerBody.turnstileToken)
        return self.registerAfterTurnstile(registerBody)
    }

    /**
     * Split from [register] so Turnstile's external HTTP call happens outside any DB transaction,
     * while invite validation through redemption recording runs in one transaction — the pessimistic
     * row lock `InviteService.validateAndLock` takes must be held until `recordRedemption` completes
     * for capacity-limited public links to be race-safe (design.md D1).
     */
    @Transactional
    fun registerAfterTurnstile(registerBody: RegisterBody): ResponseEntity<RegisterResponse> {
        val invite = if (registerBody.inviteId != null && registerBody.inviteToken != null) {
            inviteService.validateAndLock(registerBody.inviteId, registerBody.inviteToken, registerBody.email)
        } else {
            val inviteOnly = platformSettingsRepository
                .findById(PlatformSettings.SINGLETON_ID)
                .map { it.inviteOnlyEnabled }
                .orElse(false)
            if (inviteOnly) throw InviteException.Required()
            null
        }

        if (userRepository.findByEmail(registerBody.email) != null) {
            throw ConflictException("Email already registered")
        }

        val user = userRepository.save(
            UserEntity(
                email = registerBody.email,
                passwordHash = passwordEncoder.encode(registerBody.password)!!,
                timezone = registerBody.timezone ?: "America/Los_Angeles",
            ),
        )
        groupMembershipService.assignToStandardGroup(user.id!!)

        if (invite != null) {
            inviteService.recordRedemption(invite, user.id!!)
        }

        return if (invite?.email != null) {
            val verifiedUser = userRepository.save(user.copy(emailVerifiedAt = Instant.now()))
            val userDetails = userDetailsService.loadUserByUsername(verifiedUser.email)
            establishSession(userDetails)
            ResponseEntity.status(HttpStatus.CREATED).body(
                RegisterResponse(verificationId = null, verificationStatus = VerificationStatus.VERIFIED),
            )
        } else {
            val verificationId = emailVerificationService.issueVerification(user.id!!, user.email)
            ResponseEntity.status(HttpStatus.CREATED).body(
                RegisterResponse(verificationId = verificationId, verificationStatus = VerificationStatus.PENDING_VERIFICATION),
            )
        }
    }

    override fun login(loginBody: LoginBody): ResponseEntity<AuthResponse> {
        val auth = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(loginBody.email, loginBody.password),
            )
        } catch (ex: AuthenticationException) {
            throw UnauthorizedException("Invalid credentials")
        }

        val user = userRepository.findByEmail(loginBody.email)!!
        if (user.emailVerifiedAt == null) {
            val verificationId = emailVerificationService.ensureVerificationForLogin(user.id!!, user.email)
            throw EmailNotVerifiedException(verificationId)
        }

        val userDetails = auth.principal as UserDetails
        establishSession(userDetails)

        if (loginBody.rememberMe == true) {
            rememberMeServices.loginSuccessForced(request, response, auth)
        }

        return ResponseEntity.ok(user.toAuthResponse(userDetails.authorities.mapNotNull { it.authority }))
    }

    @PreAuthorize("isAuthenticated()")
    override fun logout(): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication!!
        rememberMeServices.logout(request, response, auth)
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('VIEW_PROFILE')")
    override fun getMe(): ResponseEntity<AuthResponse> {
        val auth = SecurityContextHolder.getContext().authentication!!
        val user = userRepository.findByEmail(auth.name)!!
        return ResponseEntity.ok(user.toAuthResponse(auth.authorities.mapNotNull { it.authority }))
    }

    @PreAuthorize("hasAuthority('EDIT_PROFILE')")
    override fun updateMe(updateMeBody: UpdateMeBody): ResponseEntity<AuthResponse> {
        val auth = SecurityContextHolder.getContext().authentication!!
        val user = userRepository.findByEmail(auth.name)!!
        val merged = user.copy(
            timezone = updateMeBody.timezone ?: user.timezone,
            pushoverUserKey = updateMeBody.pushoverUserKey ?: user.pushoverUserKey,
            pushoverApiToken = updateMeBody.pushoverApiToken ?: user.pushoverApiToken,
            pushoverOverrideEnabled = updateMeBody.pushoverOverrideEnabled ?: user.pushoverOverrideEnabled,
        )

        if (merged.pushoverOverrideEnabled && (merged.pushoverApiToken.isNullOrBlank() || merged.pushoverUserKey.isNullOrBlank())) {
            throw BadRequestException("Pushover app token and user key are required to enable the Pushover override")
        }

        val updated = userRepository.save(merged)
        return ResponseEntity.ok(updated.toAuthResponse(auth.authorities.mapNotNull { it.authority }))
    }

    override fun resendVerification(resendVerificationBody: ResendVerificationBody): ResponseEntity<Unit> {
        turnstileService.verify(resendVerificationBody.turnstileToken)
        emailVerificationService.resendVerification(resendVerificationBody.email)
        return ResponseEntity.accepted().build()
    }

    override fun verifyEmail(verifyEmailBody: VerifyEmailBody): ResponseEntity<AuthResponse> {
        val outcome = emailVerificationService.consumeVerification(verifyEmailBody.verificationId, verifyEmailBody.code)
        return when (outcome.result) {
            VerifyResult.SUCCESS -> {
                val user = outcome.user!!
                val userDetails = userDetailsService.loadUserByUsername(user.email)
                establishSession(userDetails)
                ResponseEntity.ok(user.toAuthResponse(userDetails.authorities.mapNotNull { it.authority }))
            }
            VerifyResult.ATTEMPTS_EXCEEDED -> throw EmailVerificationException.AttemptsExceeded()
            VerifyResult.WRONG_CODE -> throw EmailVerificationException.CodeInvalid()
            VerifyResult.INVALID_OR_EXPIRED -> throw EmailVerificationException.LinkExpired()
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

    @PreAuthorize("hasAuthority('EDIT_PROFILE')")
    override fun changePassword(changePasswordBody: ChangePasswordBody): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication!!
        val user = userRepository.findByEmail(auth.name)!!

        if (!passwordEncoder.matches(changePasswordBody.currentPassword, user.passwordHash)) {
            throw BadRequestException("Current password is incorrect")
        }
        if (changePasswordBody.newPassword == changePasswordBody.currentPassword) {
            throw BadRequestException("New password must differ from current password")
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

    override fun forgotPassword(forgotPasswordBody: ForgotPasswordBody): ResponseEntity<Unit> {
        if (!forgotPasswordRateLimiter.tryAcquire(request.remoteAddr)) {
            throw RateLimitExceededException()
        }
        passwordResetService.forgotPassword(forgotPasswordBody.email)
        return ResponseEntity.accepted().build()
    }

    override fun resetPassword(resetPasswordBody: ResetPasswordBody): ResponseEntity<Unit> =
        when (passwordResetService.consumeReset(resetPasswordBody.resetId, resetPasswordBody.token, resetPasswordBody.newPassword)) {
            ResetResult.SUCCESS -> ResponseEntity.noContent().build()
            ResetResult.INVALID_OR_EXPIRED -> throw PasswordResetException.InvalidOrExpired()
            ResetResult.PASSWORD_TOO_WEAK -> throw PasswordResetException.TooWeak()
            ResetResult.PASSWORD_SAME_AS_CURRENT -> throw PasswordResetException.SameAsCurrent()
        }

    private fun UserEntity.toAuthResponse(authorities: List<String>) =
        AuthResponse(
            id = id!!,
            email = email,
            timezone = timezone,
            verificationStatus = VerificationStatus.VERIFIED,
            pushoverUserKey = pushoverUserKey,
            pushoverApiToken = pushoverApiToken,
            pushoverOverrideEnabled = pushoverOverrideEnabled,
            permissions = authorities.mapNotNull { name -> runCatching { Permission.valueOf(name) }.getOrNull() },
        )
}
