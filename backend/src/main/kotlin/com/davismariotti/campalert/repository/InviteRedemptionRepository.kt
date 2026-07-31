package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.InviteRedemption
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface InviteRedemptionRepository : CrudRepository<InviteRedemption, UUID>
