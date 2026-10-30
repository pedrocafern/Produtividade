package br.pessoal.produtividade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Tela inteira sob as barras do sistema, com ícones claros sobre o fundo verde.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = EsquemaDeCores) {
                Aplicativo()
            }
        }
    }
}

@Composable
fun Aplicativo() {
    // Estados no nível superior para não repetir a abertura ao voltar de um módulo.
    var jaEntrou by rememberSaveable { mutableStateOf(false) }
    var moduloAtual by rememberSaveable { mutableStateOf<String?>(null) }
    val modulo = moduloAtual?.let { Modulo.valueOf(it) }

    BackHandler(enabled = modulo != null) { moduloAtual = null }

    if (modulo == null) {
        TelaEntrada(
            jaEntrou = jaEntrou,
            aoConcluirEntrada = { jaEntrou = true },
            aoAbrir = { moduloAtual = it.name },
        )
    } else {
        TelaModulo(modulo) { moduloAtual = null }
    }
}

@Composable
private fun TelaModulo(modulo: Modulo, aoVoltar: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(VerdeEscuro).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Text(modulo.rotulo, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()) {
            when (modulo) {
                Modulo.NOTAS -> NotasScreen()
                Modulo.BIBLIA -> BibliaScreen()
                Modulo.RELOGIO -> RelogioScreen()
            }
        }
    }
}
