package com.gtky.app.data.dao

import androidx.room.*
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.UserGroupMembership
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: Long): Group?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group): Long

    @Delete
    suspend fun deleteGroup(group: Group)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addUserToGroup(membership: UserGroupMembership)

    @Delete
    suspend fun removeUserFromGroup(membership: UserGroupMembership)

    @Query("SELECT groupId FROM user_group_memberships WHERE userId = :userId")
    suspend fun getGroupIdsForUser(userId: Long): List<Long>

    @Query("SELECT groupId FROM user_group_memberships WHERE userId = :userId")
    fun getGroupIdsForUserFlow(userId: Long): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM user_group_memberships WHERE userId = :userId AND groupId = :groupId)")
    suspend fun isUserInGroup(userId: Long, groupId: Long): Boolean
}
