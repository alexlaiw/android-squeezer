/*
 * Copyright (c) 2014 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Encapsulates the download database implementation
 */
public class DownloadDatabase {

    private static class DOWNLOAD_DATABASE {
        private static final String NAME = "download";
        private static final int VERSION = 4;

        private static class SONG {
            private static final String TABLE = "download";

            private static class COLUMNS {
                private static final String DOWNLOAD_ID = "download_id";
                private static final String FILE_NAME = "file_name";
                private static final String TITLE = "title";
                private static final String ALBUM = "album";
                private static final String ARTIST = "artist";
            }
        }
    }

    private final SQLiteDatabase db;

    public DownloadDatabase(Context context) {
        db = OpenHelper.getInstance(context).getWritableDatabase();
    }

    private static class OpenHelper  extends SQLiteOpenHelper {

        private static final Object mInstanceLock = new Object();
        private static OpenHelper mInstance;

        private OpenHelper(Context context) {
            // calls the super constructor, requesting the default cursor
            // factory.
            super(context, DOWNLOAD_DATABASE.NAME, null, DOWNLOAD_DATABASE.VERSION);
        }

        public static OpenHelper getInstance(Context context) {
            if (mInstance == null) {
                synchronized (mInstanceLock) {
                    if (mInstance == null) {
                        mInstance = new OpenHelper(context);
                    }
                }
            }
            return mInstance;
        }

        /**
         * Close download sync database helper instance and delete any data in the download database
         */
        public static void clear(Context context) {
            synchronized (mInstanceLock) {
                if (mInstance != null) {
                    mInstance.close();
                    mInstance  = null;
                }
                File databasePath = context.getDatabasePath(DOWNLOAD_DATABASE.NAME);
                databasePath.delete();
            }
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL("CREATE TABLE " + DOWNLOAD_DATABASE.SONG.TABLE + "(" +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + " INTEGER, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.FILE_NAME + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.TITLE + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.ALBUM + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.ARTIST + " TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + DOWNLOAD_DATABASE.SONG.TABLE);
            // Upgrades just creates a new database. The database keeps track of
            // active downloads, so it holds only temporary information.
            onCreate(sqLiteDatabase);
        }

    }

    /**
     * Register a download entry, so we can rename the file when it is downloaded.
     *
     * @param downloadId Download manager id
     * @param fileName Filename to use when the file is downloaded
     * @return False if we could not register the download
     */
    public boolean registerDownload(long downloadId, @NonNull String fileName, @NonNull String title, String album, String artist) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID, downloadId);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.FILE_NAME, fileName);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.TITLE, title);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.ALBUM, album);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.ARTIST, artist);
        return (db.insert(DOWNLOAD_DATABASE.SONG.TABLE, null, contentValues) != -1);
    }

    /**
     * Search for a previously registered download entry with the supplied id.
     * If an entry is found it is returned, and the download is unregistered.
     *
     * @param downloadId Download id
     * @return The registered download entry or null if not found
     */
    @Nullable
    public DownloadEntry popDownloadEntry(long downloadId) {
        DownloadEntry entry = null;

        try (Cursor cursor = db.rawQuery("select * from " + DOWNLOAD_DATABASE.SONG.TABLE +
                        " where " + DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + "=?",
                new String[]{String.valueOf(downloadId)})) {
            if (cursor.moveToNext()) {
                entry = getDownloadEntry(cursor);
            }
        }
        if (entry != null) {
            db.delete(DOWNLOAD_DATABASE.SONG.TABLE, DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + "=?",
                    new String[]{String.valueOf(downloadId)});
        }

        return entry;
    }

    public void iterateDownloadEntries(DownloadHandler callback) {
        try (Cursor cursor = db.rawQuery("select * from " + DOWNLOAD_DATABASE.SONG.TABLE, null)) {
            while (cursor.moveToNext()) {
                callback.handle(getDownloadEntry(cursor));
            }
        }
    }

    private DownloadEntry getDownloadEntry(Cursor cursor) {
        DownloadEntry entry = new DownloadEntry();
        entry.downloadId = cursor.getLong(cursor.getColumnIndex(DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID));
        entry.fileName = cursor.getString(cursor.getColumnIndex(DOWNLOAD_DATABASE.SONG.COLUMNS.FILE_NAME));
        entry.title = cursor.getString(cursor.getColumnIndex(DOWNLOAD_DATABASE.SONG.COLUMNS.TITLE));
        entry.album = cursor.getString(cursor.getColumnIndex(DOWNLOAD_DATABASE.SONG.COLUMNS.ALBUM));
        entry.artist = cursor.getString(cursor.getColumnIndex(DOWNLOAD_DATABASE.SONG.COLUMNS.ARTIST));
        return entry;
    }

    public void remove(long... downloadIds) {
        db.beginTransaction();
        try {
            for (long downloadId : downloadIds) {
                db.delete(DOWNLOAD_DATABASE.SONG.TABLE, DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + "=?",
                        new String[]{String.valueOf(downloadId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static class DownloadEntry {
        public long downloadId;
        public String fileName;
        public String title;
        public String album;
        public String artist;
    }

    public interface DownloadHandler {
        void handle(DownloadEntry entry);
    }
}
