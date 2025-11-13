package com.musicplayer.model;


import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/**
 * A "smart" playlist that dynamically shows songs of a specific genre.
 * It filters the main library's list rather than storing songs itself.
 */
public class GenrePlaylist implements Playlist {
    private final String genre;
    private final ObservableList<Song> filteredSongs;

    public GenrePlaylist(String genre, ObservableList<Song> allSongs) {
        this.genre = genre;

        // This list automatically updates when the source list changes.
        this.filteredSongs = new FilteredList<>(allSongs, song ->
                song.getGenre().equalsIgnoreCase(this.genre)
        );
    }

    @Override
    public String getName() {
        return "Genre: " + genre;
    }

    @Override
    public ObservableList<Song> getSongs() {
        return filteredSongs;
    }

    @Override
    public String toString() {
        return getName();
    }
}
