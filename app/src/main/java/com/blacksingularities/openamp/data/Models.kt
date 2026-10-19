package com.blacksingularities.openamp.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    val dateAdded: Long,
    val size: Long,
    val path: String,
    val mime: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val folder: String get() = path.substringBeforeLast('/', "")
    val folderName: String get() = folder.substringAfterLast('/')
    val format: String
        get() = path.substringAfterLast('.', "").uppercase().ifEmpty { mime.substringAfter('/').uppercase() }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val tracks: List<Track>,
) {
    val cover: Track get() = tracks.first()
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/** Grupa utworów (wykonawca, gatunek, folder, playlista). */
data class TrackGroup(
    val key: String,
    val name: String,
    val tracks: List<Track>,
    val subtitle: String = "",
) {
    val cover: Track? get() = tracks.firstOrNull()
}

enum class LibraryTab { TRACKS, ALBUMS, ARTISTS, GENRES, FOLDERS, PLAYLISTS }

enum class TrackSort {
    TITLE, ARTIST, ALBUM, YEAR, DURATION, DATE_ADDED, PLAY_COUNT, LAST_PLAYED, SIZE, RANDOM
}

enum class GroupSort { NAME, TRACK_COUNT, YEAR, PLAY_COUNT, DATE_ADDED, RANDOM }

enum class ViewMode { LIST, COMPACT, GRID, GRID_LARGE }

data class Playlist(val id: String, val name: String, val trackIds: List<Long>)

/** Wbudowane listy liczone z biblioteki i statystyk. */
enum class SmartList { FAVORITES, MOST_PLAYED, RECENTLY_PLAYED, RECENTLY_ADDED, NEVER_PLAYED }
