package com.example.walactv

import com.example.walactv.data.util.isSeasonPackTitle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentTitleTest {

    @Test
    fun detectsSeasonPacks() {
        assertTrue(isSeasonPackTitle("Silo.S03.2160p.ATVP.WEB-DL.DDP5.1.H.264-NTb 8.7 GB"))
        assertTrue(isSeasonPackTitle("Breaking Bad Season 2 COMPLETE 1080p"))
        assertTrue(isSeasonPackTitle("Silo S03 Pack 2160p"))
    }

    @Test
    fun singleEpisodesAreNotPacks() {
        assertFalse(isSeasonPackTitle("Silo S03E03 A Dark Web 2160p WEB-DL 8.4 GB"))
        assertFalse(isSeasonPackTitle("Silo [4k 2160p][Cap.303]] 8.6 GB"))
        assertFalse(isSeasonPackTitle("Silo S03E03 1080p WEB 3.7 GB"))
        assertFalse(isSeasonPackTitle("Dune Part Two 2024 2160p"))
        assertFalse(isSeasonPackTitle(null))
        assertFalse(isSeasonPackTitle(""))
    }
}
