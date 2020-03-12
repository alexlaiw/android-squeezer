package uk.org.ngo.squeezer.download;

import android.content.Context;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.EnumWithText;
import uk.org.ngo.squeezer.framework.Item;

public enum DownloadFilenameStructure implements EnumWithText{
    NUMBER_TITLE(R.string.download_filename_structure_number_title) {
//        @Override
//        public String get(Song song) {
//            return formatTrackNumber(song.getTrackNum()) + " - " + song.getName();
//        }
    },
    ARTIST_TITLE(R.string.download_filename_structure_artist_title) {
//        @Override
//        public String get(Song song) {
//            return song.getArtist() + " - " + song.getName();
//        }
    },
    ARTIST_NUMBER_TITLE(R.string.download_filename_structure_artist_number_title) {
//        @Override
//        public String get(Song song) {
//            return song.getArtist() + " - " + formatTrackNumber(song.getTrackNum()) + " - " + song.getName();
//        }
    },
    ALBUMARTIST_NUMBER_TITLE(R.string.download_filename_structure_albumartist_number_title) {
//        @Override
//        public String get(Song song) {
//            return song.getAlbum().getArtist() + " - " + formatTrackNumber(song.getTrackNum()) + " - " + song.getName();
//        }
    },
    TITLE(R.string.download_filename_structure_title) {
//        @Override
//        public String get(Song song) {
//            return song.getName();
//        }
    },
    NUMBER_DOT_ARTIST_TITLE(R.string.download_filename_structure_number_dot_artist_title) {
//        @Override
//        public String get(Song song) {
//            return formatTrackNumber(song.getTrackNum()) + ". " + song.getArtist() + " - " + song.getName();
//        }
    };

    private final int labelId;

    DownloadFilenameStructure(int labelId) {
        this.labelId = labelId;
    }

    @Override
    public String getText(Context context) {
        return context.getString(labelId);
    }

//    public abstract String get(Song song);
    public String get(Item song) {
        return song.getName();
    }

    private static String formatTrackNumber(int trackNumber) {
       return String.format("%02d", trackNumber);
    }
}