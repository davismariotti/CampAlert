package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.PollTargetId
import com.davismariotti.campalert.model.PollTargetState
import org.springframework.data.repository.CrudRepository

interface PollTargetStateRepository : CrudRepository<PollTargetState, PollTargetId>
