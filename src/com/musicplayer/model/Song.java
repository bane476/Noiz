package com.musicplayer.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Data model for a song.
 * Uses JavaFX properties for UI binding.
 */
public class Song {
    private final StringProperty title;
    private final StringProperty artist;
    private final StringProperty album;
    private final StringProperty genre;
    private final String filePath; // The actual path to the file

    public Song(String filePath, String title, String artist, String album, String genre) {
        this.filePath = filePath;
        this.title = new SimpleStringProperty(title.isEmpty() ? "Unknown Title" : title);
        this.artist = new SimpleStringProperty(artist.isEmpty() ? "Unknown Artist" : artist);
        this.album = new SimpleStringProperty(album.isEmpty() ? "Unknown Album" : album);
        this.genre = new SimpleStringProperty(genre.isEmpty() ? "Unknown Genre" : genre);
    }

    // Properties for UI binding
    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty artistProperty() {
        return artist;
    }

    public StringProperty albumProperty() {
        return album;
    }

    // Getters
    public String getFilePath() {
        return filePath;
    }

    public String getTitle() {
        return title.get();
    }

    public String getArtist() {
        return artist.get();
    }

    public String getAlbum() {
        return album.get();
    }

    public String getGenre() {
        return genre.get();
    }

    // Used for display in lists.
    @Override
    public String toString() {
        return getTitle() + " - " + getArtist();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return filePath.equals(song.filePath);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath);
    }
}
