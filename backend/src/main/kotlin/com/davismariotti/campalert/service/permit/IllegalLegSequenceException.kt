package com.davismariotti.campalert.service.permit

import com.davismariotti.campalert.exception.ApiException
import org.springframework.http.HttpStatus

class IllegalLegSequenceException(
    legIndex: Int,
    divisionId: String
) : ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "ILLEGAL_LEG_SEQUENCE",
        "Leg $legIndex (division $divisionId) is not a legal continuation of the previous leg",
    )
