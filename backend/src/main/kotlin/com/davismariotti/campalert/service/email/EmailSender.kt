package com.davismariotti.campalert.service.email

interface EmailSender {
    fun send(to: String, subject: String, htmlBody: String)
}
