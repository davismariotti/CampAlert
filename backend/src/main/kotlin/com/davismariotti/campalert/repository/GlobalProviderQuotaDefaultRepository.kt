package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GlobalProviderQuotaDefault
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.repository.CrudRepository

interface GlobalProviderQuotaDefaultRepository : CrudRepository<GlobalProviderQuotaDefault, Provider>
