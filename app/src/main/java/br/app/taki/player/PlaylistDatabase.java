package br.app.taki.player;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaylistDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "taki_library.db";

    public PlaylistDatabase(Context context) {
        super(context, DB_NAME, null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, url TEXT NOT NULL, logo TEXT NOT NULL, " +
                "group_title TEXT NOT NULL, type INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE favorites (url TEXT NOT NULL PRIMARY KEY)");
        db.execSQL("CREATE INDEX idx_items_type_id ON items(type, _id)");
        db.execSQL("CREATE INDEX idx_items_type_group ON items(type, group_title)");
        db.execSQL("CREATE INDEX idx_items_group ON items(group_title)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public interface ImportProgress {
        void update(int imported);
    }

    /** Substitui a biblioteca em uma única transação, preservando favoritos e sem carregar o M3U na RAM. */
    public int importM3u(InputStream source, ImportProgress onProgress) throws IOException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("items", null, null);
            SQLiteStatement stmt = db.compileStatement(
                    "INSERT INTO items(name,url,logo,group_title,type) VALUES(?,?,?,?,?)");
            final int[] inserted = {0};
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(source, StandardCharsets.UTF_8), 256 * 1024)) {
                M3uParser.parse(reader, entry -> {
                    stmt.clearBindings();
                    stmt.bindString(1, entry.name);
                    stmt.bindString(2, entry.url);
                    stmt.bindString(3, entry.logo);
                    stmt.bindString(4, entry.group);
                    stmt.bindLong(5, entry.type);
                    stmt.executeInsert();
                    if (++inserted[0] % 2000 == 0) onProgress.update(inserted[0]);
                });
            } finally {
                stmt.close();
            }
            if (inserted[0] == 0) throw new IOException("A lista não contém entradas IPTV válidas.");
            db.setTransactionSuccessful();
            return inserted[0];
        } finally {
            db.endTransaction();
        }
    }

    public int[] counts() {
        int[] result = new int[5];
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT type,COUNT(*) FROM items GROUP BY type", null)) {
            while (c.moveToNext()) {
                int type = c.getInt(0);
                int count = c.getInt(1);
                if (type >= 1 && type <= 4) {
                    result[type] = count;
                    result[0] += count;
                }
            }
        }
        return result;
    }

    public List<String> categories(int type) {
        List<String> result = new ArrayList<>();
        String sql = type == M3uParser.ALL
                ? "SELECT DISTINCT group_title FROM items WHERE group_title <> '' ORDER BY group_title COLLATE NOCASE"
                : "SELECT DISTINCT group_title FROM items WHERE type=? AND group_title <> '' ORDER BY group_title COLLATE NOCASE";
        String[] args = type == M3uParser.ALL ? null : new String[]{String.valueOf(type)};
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }

    public List<MediaEntry> page(int type, String category, String search,
                                 boolean favoritesOnly, int limit, int offset) {
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (type != M3uParser.ALL) {
            where.append(" AND i.type=?");
            args.add(String.valueOf(type));
        }
        if (category != null && !category.isEmpty()) {
            where.append(" AND i.group_title=?");
            args.add(category);
        }
        if (search != null && !search.trim().isEmpty()) {
            // Escapamos % _ e \\ para busca literal, inclusive ao colar nomes.
            String s = search.trim().replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            where.append(" AND i.name LIKE ? ESCAPE '\\'");
            args.add("%" + s + "%");
        }
        if (favoritesOnly) where.append(" AND f.url IS NOT NULL");
        String sql = "SELECT i._id,i.name,i.url,i.logo,i.group_title,i.type," +
                "CASE WHEN f.url IS NOT NULL THEN 1 ELSE 0 END " +
                "FROM items i LEFT JOIN favorites f ON f.url=i.url" + where +
                " ORDER BY i._id LIMIT ? OFFSET ?";
        args.add(String.valueOf(limit));
        args.add(String.valueOf(offset));
        List<MediaEntry> found = new ArrayList<>(limit);
        try (Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                found.add(new MediaEntry(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getInt(5), c.getInt(6) != 0));
            }
        }
        return found;
    }

    public void setFavorite(String url, boolean favorite) {
        SQLiteDatabase db = getWritableDatabase();
        if (favorite) {
            SQLiteStatement insert = db.compileStatement(
                    "INSERT OR IGNORE INTO favorites(url) VALUES(?)");
            try {
                insert.bindString(1, url);
                insert.executeInsert();
            } finally {
                insert.close();
            }
        } else {
            db.delete("favorites", "url=?", new String[]{url});
        }
    }
}
