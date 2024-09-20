package com.termux.app.remembrance_agent;


import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class DocumentIndexer {
    DatabaseHelper databaseHelper;
    public DocumentIndexer(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    File sharedDir = Environment.getExternalStorageDirectory();


    public void findDocuments() {
        // Specify the directory path
        File directory = new File(sharedDir + "/test_docs");

        // Check if the directory exists and is a directory
        if (directory.exists() && directory.isDirectory()) {
            // Get the list of files in the directory
            File[] files = directory.listFiles();

            // Iterate through the list of files
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        insertDocument(readDocument(file));
                    } else if (file.isDirectory()) {
                    }
                }
            }
        } else {
            System.out.println("The specified path is not a directory or does not exist.");
        }
    }

    public static Document readDocument(File file) {
        StringBuilder contentBuilder = new StringBuilder();
        String title = null;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            title = file.getName(); // Read the name as the title

            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append(System.lineSeparator()); // Append subsequent lines to content
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (title != null) {
            return new Document(title, contentBuilder.toString().trim());
        } else {
            return null; // Return null if the title could not be read
        }
    }

    public void insertDocument(Document document) {
        databaseHelper.insertDocument(document.getTitle(), document.getContent());
    }

}
