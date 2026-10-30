package br.pessoal.produtividade.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notas")
data class Nota(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val texto: String,
    val atualizado: Long,
)

@Entity(tableName = "notas_biblicas")
data class NotaBiblica(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val livro: String,
    val capitulo: Int,
    val versiculo: Int,
    val texto: String,
    val atualizado: Long,
)

@Entity(tableName = "pomodoros")
data class Pomodoro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val concluidoEm: Long,
    val minutos: Int,
)

@Dao
interface NotaDao {
    @Query("SELECT * FROM notas ORDER BY atualizado DESC")
    fun todas(): Flow<List<Nota>>

    // Com id = 0 insere; com id existente substitui. Retorna sempre o id da linha.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(nota: Nota): Long

    @Delete
    suspend fun excluir(nota: Nota)
}

@Dao
interface NotaBiblicaDao {
    @Query("SELECT * FROM notas_biblicas WHERE livro = :livro AND capitulo = :capitulo ORDER BY versiculo, id")
    fun doCapitulo(livro: String, capitulo: Int): Flow<List<NotaBiblica>>

    @Insert
    suspend fun inserir(nota: NotaBiblica)

    @Delete
    suspend fun excluir(nota: NotaBiblica)
}

@Dao
interface PomodoroDao {
    @Insert
    suspend fun inserir(pomodoro: Pomodoro)

    @Query("SELECT COUNT(*) FROM pomodoros WHERE concluidoEm >= :inicioDoDia")
    fun desde(inicioDoDia: Long): Flow<Int>
}

@Database(
    entities = [Nota::class, NotaBiblica::class, Pomodoro::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notaDao(): NotaDao
    abstract fun notaBiblicaDao(): NotaBiblicaDao
    abstract fun pomodoroDao(): PomodoroDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "produtividade.db",
                ).build().also { instancia = it }
            }
    }
}
