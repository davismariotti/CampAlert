package com.davismariotti.campalert.service.sms

data class AwaitingContext(
    val intent: String,
    val requestIds: List<Int>,
)
