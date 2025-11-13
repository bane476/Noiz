package com.musicplayer.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * A standard, user-managed playlist.
 */
public class UserPlaylist implements Playlist {
    private String name;
    private final ObservableList<Song> songs;

    public UserPlaylist(String name) {
        this.name = name;
        this.songs = FXCollections.observableArrayList();
    }

    public void addSong(Song song) {
        if (song != null && !songs.contains(song)) {
            songs.add(song);
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ObservableList<Song> getSongs() {
        return this.songs;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
