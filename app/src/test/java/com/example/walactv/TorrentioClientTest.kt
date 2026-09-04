package com.example.walactv.data.remote.torrent

import com.example.walactv.data.model.filterByPreferredLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentioClientTest {

    private fun hash(seed: Int): String = buildString {
        val base = seed.toString(16)
        while (length < 40) append(base)
    }.substring(0, 40)

    private fun normalize(title: String) =
        TorrentioClient.normalize(hash(7), "1080p", title, null)

    @Test
    fun `bandera España real se detecta como ES`() {
        val stream = normalize("Coco.(2017).HC.WEB-DL.Line.Latin.Spanish.YG\n👤 0 💾 1.4 GB ⚙️ 1337x\n🇪🇸")
        assertNotNull(stream)
        assertEquals(listOf("ES"), stream!!.languages)
        assertEquals("ES", stream.language)
    }

    @Test
    fun `release dual GB ES detecta ambos idiomas`() {
        val stream = normalize(
            "Coco [MicroHD 1080p][AC3 5.1-Castellano-AC3 5.1-Ingles+Subs][ES-EN]\n👤 1 💾 4.97 GB ⚙️ Wolfmax4k\n🇬🇧 / 🇪🇸",
        )
        assertNotNull(stream)
        // Mismo orden canonico que el scrapper: ES primero (dict de insercion)
        assertEquals(listOf("ES", "EN"), stream!!.languages)
    }

    @Test
    fun `bandera GB sola detecta EN`() {
        val stream = normalize("Movie 2023 1080p BluRay x264\n👤 10 💾 2 GB ⚙️ YTS\n🇬🇧")
        assertNotNull(stream)
        assertEquals(listOf("EN"), stream!!.languages)
    }

    @Test
    fun `sin marcadores asume EN`() {
        val stream = normalize("The Runner 2026 1080p WEBRip x264\n👤 100 💾 1.58 GB ⚙️ YTS")
        assertNotNull(stream)
        assertEquals(listOf("EN"), stream!!.languages)
    }

    @Test
    fun `castellano en texto detecta ES`() {
        val stream = normalize("Pelicula 2023 1080p Castellano WEBRip\n👤 5 💾 1.5 GB ⚙️ Comando")
        assertNotNull(stream)
        assertEquals(listOf("ES"), stream!!.languages)
    }

    @Test
    fun `marcador corchetes detecta idioma`() {
        val stream = normalize("Serie S01E01 1080p [ES]\n👤 3 💾 900 MB ⚙️ Comando")
        assertNotNull(stream)
        assertEquals(listOf("ES"), stream!!.languages)
    }

    @Test
    fun `bandera extranjera sin idioma conocido descarta el stream`() {
        assertNull(normalize("Pellicola 2023 1080p BluRay\n👤 8 💾 3 GB ⚙️ SpA\n🇮🇹"))
    }

    @Test
    fun `bandera latino excluida`() {
        assertNull(normalize("Coco.2017.1080p.Eng-Spa(Latino)\n👤 1 💾 1.68 GB ⚙️ 1337x\n🇬🇧 / 🇪🇸 / 🇲🇽"))
    }

    @Test
    fun `filtro por idioma preferido mantiene dual y excluye ajenos`() {
        val es = normalize("A 2023 Castellano\n👤 5 💾 1 GB ⚙️ C\n🇪🇸")!!
        val dual = normalize("B 2023 Dual\n👤 6 💾 2 GB ⚙️ W\n🇬🇧 / 🇪🇸")!!
        val en = normalize("C 2023 WEBRip\n👤 100 💾 1 GB ⚙️ YTS")!!
        val filtered = listOf(es, dual, en).filterByPreferredLanguage("ES")
        assertEquals(listOf(es, dual), filtered)
        assertTrue(listOf(es, dual, en).filterByPreferredLanguage("EN").containsAll(listOf(dual, en)))
    }

    @Test
    fun `sin idioma declarado usa language legado`() {
        val legacy = TorrentioClient.normalize(hash(7), "1080p", "D 2023\n👤 9 💾 1 GB ⚙️ YTS", null)!!
            .copy(languages = emptyList())
        assertEquals(legacy, listOf(legacy).filterByPreferredLanguage("EN").single())
    }
}
