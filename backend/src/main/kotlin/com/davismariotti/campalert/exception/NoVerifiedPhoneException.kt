package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

/** Thrown by any search-request creation endpoint that requires a verified phone on file first. */
class NoVerifiedPhoneException :
    ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "NO_VERIFIED_PHONE",
        "A verified phone number is required to create a search request.",
    )
