package com.wetune

import com.wetune.sources.Track
import com.wetune.youtube.Sync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The local-first half of sync (signed out, so nothing is sent): changes apply at once and stay queued. */
class SyncTest {
    private fun song(id: String) = Track("yt", id, "Song $id", "Artist")

    @Test
    fun likesApplyInstantlyAndQueue() {
        val before = Sync.pending
        Sync.setLiked(song("a"), true)
        assertTrue(Sync.isLiked(song("a")))
        assertEquals(before + 1, Sync.pending)
        Sync.setLiked(song("a"), false)
        assertFalse(Sync.isLiked(song("a")))
    }

    @Test
    fun offlinePlaylistOverlayAndDeleteSendNothing() {
        val id = Sync.createPlaylist("Road trip")
        assertTrue(Sync.playlists.any { it.browseId == id })
        Sync.addToPlaylist(id, song("b"))
        assertEquals(listOf("b"), Sync.overlay(id, emptyList()).map { it.ref })
        val queued = Sync.pending
        Sync.deletePlaylist(id)
        assertFalse(Sync.playlists.any { it.browseId == id })
        assertEquals(queued - 2, Sync.pending) // its create and add are dropped, no delete is queued
    }

    @Test
    fun overlayHidesQueuedRemovals() {
        val t = song("c").copy(setVideoId = "s1")
        Sync.removeFromPlaylist("VLPL1", t)
        assertEquals(emptyList<String>(), Sync.overlay("VLPL1", listOf(t)).map { it.ref })
    }
}
