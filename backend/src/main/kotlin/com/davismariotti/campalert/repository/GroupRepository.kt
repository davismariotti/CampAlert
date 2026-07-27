package com.davismariotti.campalert.repository

import com.davismariotti.campalert.model.Group
import org.springframework.data.repository.CrudRepository

interface GroupRepository : CrudRepository<Group, Long> {
    fun findByGroupName(groupName: String): Group?
}
