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

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN user_group_memberships m ON u.id = m.userId
        WHERE m.groupId = :groupId
        ORDER BY u.name ASC
    """)
    fun getUsersInGroup(groupId: Long): Flow<List<User>>

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN survey_answers sa ON u.id = sa.userId
        INNER JOIN user_group_memberships m ON u.id = m.userId
        WHERE m.groupId = :groupId
        GROUP BY u.id
        HAVING COUNT(sa.id) >= 15
    """)
    suspend fun getQuizEligibleUsersInGroup(groupId: Long): List<User>
}
