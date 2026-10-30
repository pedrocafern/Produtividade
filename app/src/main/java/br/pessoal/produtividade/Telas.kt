package br.pessoal.produtividade

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private fun formatar(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", PT_BR).format(Date(ms))

@Composable
private fun Cabecalho(titulo: String, aoVoltar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = aoVoltar) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
        }
        Text(titulo, style = MaterialTheme.typography.titleLarge)
    }
}

// ----------------------------------------------------------------------
// Módulo 1: Anotações
// ----------------------------------------------------------------------
@Composable
fun NotasScreen(vm: NotasViewModel = viewModel()) {
    val notas by vm.notas.collectAsState()
    val scope = rememberCoroutineScope()
    var atualId by rememberSaveable { mutableStateOf(0L) }
    var titulo by rememberSaveable { mutableStateOf("") }
    var texto by rememberSaveable { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }
    var falhaSalvar by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }

    fun limpar() {
        atualId = 0L
        titulo = ""
        texto = ""
        erro = false
        falhaSalvar = false
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { limpar() }) { Text("Nova") }
            Button(onClick = {
                if (titulo.isBlank()) {
                    erro = true
                } else {
                    erro = false
                    falhaSalvar = false
                    scope.launch {
                        salvando = true
                        try { atualId = vm.salvar(atualId, titulo.trim(), texto) }
                        catch (_: Exception) { falhaSalvar = true }
                        finally { salvando = false }
                    }
                }
            }, enabled = !salvando) { Text(if (salvando) "Salvando…" else "Salvar") }
            OutlinedButton(onClick = {
                notas.firstOrNull { it.id == atualId }?.let { vm.excluir(it) }
                limpar()
            }) { Text("Excluir") }
        }
        OutlinedTextField(
            value = titulo,
            onValueChange = { titulo = it },
            label = { Text("Título") },
            isError = erro,
            supportingText = {
                if (erro) Text("Informe um título")
                else if (falhaSalvar) Text("Não foi possível salvar. Tente novamente.")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            label = { Text("Texto") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(notas, key = { it.id }) { nota ->
                ListItem(
                    headlineContent = { Text(nota.titulo) },
                    supportingContent = { Text(formatar(nota.atualizado)) },
                    modifier = Modifier.clickable {
                        atualId = nota.id
                        titulo = nota.titulo
                        texto = nota.texto
                    },
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Módulo 2: Estudos Bíblicos (leitura ACF + anotações por versículo)
// ----------------------------------------------------------------------
@Composable
fun BibliaScreen(vm: BibliaViewModel = viewModel()) {
    val livros by vm.livros.collectAsState()
    val falhaBiblia by vm.erro.collectAsState()
    var livroIdx by rememberSaveable { mutableStateOf(-1) }
    var capIdx by rememberSaveable { mutableStateOf(-1) }
    var versiculo by rememberSaveable { mutableStateOf(0) }
    var anotacao by rememberSaveable { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }

    BackHandler(enabled = livroIdx >= 0) {
        if (capIdx >= 0) {
            capIdx = -1
            versiculo = 0
        } else {
            livroIdx = -1
        }
    }

    val lista = livros
    when {
        lista == null && falhaBiblia -> Text("Não foi possível carregar o arquivo bíblico.", modifier = Modifier.padding(16.dp))
        lista == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        lista.isEmpty() -> Text(
            "Arquivo app/src/main/assets/acf.json não encontrado ou inválido. " +
                "Adicione o texto da ACF nesse caminho e reinstale o aplicativo.",
            modifier = Modifier.padding(16.dp),
        )

        livroIdx < 0 -> LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(lista, key = { _, livro -> livro.nome }) { i, livro ->
                ListItem(
                    headlineContent = { Text(livro.nome) },
                    modifier = Modifier.clickable { livroIdx = i },
                )
            }
        }

        capIdx < 0 -> {
            val livro = lista[livroIdx]
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Cabecalho(livro.nome) { livroIdx = -1 }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(60.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(livro.capitulos.size) { c ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VerdeEscuro)
                                .clickable { capIdx = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${c + 1}",
                                color = Creme,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        else -> {
            val livro = lista[livroIdx]
            val versos = livro.capitulos[capIdx]
            val notas by remember(livro.nome, capIdx) { vm.notas(livro.nome, capIdx + 1) }
                .collectAsState(initial = emptyList())

            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Cabecalho("${livro.nome} ${capIdx + 1}") {
                    capIdx = -1
                    versiculo = 0
                }
                LazyColumn(Modifier.weight(0.55f)) {
                    itemsIndexed(versos, key = { i, _ -> i }) { i, verso ->
                        val selecionado = versiculo == i + 1
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selecionado) VerdeClaro else Color.Transparent)
                                .clickable {
                                    versiculo = i + 1
                                    erro = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            val corTexto = if (selecionado) Creme else MaterialTheme.colorScheme.onSurface
                            Text("${i + 1}", color = corTexto, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            Text(verso, color = corTexto)
                        }
                    }
                }
                OutlinedTextField(
                    value = anotacao,
                    onValueChange = { anotacao = it },
                    label = {
                        Text(
                            if (versiculo > 0) "Anotação sobre o versículo $versiculo"
                            else "Toque em um versículo para anotar",
                        )
                    },
                    isError = erro,
                    supportingText = { if (erro) Text("Selecione um versículo primeiro") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    if (versiculo == 0) {
                        erro = true
                    } else if (anotacao.isNotBlank()) {
                        vm.adicionar(livro.nome, capIdx + 1, versiculo, anotacao.trim())
                        anotacao = ""
                    }
                }) { Text("Salvar anotação") }
                LazyColumn(Modifier.weight(0.45f)) {
                    items(notas, key = { it.id }) { nota ->
                        ListItem(
                            headlineContent = { Text(nota.texto) },
                            supportingContent = { Text("Versículo ${nota.versiculo}") },
                            trailingContent = {
                                IconButton(onClick = { vm.excluir(nota) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Módulo 3: Relógio com Calendário
// ----------------------------------------------------------------------
@Composable
fun RelogioScreen() {
    val agora by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    var deslocamento by rememberSaveable { mutableStateOf(0) }

    val hora = SimpleDateFormat("HH:mm:ss", PT_BR).format(Date(agora))
    val data = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", PT_BR).format(Date(agora))

    val hoje = Calendar.getInstance().apply { timeInMillis = agora }
    val ref = Calendar.getInstance().apply {
        timeInMillis = agora
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, deslocamento)
    }
    val tituloMes = SimpleDateFormat("MMMM 'de' yyyy", PT_BR).format(ref.time)
        .replaceFirstChar { it.uppercase() }
    val vazios = ref.get(Calendar.DAY_OF_WEEK) - 1 // semana começa no domingo
    val diasNoMes = ref.getActualMaximum(Calendar.DAY_OF_MONTH)
    val semanas = (List(vazios) { 0 } + (1..diasNoMes)).chunked(7).map { it + List(7 - it.size) { 0 } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(hora, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(data, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        PomodoroSecao()
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { deslocamento-- }) { Text("<") }
            Text(
                tituloMes,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(200.dp),
            )
            OutlinedButton(onClick = { deslocamento++ }) { Text(">") }
        }
        Row {
            "DSTQQSS".forEach {
                Text(
                    it.toString(),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(44.dp).padding(vertical = 4.dp),
                )
            }
        }
        semanas.forEach { semana ->
            Row {
                semana.forEach { dia ->
                    val destaque = dia != 0 &&
                        dia == hoje.get(Calendar.DAY_OF_MONTH) &&
                        ref.get(Calendar.MONTH) == hoje.get(Calendar.MONTH) &&
                        ref.get(Calendar.YEAR) == hoje.get(Calendar.YEAR)
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(
                                if (destaque) VerdeEscuro else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dia != 0) Text(dia.toString(), color = if (destaque) Creme else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Módulo 4: Pomodoro
// ----------------------------------------------------------------------
@Composable
private fun PomodoroSecao(vm: PomodoroViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val hoje by vm.hoje.collectAsState()
    val segundos = (ui.restanteMs + 999) / 1000
    val tempo = "%02d:%02d".format(segundos / 60, segundos % 60)
    val context = LocalContext.current
    val permissaoNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.iniciar() }
    fun iniciar() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.iniciar()
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(ui.modo.rotulo, style = MaterialTheme.typography.titleLarge)
        Text(tempo, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { iniciar() }, enabled = !ui.rodando) { Text("Iniciar") }
            OutlinedButton(onClick = { vm.pausar() }, enabled = ui.rodando) { Text("Pausar") }
            OutlinedButton(onClick = { vm.reiniciar() }) { Text("Reiniciar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Modo.entries.forEach { modo ->
                FilterChip(
                    selected = ui.modo == modo,
                    onClick = { vm.definirModo(modo) },
                    label = { Text(modo.rotulo) },
                )
            }
        }
        Text("Pomodoros concluídos hoje: $hoje")
    }
}
