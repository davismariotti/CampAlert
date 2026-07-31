package com.davismariotti.campalert.model

/**
 * Granular permissions — these are the actual Spring Security `GrantedAuthority` values (`.name`),
 * checked via `@PreAuthorize("hasAuthority('...')")`. Groups (e.g. "Standard", "Admin") are pure
 * membership/bundling containers with no authority of their own; a group's `group_authorities` rows
 * grant it a set of these permissions, and a user's effective authorities are the union of every
 * permission granted by every group they belong to (see UserDetailsServiceImpl).
 */
enum class Permission {
    VIEW_PROFILE,
    EDIT_PROFILE,
    VIEW_SEARCH_REQUESTS,
    MANAGE_SEARCH_REQUESTS,
    VIEW_PHONE_NUMBERS,
    MANAGE_PHONE_NUMBERS,
    VIEW_CAMPGROUNDS,
    VIEW_PERMITS,

    // Admin-only
    VIEW_ADMIN_DASHBOARD,
    MANAGE_USERS,
    MANAGE_GLOBAL_SETTINGS,
    VIEW_AUDIT_LOG,
    MANAGE_USER_SEARCH_REQUESTS,
    MANAGE_INVITES,
}
