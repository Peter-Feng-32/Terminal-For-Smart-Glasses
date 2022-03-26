package com.termux.terminal;

import java.util.ArrayList;

/**
 * A class for detecting what codes are written to the terminal emulator.  Depending on this, we may write different things to the Tooz glasses.
 */
public class TerminalEmulatorChangeRecorder {
    public int codePointsEmitted = 0;
    public boolean screenCleared = false;
    public boolean possibleDontUpdate = false;
    public int newLine = 0;
    public int backSpaceCount = 0;
    public int cursorPrevRow;
    public int cursorPrevCol;
    public int cursorCurrRow;
    public int cursorCurrCol;
    public String details = "";

    //If this is set, update the entire screen regardless of the the other attributes.
    public boolean overrideChangeScreen = false;
    public ArrayList<Integer> changeX;
    public ArrayList<Integer> changeY;

    public TerminalEmulatorChangeRecorder() {
        changeX = new ArrayList<>();
        changeY = new ArrayList<>();
    }
}
