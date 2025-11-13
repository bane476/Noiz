package com.musicplayer.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages all known songs and playlists.
 */
public class MusicLibrary {
    private final ObservableList<Song> allSongs;
    private final ObservableList<Playlist> allPlaylists;
    private final Set<String> knownFilePaths;
    private final Set<String> foundGenres;

    public MusicLibrary() {
        this.allSongs = FXCollections.observableArrayList();
        this.allPlaylists = FXCollections.observableArrayList();
        this.knownFilePaths = new HashSet<>();
        this.foundGenres = new HashSet<>();

        // Add a default "All Songs" smart playlist
        this.allPlaylists.add(new Playlist() {
            @Override public String getName() { return "All Songs"; }
            @Override public ObservableList<Song> getSongs() { return allSongs; }
            @Override public String toString() { return getName(); }
        });
    }

    public ObservableList<Song> getAllSongs() {
        return allSongs;
    }

    public ObservableList<Playlist> getAllPlaylists() {
        return allPlaylists;
    }

    /**
     * Scans a directory recursively for .mp3 files.
     */
    public void scanDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file); // Recurse
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                addSongFromFile(file);
            }
        }
    }

    private void addSongFromFile(File file) {
        String filePath = file.toURI().toString();

        // Avoid adding duplicates
        if (knownFilePaths.contains(filePath)) {
            return;
        }

        try {
            Media media = new Media(filePath);
            // Metadata is read asynchronously; use a temporary player to load it.
            MediaPlayer tempPlayer = new MediaPlayer(media);

            tempPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                if (newStatus == MediaPlayer.Status.READY) {
                    createSongFromMetadata(media, filePath, file);
                } else if (newStatus == MediaPlayer.Status.HALTED || newStatus == MediaPlayer.Status.UNKNOWN) {
                    System.err.println("Error reading metadata for: " + file.getPath());
                }
                if (newStatus == MediaPlayer.Status.READY || newStatus == MediaPlayer.Status.HALTED || newStatus == MediaPlayer.Status.UNKNOWN) {
                    tempPlayer.dispose();
                }
            });
            tempPlayer.setOnError(() -> {
                System.err.println("Error reading metadata for: " + file.getPath());
                tempPlayer.dispose();
            });

        } catch (Exception e) {
            System.err.println("Could not process file: " + file.getPath());
            e.printStackTrace();
        }
    }

    private void createSongFromMetadata(Media media, String filePath, File file) {
        String title = (String) media.getMetadata().get("title");
        String artist = (String) media.getMetadata().get("artist");
        String album = (String) media.getMetadata().get("album");
        String genre = (String) media.getMetadata().get("genre");

        // Use file name as fallback for title
        if (title == null || title.isEmpty()) {
            title = file.getName().replace(".mp3", "");
        }

        Song newSong = new Song(
                filePath,
                title,
                (artist == null ? "" : artist),
                (album == null ? "" : album),
                (genre == null ? "" : genre)
        );

        // Add to list on FX thread
        javafx.application.Platform.runLater(() -> {
            allSongs.add(newSong);
            knownFilePaths.add(filePath);

            // Check if we need to create a new "Genre" smart playlist
            if (genre != null && !genre.isEmpty() && foundGenres.add(genre)) {
                allPlaylists.add(new GenrePlaylist(genre, allSongs));
            }
        });
    }

    public void createUserPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        // Simple check to avoid duplicate playlist names
        if (allPlaylists.stream().anyMatch(p -> p.getName().equalsIgnoreCase(name))) {
            System.err.println("Playlist with name '" + name + "' already exists.");
            return;
        }
        UserPlaylist newPlaylist = new UserPlaylist(name);
        allPlaylists.add(newPlaylist);
    }

    public ObservableList<UserPlaylist> getUserPlaylists() {
        return allPlaylists.stream()
                .filter(p -> p instanceof UserPlaylist)
                .map(p -> (UserPlaylist) p)
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
    }

    public void deletePlaylist(Playlist playlist) {
        // Only allow deleting user playlists
        if (playlist instanceof UserPlaylist) {
            allPlaylists.remove(playlist);
        }
    }

    public void renamePlaylist(UserPlaylist playlist, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return;
        }
        // Check for duplicate names, ignoring the current playlist
        if (allPlaylists.stream().anyMatch(p -> p.getName().equalsIgnoreCase(newName) && p != playlist)) {
            System.err.println("Playlist with name '" + newName + "' already exists.");
            return;
        }
        playlist.setName(newName);

        // Force ListView refresh
        int index = allPlaylists.indexOf(playlist);
        if (index != -1) {
            allPlaylists.set(index, playlist);
        }
    }

    public void saveToFile(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            // Save all songs
            for (Song song : allSongs) {
                writer.write("SONG_START\n");
                writer.write("filePath:" + song.getFilePath() + "\n");
                writer.write("title:" + song.getTitle().replace("\n", " ") + "\n");
                writer.write("artist:" + song.getArtist().replace("\n", " ") + "\n");
                writer.write("album:" + song.getAlbum().replace("\n", " ") + "\n");
                writer.write("genre:" + song.getGenre().replace("\n", " ") + "\n");
                writer.write("SONG_END\n");
            }

            // Save user playlists
            for (UserPlaylist playlist : getUserPlaylists()) {
                writer.write("PLAYLIST_START:" + playlist.getName().replace("\n", " ") + "\n");
                for (Song song : playlist.getSongs()) {
                    writer.write("PLAYLIST_SONG:" + song.getFilePath() + "\n");
                }
                writer.write("PLAYLIST_END\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadFromFile(File file) {
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // Clear existing user data, but keep the "All Songs" playlist
            allSongs.clear();
            allPlaylists.removeIf(p -> !(p.getName().equals("All Songs")));
            knownFilePaths.clear();
            foundGenres.clear();

            Map<String, Song> songMap = new HashMap<>();
            String line;
            Map<String, String> songData = new HashMap<>();
            UserPlaylist currentPlaylist = null;

            while ((line = reader.readLine()) != null) {
                if (line.equals("SONG_START")) {
                    songData.clear();
                } else if (line.startsWith("filePath:")) {
                    songData.put("filePath", line.substring("filePath:".length()));
                } else if (line.startsWith("title:")) {
                    songData.put("title", line.substring("title:".length()));
                } else if (line.startsWith("artist:")) {
                    songData.put("artist", line.substring("artist:".length()));
                } else if (line.startsWith("album:")) {
                    songData.put("album", line.substring("album:".length()));
                } else if (line.startsWith("genre:")) {
                    songData.put("genre", line.substring("genre:".length()));
                } else if (line.equals("SONG_END")) {
                    String filePath = songData.getOrDefault("filePath", "");
                    if (filePath.isEmpty()) {
                        continue;
                    }
                    try {
                        java.io.File songFile = new java.io.File(java.net.URI.create(filePath));
                        if (!songFile.exists()) {
                            System.err.println("File for song not found, skipping: " + filePath);
                            continue;
                        }
                    } catch (Exception e) {
                        System.err.println("Invalid file path for song, skipping: " + filePath);
                        continue;
                    }
                    Song newSong = new Song(
                        filePath,
                        songData.getOrDefault("title", ""),
                        songData.getOrDefault("artist", ""),
                        songData.getOrDefault("album", ""),
                        songData.getOrDefault("genre", "")
                    );
                    allSongs.add(newSong);
                    knownFilePaths.add(newSong.getFilePath());
                    songMap.put(newSong.getFilePath(), newSong);
                    
                    String genre = newSong.getGenre();
                    if (genre != null && !genre.isEmpty() && !genre.equals("Unknown Genre") && foundGenres.add(genre)) {
                        allPlaylists.add(new GenrePlaylist(genre, allSongs));
                    }
                } else if (line.startsWith("PLAYLIST_START:")) {
                    String name = line.substring("PLAYLIST_START:".length());
                    currentPlaylist = new UserPlaylist(name);
                } else if (line.startsWith("PLAYLIST_SONG:")) {
                    if (currentPlaylist != null) {
                        String songPath = line.substring("PLAYLIST_SONG:".length());
                        Song song = songMap.get(songPath);
                        if (song != null) {
                            currentPlaylist.addSong(song);
                        }
                    }
                } else if (line.equals("PLAYLIST_END")) {
                    if (currentPlaylist != null) {
                        allPlaylists.add(currentPlaylist);
                        currentPlaylist = null;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
