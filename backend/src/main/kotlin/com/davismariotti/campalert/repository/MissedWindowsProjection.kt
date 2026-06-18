package com.davismariotti.campalert.repository

interface MissedWindowsProjection {
    fun getRequestId(): Long

    fun getMissedCount(): Long
}
