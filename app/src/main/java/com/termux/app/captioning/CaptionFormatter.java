package com.termux.app.captioning;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class CaptionFormatter
{
    private int width;
    private int height;
    private int currBufferIndex;

    private char[][] buffer;

    public CaptionFormatter() {
        width = 20;
        height = 3;
        buffer = new char[height][width];
    }

    private void resetBuffer() {
        for(int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                buffer[i][j] = ' ';
            }
        }
        currBufferIndex = 0;
    }

    public void formatCaption(String caption) {
        final int MAX_LENGTH_TO_WRITE = width * (height - 1);

        String captionSubstring;
        if(caption.length() < MAX_LENGTH_TO_WRITE) {
            captionSubstring = caption;
        } else {
            int i = caption.length() - MAX_LENGTH_TO_WRITE;
            while(caption.getBytes(StandardCharsets.UTF_8)[i] != ' ') {
                i++;
            }
            captionSubstring = caption.substring(i);
        }
        //Get only enough words to fit on 3 lines.
        //Split substring into words
        String[] splited = captionSubstring.split(" ");
        ArrayList<String> toSendArray = new ArrayList<>();
        int numCharsWritten = 0;
        int numCharsWrittenInRow = 0;
        int currIndex = 0;
        while(currIndex < splited.length && numCharsWritten < MAX_LENGTH_TO_WRITE) {
            String currWord = splited[currIndex];
            if(currWord.length() > width) {
                //If the word is bigger than a row, just send it.
                toSendArray.add(currWord);
                numCharsWritten += currWord.length();
                numCharsWrittenInRow = (numCharsWrittenInRow + currWord.length()) % width;
                if(numCharsWrittenInRow % width != 0) {
                    toSendArray.add(" ");
                    numCharsWrittenInRow = (numCharsWrittenInRow + 1) % width;
                    numCharsWritten++;
                }
            } else {
                int numCharsRemainingInRow = width - numCharsWrittenInRow;
                if(currWord.length() > numCharsRemainingInRow) {
                    //Move to next row.
                    String spaces = "";
                    for(int i = 0; i < numCharsRemainingInRow; i++) {
                        spaces = spaces + " ";
                        numCharsWritten++;
                    }
                    toSendArray.add(spaces);
                    numCharsWrittenInRow = 0;
                }
                toSendArray.add(currWord);
                numCharsWritten += currWord.length();
                numCharsWrittenInRow = (numCharsWrittenInRow + currWord.length()) % width;
                if(numCharsWrittenInRow % width != 0) {
                    toSendArray.add(" ");
                    numCharsWrittenInRow = (numCharsWrittenInRow + 1) % width;
                    numCharsWritten++;
                }
            }
            currIndex++;
        }

        resetBuffer();
        for(String str : toSendArray) {
            for(int i = 0; i < str.length(); i++) {
                buffer[currBufferIndex/width][currBufferIndex%width] = str.charAt(i);
                currBufferIndex++;
            }
        }
        printBuffer();

    }

    public void printBuffer() {
        for(int i = 0; i < buffer.length; i++) {
            String row = "";
            for(int j = 0; j < buffer[0].length; j++) {
                row += buffer[i][j];
            }
            Log.w("Buffer " + i, row);
        }
    }

    public char[][] getBuffer() {
        return buffer;
    }

    public void setWidth(int width) {
        this.width = width;
        buffer = new char[height][width];
        resetBuffer();
    }

    public void setHeight(int height) {
        this.height = height;
        buffer = new char[height][width];
        resetBuffer();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
