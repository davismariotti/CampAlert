package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.GlobalProviderAccess
import com.davismariotti.campalert.provider.Provider
import org.springframework.data.repository.CrudRepository

interface GlobalProviderAccessRepository : CrudRepository<GlobalProviderAccess, Provider>
