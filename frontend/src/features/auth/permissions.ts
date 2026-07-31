import type { AuthUser } from './authState'
import type { Permission } from '../../api/generated/types.gen'

export function hasPermission(user: AuthUser | null, permission: Permission): boolean {
  return user?.permissions.includes(permission) ?? false
}

export function isAdmin(user: AuthUser | null): boolean {
  return hasPermission(user, 'VIEW_ADMIN_DASHBOARD')
}

export function canManageInvites(user: AuthUser | null): boolean {
  return hasPermission(user, 'MANAGE_INVITES')
}
