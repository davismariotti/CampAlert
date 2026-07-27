package com.davismariotti.campalert.model

/**
 * Well-known values stored in `search_request_state.pause_reason` / `permit_search_request_state.pause_reason`
 * (plain `varchar`, no DB check constraint — see [ResourceReconciliationService]'s precedence rule
 * for how these interact). `.name` is the literal string persisted.
 */
enum class PauseReason {
    NO_VERIFIED_PHONE,
    QUOTA_EXCEEDED,
    PROVIDER_DISABLED,
}
