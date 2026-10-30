package br.pessoal.produtividade

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.pessoal.produtividade.data.AppDatabase
import br.pessoal.produtividade.data.Biblia
import br.pessoal.produtividade.data.Livro
import br.pessoal.produtividade.data.Nota
import br.pessoal.produtividade.data.NotaBiblica
import br.pessoal.produtividade.data.Pomodoro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class NotasViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).notaDao()
    val notas: StateFlow<List<Nota>> = dao.todas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun salvar(id: Long, titulo: String, texto: String): Long = try {
        dao.salvar(Nota(id = id, titulo = titulo, texto = texto, atualizado = System.currentTimeMillis()))
    } catch (e: Exception) { Log.e("Notas", "Falha ao salvar nota", e); throw e }
    fun excluir(nota: Nota) { viewModelScope.launch { try { dao.excluir(nota) } catch (e: Exception) { Log.e("Notas", "Falha ao excluir nota", e) } } }
}

class BibliaViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).notaBiblicaDao()
    private val _livros = MutableStateFlow<List<Livro>?>(null)
    val livros: StateFlow<List<Livro>?> = _livros.asStateFlow()
    val erro = MutableStateFlow(false)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try { _livros.value = Biblia.carregar(app) }
            catch (e: Exception) { Log.e("Biblia", "Falha ao carregar acf.json", e); erro.value = true }
        }
    }
    fun notas(livro: String, capitulo: Int) = dao.doCapitulo(livro, capitulo)
    fun adicionar(livro: String, capitulo: Int, versiculo: Int, texto: String) {
        viewModelScope.launch { try { dao.inserir(NotaBiblica(livro = livro, capitulo = capitulo, versiculo = versiculo, texto = texto, atualizado = System.currentTimeMillis())) } catch (e: Exception) { Log.e("Biblia", "Falha ao salvar anotação bíblica", e) } }
    }
    fun excluir(nota: NotaBiblica) { viewModelScope.launch { try { dao.excluir(nota) } catch (e: Exception) { Log.e("Biblia", "Falha ao excluir anotação bíblica", e) } } }
}

enum class Modo(val rotulo: String, val minutos: Int) { FOCO("Foco", 25), CURTA("Pausa curta", 5), LONGA("Pausa longa", 15) }
fun proximoModo(atual: Modo, ciclosDeFoco: Int): Modo = when {
    atual != Modo.FOCO -> Modo.FOCO
    ciclosDeFoco % 4 == 0 -> Modo.LONGA
    else -> Modo.CURTA
}

data class PomodoroUi(val modo: Modo = Modo.FOCO, val restanteMs: Long = Modo.FOCO.minutos * 60_000L, val rodando: Boolean = false, val ciclos: Int = 0)

/** Deadline monotônico enquanto o dispositivo não reinicia; wall clock permite detectar expiração. */
object PomodoroClock {
    fun restante(deadlineElapsed: Long, deadlineWall: Long, savedBoot: Int, currentBoot: Int, nowElapsed: Long, nowWall: Long, maxOnDifferentBoot: Long = Long.MAX_VALUE): Long =
        if (savedBoot == currentBoot) (deadlineElapsed - nowElapsed).coerceAtLeast(0L)
        else (deadlineWall - nowWall).coerceIn(0L, maxOnDifferentBoot.coerceAtLeast(0L))
}

class PomodoroViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).pomodoroDao()
    private val prefs = app.getSharedPreferences("pomodoro", 0)
    private val _ui = MutableStateFlow(restaurar(app))
    val ui: StateFlow<PomodoroUi> = _ui.asStateFlow()
    private val inicioDoDiaAtual = flow {
        while (true) {
            emit(inicioDoDia())
            val agora = System.currentTimeMillis()
            val proximaMeiaNoite = Calendar.getInstance().apply { timeInMillis = agora; add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            delay(minOf((proximaMeiaNoite - agora).coerceAtLeast(1_000L), 60_000L))
        }
    }.distinctUntilChanged()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val hoje: StateFlow<Int> = inicioDoDiaAtual.flatMapLatest { dao.desde(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        if (_ui.value.rodando) { PomodoroService.iniciar(app); iniciarAtualizacao() }
    }
    fun iniciar() {
        val atual = _ui.value
        if (atual.rodando) return
        val endElapsed = SystemClock.elapsedRealtime() + atual.restanteMs
        val endWall = System.currentTimeMillis() + atual.restanteMs
        salvar(atual.copy(rodando = true), endElapsed, endWall)
        _ui.value = atual.copy(rodando = true)
        PomodoroService.iniciar(getApplication())
        iniciarAtualizacao()
    }
    fun pausar() {
        val restante = restanteAtual()
        salvar(_ui.value.copy(restanteMs = restante, rodando = false), 0, 0)
        _ui.value = _ui.value.copy(restanteMs = restante, rodando = false)
        PomodoroService.parar(getApplication())
    }
    fun definirModo(modo: Modo) {
        PomodoroService.parar(getApplication())
        _ui.value = PomodoroUi(modo, modo.minutos * 60_000L, false, _ui.value.ciclos)
        salvar(_ui.value, 0, 0)
    }
    fun reiniciar() = definirModo(_ui.value.modo)
    private fun iniciarAtualizacao() { viewModelScope.launch {
        while (_ui.value.rodando) {
            if (!prefs.getBoolean("running", false)) {
                val paused = prefs.getLong("remaining", _ui.value.restanteMs)
                if (paused <= 0) concluir() else _ui.value = _ui.value.copy(restanteMs = paused, rodando = false)
                break
            }
            val left = restanteAtual()
            if (left <= 0) { concluir(); break }
            _ui.value = _ui.value.copy(restanteMs = left)
            delay(250)
        }
    } }
    private fun restanteAtual(): Long = PomodoroClock.restante(prefs.getLong("endElapsed", 0), prefs.getLong("endWall", 0), prefs.getInt("boot", -1), bootCount(getApplication()), SystemClock.elapsedRealtime(), System.currentTimeMillis(), prefs.getLong("remaining", _ui.value.modo.minutos * 60_000L))
    private fun concluir() {
        val atual = _ui.value
        PomodoroService.parar(getApplication())
        val ciclos = if (atual.modo == Modo.FOCO) atual.ciclos + 1 else atual.ciclos
        if (atual.modo == Modo.FOCO) viewModelScope.launch { dao.inserir(Pomodoro(concluidoEm = System.currentTimeMillis(), minutos = Modo.FOCO.minutos)) }
        val proximo = proximoModo(atual.modo, ciclos)
        _ui.value = PomodoroUi(proximo, proximo.minutos * 60_000L, false, ciclos)
        salvar(_ui.value, 0, 0)
    }
    private fun salvar(ui: PomodoroUi, endElapsed: Long, endWall: Long) {
        prefs.edit().putString("mode", ui.modo.name).putLong("remaining", ui.restanteMs).putBoolean("running", ui.rodando)
            .putInt("cycles", ui.ciclos).putLong("endElapsed", endElapsed).putLong("endWall", endWall)
            .putInt("boot", bootCount(getApplication())).apply()
    }
    private fun restaurar(app: Application): PomodoroUi {
        val mode = runCatching { Modo.valueOf(prefs.getString("mode", null) ?: "FOCO") }.getOrDefault(Modo.FOCO)
        val cycles = prefs.getInt("cycles", 0)
        if (!prefs.getBoolean("running", false)) return PomodoroUi(mode, prefs.getLong("remaining", mode.minutos * 60_000L), false, cycles)
        val left = PomodoroClock.restante(prefs.getLong("endElapsed", 0), prefs.getLong("endWall", 0), prefs.getInt("boot", -1), bootCount(app), SystemClock.elapsedRealtime(), System.currentTimeMillis(), prefs.getLong("remaining", mode.minutos * 60_000L))
        if (left <= 0) return PomodoroUi(mode, 0, true, cycles)
        return PomodoroUi(mode, left, true, cycles)
    }
    private fun bootCount(app: Application) = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(0)
    private fun inicioDoDia(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
}
