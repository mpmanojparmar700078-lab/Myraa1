package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MessageEntity::class,
        UserPreferenceEntity::class,
        InteractionHistoryEntity::class,
        ExperienceEntity::class,
        LearnedSkillEntity::class,
        FailedStrategyEntity::class,
        LearnedFactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MyraDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun interactionHistoryDao(): InteractionHistoryDao
    abstract fun experienceDao(): ExperienceDao
    abstract fun learnedSkillDao(): LearnedSkillDao
    abstract fun failedStrategyDao(): FailedStrategyDao
    abstract fun learnedFactDao(): LearnedFactDao

    companion object {
        @Volatile
        private var INSTANCE: MyraDatabase? = null

        fun getDatabase(context: Context): MyraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MyraDatabase::class.java,
                    "myra_assistant_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
