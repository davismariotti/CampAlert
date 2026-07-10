package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PermitSearchRequestState
import org.springframework.data.jpa.repository.JpaRepository

interface PermitSearchRequestStateRepository : JpaRepository<PermitSearchRequestState, Long>
