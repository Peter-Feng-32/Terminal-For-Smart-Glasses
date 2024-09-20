package com.termux.app.remembrance_agent;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "documents.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_DOCUMENTS = "documents";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_FTS4_TABLE = "CREATE VIRTUAL TABLE " + TABLE_DOCUMENTS + " USING fts4(title TEXT, content TEXT)";
        db.execSQL(CREATE_FTS4_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOCUMENTS);
        onCreate(db);
    }

    public void insertDocument(String title, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_CONTENT, content);
        db.insert(TABLE_DOCUMENTS, null, values);
        db.close();
    }

    public List<Document> searchDocuments(String query) {
        List<Document> results = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String sqlQuery = "SELECT snippet(fts_table, '', '', '...', 5) FROM " + TABLE_DOCUMENTS + " WHERE content MATCH ? ORDER BY rank";
        String[] selectionArgs = new String[]{query, query};

        Cursor cursor = db.rawQuery(sqlQuery, selectionArgs);

        if (cursor.moveToFirst()) {
            do {
                String title = cursor.getString(cursor.getColumnIndex(COLUMN_TITLE));
                String content = cursor.getString(cursor.getColumnIndex(COLUMN_CONTENT));
                results.add(new Document(title, content));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();

        return results;
    }
}
