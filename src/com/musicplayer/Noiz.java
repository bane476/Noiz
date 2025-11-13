package com.musicplayer;

import com.musicplayer.model.MusicLibrary;
import com.musicplayer.model.Playlist;
import com.musicplayer.model.Song;
import com.musicplayer.model.UserPlaylist;
import com.musicplayer.service.PlayerEngine;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.File;

/**
 * Main application class. Builds the UI and connects the components.
 */
public class Noiz extends Application {

    private final MusicLibrary library = new MusicLibrary();
    private final PlayerEngine engine = new PlayerEngine();

    private ListView<Playlist> playlistView = new ListView<>();
    private TableView<Song> songView = new TableView<>();
    private Label currentSongLabel = new Label("No song selected");
    private Button playPauseButton = new Button("Play");
    private Slider volumeSlider = new Slider(0, 1, 0.75);
    private Slider songProgressSlider = new Slider();
    private Label currentTimeLabel = new Label("00:00");
    private Label totalTimeLabel = new Label("00:00");
    private TextField searchField = new TextField();
    private ToggleButton shuffleButton = new ToggleButton("Shuffle");
    private ToggleButton repeatButton = new ToggleButton("Repeat");

    public static void main(String[] args) {
        // You MUST have the JavaFX SDK.
        // If running from command line, you need to add VM options, e.g.:
        // --module-path /path/to/javafx-sdk-xx/lib --add-modules javafx.controls,javafx.media
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Load library from file before building UI
        File saveFile = new File(System.getProperty("user.home"), "music_library.json");
        library.loadFromFile(saveFile);

        primaryStage.setTitle("Noiz");

        // --- Layout ---
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Left: Playlists
        VBox leftPanel = new VBox(10);
        Button addFolderButton = new Button("Scan Music Folder");
        Button newPlaylistButton = new Button("New Playlist");
        HBox playlistButtons = new HBox(5, addFolderButton, newPlaylistButton);
        leftPanel.getChildren().addAll(new Label("Playlists"), playlistView, playlistButtons);
        root.setLeft(leftPanel);
        BorderPane.setMargin(leftPanel, new Insets(0, 10, 0, 0));

        // Center: Songs in selected playlist
        setupSongTableView(); // Call setup method
        searchField.setPromptText("Search songs...");
        VBox centerPanel = new VBox(10);
        centerPanel.getChildren().addAll(new Label("Songs"), searchField, songView);
        root.setCenter(centerPanel);

        // Bottom: Player Controls
        root.setBottom(createControlsPanel());

        // --- Wiring and Logic ---

        // Wire up folder and playlist buttons
        addFolderButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Music Folder to Scan");
            File selectedDirectory = chooser.showDialog(primaryStage);
            if (selectedDirectory != null) {
                // Run scanning in a new thread to keep UI responsive
                new Thread(() -> library.scanDirectory(selectedDirectory)).start();
            }
        });

        newPlaylistButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("My Playlist");
            dialog.setTitle("New Playlist");
            dialog.setHeaderText("Create a new playlist");
            dialog.setContentText("Playlist name:");

            dialog.showAndWait().ifPresent(name -> {
                library.createUserPlaylist(name);
            });
        });

        // Bind Playlist view to the library and add context menu
        playlistView.setItems(library.getAllPlaylists());
        setupPlaylistContextMenu();

        // When a playlist is clicked, show its songs in the songView
        playlistView.getSelectionModel().selectedItemProperty().addListener((obs, oldPl, newPl) -> {
            if (newPl != null) {
                // Wrap the playlist's songs in a FilteredList
                FilteredList<Song> filteredSongs = new FilteredList<>(newPl.getSongs(), p -> true);

                // Add a listener to the search field to update the filter
                searchField.textProperty().addListener((o, oldVal, newVal) -> {
                    filteredSongs.setPredicate(song -> {
                        if (newVal == null || newVal.isEmpty()) {
                            return true;
                        }
                        String lowerCaseFilter = newVal.toLowerCase();
                        if (song.getTitle().toLowerCase().contains(lowerCaseFilter)) {
                            return true;
                        } else if (song.getArtist().toLowerCase().contains(lowerCaseFilter)) {
                            return true;
                        } else if (song.getAlbum().toLowerCase().contains(lowerCaseFilter)) {
                            return true;
                        } else if (song.getGenre().toLowerCase().contains(lowerCaseFilter)) {
                            return true;
                        }
                        return false;
                    });
                });

                // Wrap the FilteredList in a SortedList
                SortedList<Song> sortedSongs = new SortedList<>(filteredSongs);

                // Bind the SortedList comparator to the TableView comparator
                sortedSongs.comparatorProperty().bind(songView.comparatorProperty());

                // Set the sorted and filtered data to the TableView
                songView.setItems(sortedSongs);

                // Give the player engine the context of the displayed songs
                Playlist displayPlaylist = new Playlist() {
                    @Override
                    public String getName() {
                        return newPl.getName();
                    }
                    @Override
                    public ObservableList<Song> getSongs() {
                        return sortedSongs;
                    }
                };
                engine.loadPlaylist(displayPlaylist);
            }
        });

        // When a song is double-clicked, play it
        songView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Song selectedSong = songView.getSelectionModel().getSelectedItem();
                if (selectedSong != null) {
                    engine.playSongFromPlaylist(selectedSong);
                }
            }
        });

        // Wire up player controls
        playPauseButton.setOnAction(e -> engine.togglePlayPause());

        // Bind play/pause button text to player state
        engine.isPlayingProperty().addListener((obs, wasPlaying, isPlaying) -> {
            playPauseButton.setText(isPlaying ? "Pause" : "Play");
        });

        // Bind current song label and highlight playing song in table
        engine.currentSongProperty().addListener((obs, oldSong, newSong) -> {
            currentSongLabel.setText(newSong != null ? newSong.getTitle() : "N/A");
            if (newSong != null) {
                songView.getSelectionModel().select(newSong);
            } else {
                songView.getSelectionModel().clearSelection();
            }
        });

        // Wire up volume slider
        engine.setVolume(volumeSlider.getValue()); // Set initial volume
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            engine.setVolume(newVal.doubleValue());
        });

        // Wire up shuffle and repeat buttons
        shuffleButton.setOnAction(e -> engine.toggleShuffle());
        repeatButton.setOnAction(e -> engine.toggleRepeat());

        engine.isShuffleProperty().addListener((obs, oldVal, newVal) -> {
            shuffleButton.setSelected(newVal);
            shuffleButton.setStyle(newVal ? "-fx-base: lightgreen;" : "");
        });
        engine.isRepeatProperty().addListener((obs, oldVal, newVal) -> {
            repeatButton.setSelected(newVal);
            repeatButton.setStyle(newVal ? "-fx-base: lightgreen;" : "");
        });

        // Wire up song progress slider and time labels
        engine.totalDurationProperty().addListener((obs, oldDur, newDur) -> {
            if (newDur != null && !newDur.isUnknown()) {
                songProgressSlider.setMax(newDur.toSeconds());
                totalTimeLabel.setText(formatDuration(newDur));
            }
        });

        engine.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime != null && !newTime.isUnknown()) {
                // Update slider only if user is not dragging it
                if (!songProgressSlider.isValueChanging()) {
                    songProgressSlider.setValue(newTime.toSeconds());
                }
                currentTimeLabel.setText(formatDuration(newTime));
            }
        });

        songProgressSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Seek only when the user has finished dragging the slider
            if (!songProgressSlider.isValueChanging()) {
                if (Math.abs(newVal.doubleValue() - engine.currentTimeProperty().get().toSeconds()) > 1) {
                    engine.seek(javafx.util.Duration.seconds(newVal.doubleValue()));
                }
            }
        });

        // --- Show Scene ---
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("resources/styles.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        // Select "All Songs" by default
        playlistView.getSelectionModel().selectFirst();
    }

    private Node createControlsPanel() {
        // --- Bottom Player Controls ---
        // Main container for all controls
        VBox controlsContainer = new VBox(5);
        controlsContainer.setPadding(new Insets(10, 0, 0, 0));
        controlsContainer.setAlignment(Pos.CENTER);

        // Progress Bar and Time Labels
        HBox progressBox = new HBox(5, currentTimeLabel, songProgressSlider, totalTimeLabel);
        progressBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(songProgressSlider, Priority.ALWAYS); // Make slider grow

        // Buttons, Now Playing, and Volume
        HBox bottomControls = new HBox(15);
        bottomControls.setAlignment(Pos.CENTER);

        Button prevButton = new Button("<<");
        Button nextButton = new Button(">>");
        Button stopButton = new Button("Stop");

        prevButton.setOnAction(e -> engine.previous());
        nextButton.setOnAction(e -> engine.next());
        stopButton.setOnAction(e -> engine.pause());

        HBox volumeBox = new HBox(5, new Label("Vol:"), volumeSlider);
        volumeBox.setAlignment(Pos.CENTER);

        VBox nowPlayingBox = new VBox(5);
        nowPlayingBox.setAlignment(Pos.CENTER_LEFT);
        nowPlayingBox.getChildren().addAll(new Label("Now Playing:"), currentSongLabel);

        HBox playBar = new HBox(10, shuffleButton, repeatButton, prevButton, playPauseButton, nextButton, stopButton);
        playBar.setAlignment(Pos.CENTER);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomControls.getChildren().addAll(nowPlayingBox, spacer, playBar, volumeBox);

        // Add all sections to the main container
        controlsContainer.getChildren().addAll(progressBox, bottomControls);

        return controlsContainer;
    }

    private String formatDuration(javafx.util.Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "00:00";
        }
        long totalSeconds = (long) duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void setupSongTableView() {
        songView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Song, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<Song, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(new PropertyValueFactory<>("artist"));

        TableColumn<Song, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(new PropertyValueFactory<>("album"));

        TableColumn<Song, String> genreCol = new TableColumn<>("Genre");
        genreCol.setCellValueFactory(new PropertyValueFactory<>("genre"));

        songView.getColumns().addAll(titleCol, artistCol, albumCol, genreCol);

        // --- Context Menu for adding songs to playlists ---
        ContextMenu contextMenu = new ContextMenu();
        Menu addToPlaylistMenu = new Menu("Add to Playlist");

        // Dynamically build the playlist sub-menu each time the context menu is requested
        songView.setOnContextMenuRequested(event -> {
            Song selectedSong = songView.getSelectionModel().getSelectedItem();
            if (selectedSong == null) {
                // Don't show the menu if no song is selected
                contextMenu.hide();
                return;
            }

            addToPlaylistMenu.getItems().clear();
            // Only show the "Add to" menu if there are user playlists to add to
            if (library.getUserPlaylists().isEmpty()) {
                return;
            }

            for (UserPlaylist playlist : library.getUserPlaylists()) {
                MenuItem playlistItem = new MenuItem(playlist.getName());
                playlistItem.setOnAction(actionEvent -> {
                    playlist.addSong(selectedSong);
                });
                addToPlaylistMenu.getItems().add(playlistItem);
            }
        });

        contextMenu.getItems().add(addToPlaylistMenu);
        songView.setContextMenu(contextMenu);
    }

    private void setupPlaylistContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem renameItem = new MenuItem("Rename");
        MenuItem deleteItem = new MenuItem("Delete");

        renameItem.setOnAction(e -> {
            Playlist selectedPlaylist = playlistView.getSelectionModel().getSelectedItem();
            if (selectedPlaylist instanceof UserPlaylist) {
                TextInputDialog dialog = new TextInputDialog(selectedPlaylist.getName());
                dialog.setTitle("Rename Playlist");
                dialog.setHeaderText("Enter a new name for the playlist");
                dialog.setContentText("New name:");
                dialog.showAndWait().ifPresent(newName -> {
                    library.renamePlaylist((UserPlaylist) selectedPlaylist, newName);
                });
            }
        });

        deleteItem.setOnAction(e -> {
            Playlist selectedPlaylist = playlistView.getSelectionModel().getSelectedItem();
            if (selectedPlaylist != null) {
                library.deletePlaylist(selectedPlaylist);
            }
        });

        contextMenu.getItems().addAll(renameItem, deleteItem);

        playlistView.setContextMenu(contextMenu);

        // Only show the context menu for user-created playlists
        playlistView.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            Playlist selected = playlistView.getSelectionModel().getSelectedItem();
            if (!(selected instanceof UserPlaylist)) {
                contextMenu.hide();
            }
        });
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Closing application and saving library...");
        File saveFile = new File(System.getProperty("user.home"), "music_library.json");
        library.saveToFile(saveFile);
        super.stop();
    }
}
