package com.termux.app.captioning;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class CaptioningFormatter {
    String oldCaptionUnformatted = "";
    int indexToUpdate = 0;
    int[][] oldCaptionIndexCorrespondance;
    int rows;
    int cols;
    int numChars;

    public CaptioningFormatter(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        numChars = rows * cols;
        oldCaptionIndexCorrespondance = new int[rows][cols + 1];
        for(int i = 0; i < rows; i++) {
            for(int j = 0; j < cols + 1; j++) {
                oldCaptionIndexCorrespondance[i][j] = -1;
            }
        }
    }

    public String formatCaption(String caption) {
        String result = "";
        final int NUM_ROWS = rows;

        int ROW_LENGTH = cols;

        String captionSubstring;
        captionSubstring = caption;

        //Get only enough words to fit on 3 lines.
        //Split substring into words
        String[] splited = captionSubstring.split(" ");
        ArrayList<String> toSendArray = new ArrayList<>();
        int numCharsWritten = 0;
        int numCharsWrittenInRow = 0;
        int currIndex = 0;
        while(currIndex < splited.length) {
            String currWord = splited[currIndex];
            if(currWord.length() > ROW_LENGTH) {
                //If the word is bigger than a row, just send it.
                toSendArray.add(currWord);
                numCharsWritten += currWord.length();
                numCharsWrittenInRow = (numCharsWrittenInRow + currWord.length()) % ROW_LENGTH;
                if(numCharsWrittenInRow % ROW_LENGTH != 0) {
                    toSendArray.add(" ");
                    numCharsWrittenInRow = (numCharsWrittenInRow + 1) % ROW_LENGTH;
                    numCharsWritten++;
                }
            } else {
                int numCharsRemainingInRow = ROW_LENGTH - numCharsWrittenInRow;
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
                numCharsWrittenInRow = (numCharsWrittenInRow + currWord.length()) % ROW_LENGTH;
                if(numCharsWrittenInRow % ROW_LENGTH != 0) {
                    toSendArray.add(" ");
                    numCharsWrittenInRow = (numCharsWrittenInRow + 1) % ROW_LENGTH;
                    numCharsWritten++;
                }
            }
            currIndex++;
        }
        for(String s : toSendArray) {
            result += s;
        }

        return result;
    }

    public String process(String newCaption) {
        String x = oldCaptionUnformatted;
        String y = newCaption;
        int t = 20;
        int u = 40;

        if(x.length() < t) {
            //Old caption's length too small to compare old caption. Just directly display to screen.
            return formatCaption(y);
        }
        if(y.length() < u) {
            //New caption's length is too small to do this.  Just directly display to screen.
            return formatCaption(y);
        }

        int maxEditDistance = 0;
        int bestIndex = -1;
        String oldStringToMatch = x.substring(x.length() - t);
        for(int i = t; i <= u; i++) {
            String newStringMatch = y.substring(y.length() - i, y.length() - i + t);
            int d = editDistance(oldStringToMatch, newStringMatch);
            if(d >= maxEditDistance) {
                maxEditDistance = d;
                bestIndex = y.length() - i;
            }
        }

        String z = x.substring(0, x.length() - t) + y.substring(bestIndex);
        oldCaptionUnformatted = z;
        return formatCaption(z);
    }

    static int editDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                }
                else if (j == 0) {
                    dp[i][j] = i;
                }
                else {
                    dp[i][j] = min(dp[i - 1][j - 1]
                            + costOfSubstitution(x.charAt(i - 1), y.charAt(j - 1)),
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1);
                }
            }
        }

        return dp[x.length()][y.length()];
    }

    public static int costOfSubstitution(char a, char b) {
        return a == b ? 0 : 1;
    }

    public static int min(int... numbers) {
        return Arrays.stream(numbers)
            .min().orElse(Integer.MAX_VALUE);
    }

    public int getIndex() {
        return indexToUpdate;
    }

    public void resetSavedCaption() {
        //For use when we get a new caption, not a continuation, and want to start the screen over.
        oldCaptionUnformatted = "";
        oldCaptionIndexCorrespondance = new int[rows][cols + 1];
        for(int i = 0; i < rows; i++) {
            for(int j = 0; j < cols + 1; j++) {
                oldCaptionIndexCorrespondance[i][j] = -1;
            }
        }
    }

    public int phantomConversion(int position) {
        return (position + position / cols) % (numChars + rows);
    }

}
