package br.pessoal.produtividade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Cores do design (Figma). Cores extraídas do SVG do Figma.
val VerdeEscuro = Color(0xFF164739)
val VerdeClaro = Color(0xFF42635A)
val Creme = Color(0xFFEEEFE9)

private val FundoDegrade = Brush.verticalGradient(
    0f to VerdeEscuro,
    0.26f to VerdeEscuro,
    0.75f to VerdeClaro,
    1f to VerdeClaro,
)

// Medidas do quadro original do Figma (428 x 926)
private const val ALTURA_QUADRO = 926f

enum class Modulo(val rotulo: String, val icone: ImageVector) {
    NOTAS("Notas", Icons.Default.Edit),
    RELOGIO("Relógio", Icons.Default.AccessTime),
    BIBLIA("Bíblia", Icons.AutoMirrored.Filled.MenuBook),
}

/**
 * Abertura (5 s) seguida da tela de ferramentas, com transição "Push" para cima
 * em 950 ms. O ícone (elemento em comum) se desloca e aumenta de 90 para 110.
 */
@Composable
fun TelaEntrada(
    jaEntrou: Boolean,
    aoConcluirEntrada: () -> Unit,
    aoAbrir: (Modulo) -> Unit,
) {
    val progresso = remember { Animatable(if (jaEntrou) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!jaEntrou) {
            delay(5000)
            progresso.animateTo(1f, tween(950, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
            aoConcluirEntrada()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(FundoDegrade)) {
        val altura = maxHeight
        val p = progresso.value
        // Em telas estreitas, os blocos diminuem para caber (margem de 16 dp e espaço de 40 dp).
        val bloco = minOf(90.dp, (maxWidth - 72.dp) / 2)

        // Ícone: centro em 50% da altura (abertura) -> 29,7% (ferramentas)
        val tamanho = lerp(90.dp, 110.dp, p)
        val centroY = lerp(altura * 0.5f, altura * (275f / ALTURA_QUADRO), p)
        Image(
            painter = painterResource(R.drawable.ic_tempacio),
            contentDescription = "Tempácio",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = centroY - tamanho / 2)
                .size(tamanho),
        )

        // Blocos: entram de baixo para cima junto com a transição
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = altura * (415f / ALTURA_QUADRO) + altura * (1f - p)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Bloco(Modulo.NOTAS, bloco, p >= 1f, aoAbrir)
                Bloco(Modulo.RELOGIO, bloco, p >= 1f, aoAbrir)
            }
            Bloco(Modulo.BIBLIA, bloco, p >= 1f, aoAbrir)
        }
    }
}

// Provisório: ícone e nome serão substituídos pelo desenho final do Figma.
@Composable
private fun Bloco(modulo: Modulo, lado: Dp, habilitado: Boolean, aoAbrir: (Modulo) -> Unit) {
    Column(
        modifier = Modifier
            .size(lado)
            .clip(RoundedCornerShape(22.dp))
            .background(Creme)
            .clickable(enabled = habilitado) { aoAbrir(modulo) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(modulo.icone, contentDescription = modulo.rotulo, tint = VerdeEscuro, modifier = Modifier.size(32.dp))
        Text(modulo.rotulo, color = VerdeEscuro, fontSize = 12.sp)
    }
}
