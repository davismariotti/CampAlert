package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

/** Failures classifying a permit ID against the search type an endpoint expects. */
sealed class PermitClassificationException(
    code: String,
    message: String
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message) {
    class UnsupportedPermitType : PermitClassificationException("PERMIT_TYPE_NOT_SUPPORTED", "Permit reservation mechanism is not supported")

    class TypeMismatch(
        actual: String
    ) : PermitClassificationException("PERMIT_TYPE_MISMATCH", "Permit is classified as $actual, not the type this endpoint requires")

    class SearchTypeMismatch(
        actual: String
    ) : PermitClassificationException("PERMIT_TYPE_MISMATCH", "Permit is classified as $actual, not the requested searchType")
}
