package com.musicplayer.service;

import com.musicplayer.model.Playlist;
import com.musicplayer.model.Song;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import java.util.Random;

/**
 * Manages playback logic, hiding JavaFX MediaPlayer complexity from the UI.
 * The UI calls methods like play(), pause(), next(), etc.
 */
public class PlayerEngine {
    private MediaPlayer mediaPlayer;

    private final ReadOnlyObjectWrapper<Song> currentSong;
    private final ReadOnlyBooleanWrapper isPlaying;
    private final ReadOnlyObjectWrapper<Duration> currentTime;
    private final ReadOnlyObjectWrapper<Duration> totalDuration;

    private final ReadOnlyBooleanWrapper isShuffle;
    private final ReadOnlyBooleanWrapper isRepeat;


    private Playlist currentPlaylist;
    private int currentTrackIndex;

    public PlayerEngine() {
        this.currentSong = new ReadOnlyObjectWrapper<>();
        this.isPlaying = new ReadOnlyBooleanWrapper(false);
        this.currentTime = new ReadOnlyObjectWrapper<>(Duration.ZERO);
        this.totalDuration = new ReadOnlyObjectWrapper<>(Duration.ZERO);
        this.isShuffle = new ReadOnlyBooleanWrapper(false);
        this.isRepeat = new ReadOnlyBooleanWrapper(false);
        this.currentTrackIndex = 0;
    }

    public void loadPlaylist(Playlist playlist) {
        if (mediaPlayer != null) {
            stop();
        }
        this.currentPlaylist = playlist;
        this.currentTrackIndex = 0;

        if (this.currentPlaylist != null && !this.currentPlaylist.getSongs().isEmpty()) {
            loadSong(this.currentPlaylist.getSongs().get(currentTrackIndex));
        }
    }

    public void playSongFromPlaylist(Song song) {
        if (currentPlaylist == null) return;

        int index = currentPlaylist.getSongs().indexOf(song);
        if (index != -1) {
            currentTrackIndex = index;
            loadSong(song);
            play();
        }
    }

    private void loadSong(Song song) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        try {
            Media media = new Media(song.getFilePath());
            mediaPlayer = new MediaPlayer(media);

            mediaPlayer.setOnReady(() -> {
                currentSong.set(song);
                isPlaying.set(false);
                totalDuration.set(mediaPlayer.getMedia().getDuration());
                // Reset currentTime when a new song is ready
                currentTime.set(Duration.ZERO);
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (newTime != null) {
                    currentTime.set(newTime);
                }
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                if (isRepeat.get()) {
                    mediaPlayer.seek(Duration.ZERO);
                    play();
                } else {
                    playNextSong();
                }
            });

            mediaPlayer.setOnError(() -> {
                System.err.println("MediaPlayer Error: " + mediaPlayer.getError());
                next(); // Try to play the next song
            });

        } catch (Exception e) {
            System.err.println("Error loading song: " + song.getFilePath());
            e.printStackTrace();
        }
    }

    public void play() {
        if (mediaPlayer != null && !isPlaying.get()) {
            mediaPlayer.play();
            isPlaying.set(true);
        }
    }

    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            isPlaying.set(false);
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            isPlaying.set(false);
        }
    }

    private void playNextSong() {
        if (currentPlaylist == null || currentPlaylist.getSongs().isEmpty()) return;

        if (isShuffle.get()) {
            currentTrackIndex = new Random().nextInt(currentPlaylist.getSongs().size());
        } else { // Not shuffle
            if (currentTrackIndex + 1 >= currentPlaylist.getSongs().size()) { // End of playlist
                stop();
                // Set player to beginning of playlist but don't play
                currentTrackIndex = 0;
                if (!currentPlaylist.getSongs().isEmpty()) {
                    loadSong(currentPlaylist.getSongs().get(currentTrackIndex));
                }
                return;
            } else {
                currentTrackIndex++;
            }
        }
        loadSong(currentPlaylist.getSongs().get(currentTrackIndex));
        play();
    }

    public void next() {
        playNextSong();
    }

    public void previous() {
        if (currentPlaylist == null || currentPlaylist.getSongs().isEmpty()) return;

        if (isShuffle.get()) {
            // In shuffle mode, previous plays another random song
            playNextSong();
        } else {
            currentTrackIndex = (currentTrackIndex - 1 + currentPlaylist.getSongs().size()) % currentPlaylist.getSongs().size();
            loadSong(currentPlaylist.getSongs().get(currentTrackIndex));
            play();
        }
    }

    public void setVolume(double volume) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume);
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer != null) {
            if (isPlaying.get()) {
                pause();
            } else {
                play();
            }
        }
    }

    // --- Properties for UI Binding ---

    public ReadOnlyObjectProperty<Song> currentSongProperty() {
        return currentSong.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty isPlayingProperty() {
        return isPlaying.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Duration> currentTimeProperty() {
        return currentTime.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Duration> totalDurationProperty() {
        return totalDuration.getReadOnlyProperty();
    }

    public void seek(Duration duration) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(duration);
        }
    }

    public ReadOnlyBooleanProperty isShuffleProperty() {
        return isShuffle.getReadOnlyProperty();
    }

    public void toggleShuffle() {
        isShuffle.set(!isShuffle.get());
    }

    public ReadOnlyBooleanProperty isRepeatProperty() {
        return isRepeat.getReadOnlyProperty();
    }

    public void toggleRepeat() {
        isRepeat.set(!isRepeat.get());
    }
}
