package com.termux.app.remembrance_agent;

import java.util.List;

public class InformationRetriever {
    DatabaseHelper databaseHelper;
    public InformationRetriever(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    public String retrieve (String context) {
        List<Document> documents = this.databaseHelper.searchDocuments(context);

        if (documents.size() > 0) {
            return documents.get(0).getTitle();
        } else{
            return null;
        }
    }
}
