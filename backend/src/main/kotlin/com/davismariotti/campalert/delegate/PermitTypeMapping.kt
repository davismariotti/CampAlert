package com.davismariotti.campalert.delegate

import com.davismariotti.campalert.api.model.PermitType
import com.davismariotti.campalert.model.SearchType

fun SearchType.toApi(): PermitType = PermitType.valueOf(this.name)

fun PermitType.toModel(): SearchType = SearchType.valueOf(this.name)
