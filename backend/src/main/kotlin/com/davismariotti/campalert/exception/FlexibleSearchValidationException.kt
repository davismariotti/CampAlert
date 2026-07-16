package com.davismariotti.campalert.exception

import org.springframework.http.HttpStatus

/** Validation failures for a campground search request's flexible `latestStartDay` range. */
sealed class FlexibleSearchValidationException(
    code: String,
    message: String
) : ApiException(HttpStatus.BAD_REQUEST, code, message) {
    class LatestStartDayTooEarly : FlexibleSearchValidationException("LATEST_START_DAY_TOO_EARLY", "latestStartDay must be on or after startDay.")

    class Unsupported(
        providerName: String
    ) : FlexibleSearchValidationException("FLEXIBLE_SEARCH_UNSUPPORTED", "Flexible search is not supported for $providerName.")

    class RangeTooWide(
        rangeWidthDays: Long,
        maxRangeWidthDays: Int,
        providerName: String
    ) : FlexibleSearchValidationException(
            "SEARCH_RANGE_TOO_WIDE",
            "The flexible date range ($rangeWidthDays days) exceeds the maximum of $maxRangeWidthDays days for $providerName.",
        )
}
