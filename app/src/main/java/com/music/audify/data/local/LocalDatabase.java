// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.local;

import androidx.room.Dao;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.RoomDatabase;
import java.util.List;

/** Java Room declarations use javac annotation processing, compatible with AGP 9 built-in Kotlin. */
@Database(entities = {LocalDatabase.SongRow.class, LocalDatabase.FavoriteRow.class,
        LocalDatabase.HistoryRow.class, LocalDatabase.PlaylistRow.class,
        LocalDatabase.PlaylistSongRow.class, LocalDatabase.PlaylistAccountSyncRow.class,
        LocalDatabase.FavoriteAccountSyncRow.class, LocalDatabase.SyncOperationRow.class,
        LocalDatabase.MigrationStateRow.class},
        version = 2, exportSchema = true)
public abstract class LocalDatabase extends RoomDatabase {
    public abstract LocalDao dao();

    @Entity(tableName = "songs")
    public static class SongRow {
        @NonNull @PrimaryKey public String videoId;
        public String title, artist, thumbnailUrl, durationText, artistId, artistsJson, albumId, albumName, setVideoId;
        public boolean isVideo, isVideoOrigin;
        public SongRow(String videoId, String title, String artist, String thumbnailUrl, String durationText,
                String artistId, String artistsJson, String albumId, String albumName,
                boolean isVideo, boolean isVideoOrigin, String setVideoId) {
            this.videoId=videoId; this.title=title; this.artist=artist; this.thumbnailUrl=thumbnailUrl;
            this.durationText=durationText; this.artistId=artistId; this.artistsJson=artistsJson;
            this.albumId=albumId; this.albumName=albumName; this.isVideo=isVideo;
            this.isVideoOrigin=isVideoOrigin; this.setVideoId=setVideoId;
        }
    }

    @Entity(tableName = "favorites", primaryKeys = {"videoId"})
    public static class FavoriteRow {
        @NonNull public String videoId; public long addedAt;
        public FavoriteRow(String videoId, long addedAt) { this.videoId=videoId; this.addedAt=addedAt; }
    }

    @Entity(tableName = "history", primaryKeys = {"videoId"}, indices = {@Index("lastPlayedAt")})
    public static class HistoryRow {
        @NonNull public String videoId; public long lastPlayedAt; public int playCount;
        public HistoryRow(String videoId, long lastPlayedAt, int playCount) {
            this.videoId=videoId; this.lastPlayedAt=lastPlayedAt; this.playCount=playCount;
        }
    }

    @Entity(tableName = "playlists")
    public static class PlaylistRow {
        @NonNull @PrimaryKey public String playlistId;
        public String title; public long createdAt, updatedAt, localRevision;
        public PlaylistRow(String playlistId, String title, long createdAt, long updatedAt, long localRevision) {
            this.playlistId=playlistId; this.title=title; this.createdAt=createdAt;
            this.updatedAt=updatedAt; this.localRevision=localRevision;
        }
    }

    @Entity(tableName = "playlist_songs", primaryKeys = {"playlistId", "videoId"}, indices = {@Index("videoId")})
    public static class PlaylistSongRow {
        @NonNull public String playlistId;
        @NonNull public String videoId;
        public int position;
        public PlaylistSongRow(String playlistId, String videoId, int position) {
            this.playlistId=playlistId; this.videoId=videoId; this.position=position;
        }
    }

    @Entity(tableName = "playlist_account_sync", primaryKeys = {"playlistId", "accountId", "profileId"})
    public static class PlaylistAccountSyncRow {
        @NonNull public String playlistId;
        @NonNull public String accountId;
        @NonNull public String profileId;
        public String youtubePlaylistId, syncedVideoIdsJson, syncedTitle;
        public long syncedRevision, updatedAt; public String status, lastError;
        public PlaylistAccountSyncRow(String playlistId, String accountId, String profileId, String youtubePlaylistId,
                String syncedVideoIdsJson, String syncedTitle, long syncedRevision, String status,
                String lastError, long updatedAt) {
            this.playlistId=playlistId; this.accountId=accountId; this.profileId=profileId;
            this.youtubePlaylistId=youtubePlaylistId; this.syncedVideoIdsJson=syncedVideoIdsJson;
            this.syncedTitle=syncedTitle; this.syncedRevision=syncedRevision; this.status=status;
            this.lastError=lastError; this.updatedAt=updatedAt;
        }
    }

    @Entity(tableName = "favorite_account_sync", primaryKeys = {"videoId", "accountId", "profileId"})
    public static class FavoriteAccountSyncRow {
        @NonNull public String videoId;
        @NonNull public String accountId;
        @NonNull public String profileId;
        public String status;
        public long updatedAt;
        public FavoriteAccountSyncRow(String videoId, String accountId, String profileId, String status, long updatedAt) {
            this.videoId=videoId; this.accountId=accountId; this.profileId=profileId;
            this.status=status; this.updatedAt=updatedAt;
        }
    }

