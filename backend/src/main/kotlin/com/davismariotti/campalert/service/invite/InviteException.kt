package com.davismariotti.campalert.service.invite

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

/**
 * Failure outcomes of validating/redeeming an invite at registration time. Messages are worded
 * per whether the invite was email-tied or a public link (isEmailInvite), per the requirement that
 * users be told to request a new invitation (email) vs. a new link (public), not a generic error.
 */
sealed class InviteException(
    code: String,
    message: String
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message) {
    class Required : InviteException("INVITE_REQUIRED", "Registration currently requires an invitation")

    class InvalidOrExpired(
        isEmailInvite: Boolean
    ) : InviteException(
            "INVITE_INVALID_OR_EXPIRED",
            if (isEmailInvite) {
                "This invitation is invalid or has expired. Please request a new invitation."
            } else {
                "This invite link is invalid or has expired. Please request a new link."
            },
        )

    class Exhausted(
        isEmailInvite: Boolean
    ) : InviteException(
            "INVITE_EXHAUSTED",
            if (isEmailInvite) {
                "This invitation has already been used. Please request a new invitation."
            } else {
                "This invite link has reached its redemption limit. Please request a new link."
            },
        )

    class Deactivated(
        isEmailInvite: Boolean
    ) : InviteException(
            "INVITE_DEACTIVATED",
            if (isEmailInvite) {
                "This invitation is no longer valid. Please request a new invitation."
            } else {
                "This invite link is no longer valid. Please request a new link."
            },
        )

    class EmailMismatch : InviteException("INVITE_EMAIL_MISMATCH", "This invitation was sent to a different email address")
}
