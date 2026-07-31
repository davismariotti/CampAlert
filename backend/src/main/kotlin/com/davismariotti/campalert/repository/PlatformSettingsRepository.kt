package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PlatformSettings
import org.springframework.data.repository.CrudRepository

interface PlatformSettingsRepository : CrudRepository<PlatformSettings, Short>
