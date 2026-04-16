package com.purebeat.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.purebeat.model.Folder;
import com.purebeat.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicScanner {

    private ContentResolver contentResolver;

    public MusicScanner(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    public List<Song> scanAllSongs() {
        List<Song> songs = new ArrayList<>();

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                          MediaStore.Audio.Media.DURATION + " > 10000";

        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor cursor = contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        );

        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                String album = cursor.getString(albumColumn);
                long duration = cursor.getLong(durationColumn);
                long dateAdded = cursor.getLong(dateAddedColumn);
                long size = cursor.getLong(sizeColumn);
                String mimeType = cursor.getString(mimeTypeColumn);
                String data = cursor.getString(dataColumn);

                // 处理编码问题 - 尝试检测并修复乱码
                title = fixEncoding(title);
                artist = fixEncoding(artist);
                album = fixEncoding(album);

                if (TextUtils.isEmpty(title)) {
                    // 如果标题为空，尝试从文件名提取
                    title = extractTitleFromPath(data);
                }
                if (TextUtils.isEmpty(title)) {
                    title = "Unknown";
                }
                if (TextUtils.isEmpty(artist)) {
                    artist = "Unknown Artist";
                }
                if (TextUtils.isEmpty(album)) {
                    album = "Unknown Album";
                }
                if (TextUtils.isEmpty(mimeType)) {
                    mimeType = "audio/mpeg";
                }

                Uri contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                Uri albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), id);

                // Extract folder information
                String folderPath = "";
                String folderName = "Unknown Folder";
                if (!TextUtils.isEmpty(data)) {
                    File file = new File(data);
                    File parent = file.getParentFile();
                    if (parent != null) {
                        folderPath = parent.getAbsolutePath();
                        folderName = parent.getName();
                    }
                }

                Song song = new Song(
                    id, title, artist, album, duration,
                    contentUri, albumArtUri, dateAdded, size, mimeType,
                    folderPath, folderName
                );

                songs.add(song);
            }

            cursor.close();
        }

        return songs;
    }

    /**
     * 尝试修复编码问题
     */
    private String fixEncoding(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // 尝试检测是否为乱码 (常见GBK编码的中文被当成ISO-8859-1或其他编码读取)
        try {
            // 如果字符串包含明显的乱码特征字符，尝试转换
            byte[] bytes = input.getBytes("ISO-8859-1");
            String converted = new String(bytes, "UTF-8");
            // 如果转换后变成问号或空，说明原来可能是有效的UTF-8
            if (converted.contains("?") || converted.trim().isEmpty()) {
                return input;
            }
            // 如果转换后内容不同，可能是编码问题
            if (!converted.equals(input)) {
                return converted;
            }
        } catch (Exception e) {
            // 忽略转换错误
        }
        
        return input;
    }

    /**
     * 从文件路径提取歌曲标题
     */
    private String extractTitleFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            // 获取文件名（不含扩展名）
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                String filename = path.substring(lastSlash + 1);
                int dot = filename.lastIndexOf('.');
                if (dot > 0) {
                    return filename.substring(0, dot);
                }
                return filename;
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }

    public Map<String, Folder> scanFolders(List<Song> songs) {
        Map<String, Folder> folderMap = new HashMap<>();

        for (Song song : songs) {
            String folderPath = song.getFolderPath();
            if (folderPath == null || folderPath.isEmpty()) {
                folderPath = "Unknown";
                song.setFolderPath(folderPath);
                song.setFolderName("Unknown Folder");
            }

            if (!folderMap.containsKey(folderPath)) {
                folderMap.put(folderPath, new Folder(folderPath, song.getFolderName()));
            }

            folderMap.get(folderPath).addSong(song);
        }

        return folderMap;
    }

    public List<Folder> getFolders(List<Song> songs) {
        Map<String, Folder> folderMap = scanFolders(songs);
        List<Folder> folders = new ArrayList<>(folderMap.values());

        // Sort folders by name
        folders.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

        return folders;
    }

    public List<Song> getSongsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        List<Song> songs = new ArrayList<>();
        StringBuilder selection = new StringBuilder();
        selection.append(MediaStore.Audio.Media._ID).append(" IN (");
        for (int i = 0; i < ids.size(); i++) {
            selection.append(ids.get(i));
            if (i < ids.size() - 1) {
                selection.append(",");
            }
        }
        selection.append(")");

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        };

        Cursor cursor = contentResolver.query(
            collection,
            projection,
            selection.toString(),
            null,
            null
        );

        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                String album = cursor.getString(albumColumn);
                long duration = cursor.getLong(durationColumn);
                long dateAdded = cursor.getLong(dateAddedColumn);
                long size = cursor.getLong(sizeColumn);
                String mimeType = cursor.getString(mimeTypeColumn);
                String data = cursor.getString(dataColumn);

                // 处理编码问题 - 尝试检测并修复乱码
                title = fixEncoding(title);
                artist = fixEncoding(artist);
                album = fixEncoding(album);

                // 如果标题为空，尝试从文件名提取
                if (TextUtils.isEmpty(title)) {
                    title = extractTitleFromPath(data);
                }
                if (TextUtils.isEmpty(title)) title = "Unknown";
                if (TextUtils.isEmpty(artist)) artist = "Unknown Artist";
                if (TextUtils.isEmpty(album)) album = "Unknown Album";
                if (TextUtils.isEmpty(mimeType)) mimeType = "audio/mpeg";

                Uri contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                Uri albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), id);

                String folderPath = "";
                String folderName = "Unknown Folder";
                if (!TextUtils.isEmpty(data)) {
                    File file = new File(data);
                    File parent = file.getParentFile();
                    if (parent != null) {
                        folderPath = parent.getAbsolutePath();
                        folderName = parent.getName();
                    }
                }

                Song song = new Song(
                    id, title, artist, album, duration,
                    contentUri, albumArtUri, dateAdded, size, mimeType,
                    folderPath, folderName
                );

                songs.add(song);
            }

            cursor.close();
        }

        return songs;
    }
}
