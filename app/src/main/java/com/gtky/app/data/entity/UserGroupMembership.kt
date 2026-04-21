package com.gtky.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "user_group_memberships",
    primaryKeys = ["userId", "groupId"],
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class UserGroupMembership(
    val userId: Long,
    val groupId: Long,
    val joinedAt: Long = System.currentTimeMillis()
)
