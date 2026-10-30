package br.pessoal.produtividade.data

import android.content.Context
import android.util.JsonReader
import java.io.BufferedReader
import java.io.InputStreamReader

data class Livro(val nome: String, val capitulos: List<List<String>>)

/**
 * Lê app/src/main/assets/acf.json.
 * Formato: lista de livros; cada livro tem "chapters" (lista de capítulos, cada um
 * com a lista de versículos em texto) e, opcionalmente, "name" ou "abbrev".
 */
object Biblia {
    fun carregar(context: Context): List<Livro> = context.assets.open("acf.json").use { input ->
        val buffered = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        buffered.mark(1)
        if (buffered.read() != '\uFEFF'.code) buffered.reset()
        val reader = JsonReader(buffered)
        reader.isLenient = true
        reader.use { json ->
            val livros = mutableListOf<Livro>()
            json.beginArray()
            while (json.hasNext()) {
                var nome: String? = null
                var abreviacao: String? = null
                var capitulos: List<List<String>>? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "name" -> nome = json.nextString()
                        "abbrev" -> abreviacao = json.nextString()
                        "chapters" -> capitulos = lerCapitulos(json)
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                livros += Livro(
                    nome?.takeIf(String::isNotBlank) ?: abreviacao?.takeIf(String::isNotBlank)
                        ?: "Livro ${livros.size + 1}",
                    requireNotNull(capitulos) { "Livro sem capítulos no acf.json" },
                )
            }
            json.endArray()
            livros
        }
    }

    private fun lerCapitulos(json: JsonReader): List<List<String>> = buildList {
        json.beginArray()
        while (json.hasNext()) {
            val versos = buildList {
                json.beginArray()
                while (json.hasNext()) add(json.nextString())
                json.endArray()
            }
            add(versos)
        }
        json.endArray()
    }
}
