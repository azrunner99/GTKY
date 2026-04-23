package com.gtky.app.data.dao

import androidx.room.*
import com.gtky.app.data.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE name = :name LIMIT 1")
    suspend fun getUserByName(name: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Delete
    suspend fun deleteUser(user: User)

    @Query("UPDATE users SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE users SET photoPath = :path WHERE id = :id")
    suspend fun updatePhotoPath(id: Long, path: String?)

    @Query("UPDATE users SET photoPromptCount = photoPromptCount + 1 WHERE id = :id")
    suspend fun incrementPhotoPromptCount(id: Long)

    @Query("UPDATE users SET photoPromptOptOut = 1 WHERE id = :id")
    suspend fun setPhotoPromptOptOut(id: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN user_group_memberships m ON u.id = m.userId
        WHERE m.groupId = :groupId
        ORDER BY u.name ASC
    """)
    fun getUsersInGroup(groupId: Long): Flow<List<User>>

}
