package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.UserQuotaOverride
import org.springframework.data.repository.CrudRepository

interface UserQuotaOverrideRepository : CrudRepository<UserQuotaOverride, Long>