    @Entity(tableName = "sync_operations", indices = {
            @Index(value = {"dedupeKey"}, unique = true),
            @Index(value = {"accountId", "profileId", "state", "nextAttemptAt"})
    })
    public static class SyncOperationRow {
        @PrimaryKey(autoGenerate = true) public long id;
        public String dedupeKey, accountId, profileId, type, entityId, payloadJson, state, lastError;
        public int attempts; public long nextAttemptAt, createdAt, updatedAt;
        public SyncOperationRow(long id, String dedupeKey, String accountId, String profileId, String type,
                String entityId, String payloadJson, String state, int attempts, long nextAttemptAt,
                String lastError, long createdAt, long updatedAt) {
            this.id=id; this.dedupeKey=dedupeKey; this.accountId=accountId; this.profileId=profileId;
            this.type=type; this.entityId=entityId; this.payloadJson=payloadJson; this.state=state;
            this.attempts=attempts; this.nextAttemptAt=nextAttemptAt; this.lastError=lastError;
            this.createdAt=createdAt; this.updatedAt=updatedAt;
        }
    }

    @Entity(tableName = "migration_state")
    public static class MigrationStateRow {
        @NonNull @PrimaryKey public String key;
        @NonNull public String value;
        public MigrationStateRow(String key, String value) { this.key=key; this.value=value; }
    }

    @Dao
    public interface LocalDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putSongs(List<SongRow> rows);
        @Query("SELECT * FROM songs WHERE videoId = :id LIMIT 1") SongRow song(String id);
        @Query("SELECT * FROM songs") List<SongRow> allSongs();
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putFavorites(List<FavoriteRow> rows);
        @Query("DELETE FROM favorites WHERE videoId = :id") void deleteFavorite(String id);
        @Query("DELETE FROM favorites") void clearFavorites();
        @Query("SELECT * FROM favorites") List<FavoriteRow> favorites();
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putHistory(List<HistoryRow> rows);
        @Query("DELETE FROM history") void clearHistory();
        @Query("SELECT * FROM history ORDER BY lastPlayedAt DESC LIMIT 500") List<HistoryRow> history();
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putPlaylist(PlaylistRow row);
        @Query("SELECT * FROM playlists WHERE playlistId = :id LIMIT 1") PlaylistRow playlist(String id);
        @Query("SELECT * FROM playlists") List<PlaylistRow> playlists();
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putPlaylistSongs(List<PlaylistSongRow> rows);
        @Query("DELETE FROM playlist_songs WHERE playlistId = :id") void clearPlaylistSongs(String id);
        @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position") List<PlaylistSongRow> playlistSongs(String id);
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putPlaylistSync(PlaylistAccountSyncRow row);
        @Query("SELECT * FROM playlist_account_sync WHERE playlistId = :playlistId AND accountId = :accountId AND profileId = :profileId LIMIT 1")
        PlaylistAccountSyncRow playlistSync(String playlistId, String accountId, String profileId);
        @Query("SELECT * FROM playlist_account_sync WHERE accountId = :accountId AND profileId = :profileId ORDER BY updatedAt DESC")
        List<PlaylistAccountSyncRow> playlistSyncRows(String accountId, String profileId);
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putFavoriteSync(FavoriteAccountSyncRow row);
        @Query("SELECT * FROM favorite_account_sync WHERE videoId = :videoId AND accountId = :accountId AND profileId = :profileId LIMIT 1")
        FavoriteAccountSyncRow favoriteSync(String videoId, String accountId, String profileId);
        @Query("SELECT * FROM favorite_account_sync WHERE accountId = :accountId AND profileId = :profileId ORDER BY updatedAt DESC")
        List<FavoriteAccountSyncRow> favoriteSyncRows(String accountId, String profileId);
        @Insert(onConflict = OnConflictStrategy.REPLACE) void enqueue(SyncOperationRow row);
        @Query("SELECT * FROM sync_operations WHERE accountId = :accountId AND profileId = :profileId AND state = 'pending' AND nextAttemptAt <= :now ORDER BY createdAt LIMIT 100")
        List<SyncOperationRow> dueOperations(String accountId, String profileId, long now);
        @Query("UPDATE sync_operations SET state = :state, attempts = :attempts, nextAttemptAt = :nextAt, lastError = :error, updatedAt = :now WHERE id = :id AND payloadJson = :payloadJson AND updatedAt = :expectedUpdatedAt")
        void updateOperationIfUnchanged(long id, String payloadJson, long expectedUpdatedAt, String state, int attempts, long nextAt, String error, long now);
        @Query("DELETE FROM sync_operations WHERE id = :id AND payloadJson = :payloadJson AND updatedAt = :updatedAt")
        void deleteOperationIfUnchanged(long id, String payloadJson, long updatedAt);
        @Query("SELECT COUNT(*) FROM sync_operations WHERE state IN ('pending','retrying')") int pendingCount();
        @Query("SELECT COUNT(*) FROM sync_operations WHERE state = 'conflict'") int conflictCount();
        @Query("SELECT * FROM sync_operations WHERE accountId = :accountId AND profileId = :profileId ORDER BY createdAt DESC")
        List<SyncOperationRow> syncOperations(String accountId, String profileId);
        @Query("SELECT COUNT(*) FROM sync_operations WHERE accountId = :accountId AND profileId = :profileId AND state = 'retrying'")
        int retryingCount(String accountId, String profileId);
        @Query("UPDATE sync_operations SET state = 'pending', nextAttemptAt = :now, updatedAt = :now WHERE state = 'retrying' AND nextAttemptAt <= :now")
        void resetRetries(long now);
        @Query("SELECT value FROM migration_state WHERE `key` = :key LIMIT 1") String migrationValue(String key);
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putMigrationState(MigrationStateRow row);
    }
}
