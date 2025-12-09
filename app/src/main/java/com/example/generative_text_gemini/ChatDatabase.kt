package com.example.generative_text_gemini

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ▼ 1. チャットルーム（セッション）の定義
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ▼ 2. メッセージの定義（どのセッションに属するかを持つ）
@Entity(tableName = "chat_messages")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Long, // ★これが「どのチャットルームか」の紐付けID
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ▼ 3. アクセス方法 (DAO)
@Dao
interface ChatDao {
    // セッション一覧を取得（新しい順）
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    // 特定のセッションのメッセージだけを取得
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFromSession(sessionId: Long): Flow<List<ChatEntity>>

    @Insert
    suspend fun insertSession(session: ChatSession): Long // 作ったIDを返す

    @Insert
    suspend fun insertMessage(message: ChatEntity)

    // セッション削除（関連メッセージも消すクエリ）
    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Long)
}

// ▼ 4. データベース本体
@Database(entities = [ChatSession::class, ChatEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration() // 古いデータが壊れても強制的に作り直す設定
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}