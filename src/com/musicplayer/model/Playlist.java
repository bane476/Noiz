package com.musicplayer.model;

import javafx.collections.ObservableList;

/**
 * Interface for playlist types.
 */
public interface Playlist {
    String getName();
    ObservableList<Song> getSongs();
}
