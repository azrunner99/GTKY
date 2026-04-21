package com.gtky.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gtky.app.data.dao.*
import com.gtky.app.data.entity.*

@Database(
    entities = [
        User::class,
        Group::class,
        UserGroupMembership::class,
        SurveyQuestion::class,
        SurveyAnswer::class,
        QuizResult::class,
        AppConfig::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GTKYDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun surveyQuestionDao(): SurveyQuestionDao
    abstract fun surveyAnswerDao(): SurveyAnswerDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        @Volatile private var instance: GTKYDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE survey_questions ADD COLUMN questionTemplateEs TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE survey_questions ADD COLUMN optionsJsonEs TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): GTKYDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GTKYDatabase::class.java,
                    "gtky_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
