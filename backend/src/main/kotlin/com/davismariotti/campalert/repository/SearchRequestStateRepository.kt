package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.SearchRequestState
import org.springframework.data.jpa.repository.JpaRepository

interface SearchRequestStateRepository : JpaRepository<SearchRequestState, Long>
