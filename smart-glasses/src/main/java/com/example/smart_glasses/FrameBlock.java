package com.example.smart_glasses;

public class FrameBlock {
    private int framesSent;
    private int x;
    private int y;
    private boolean overlay;
    private boolean important;
    private String format;
    private int timeToLive;
    private int loop;

    public FrameBlock() {
        this.x = 0;
        this.y = 0;
        this.overlay = false;
        this.important = false;
        this.format = "jpeg";
        this.timeToLive = -1;
        this.loop = 1;
    }

    public FrameBlock(int x, int y, boolean overlay, boolean important, String format, int timeToLive, int loop) {
        this.x = x;
        this.y = y;
        this.overlay = overlay;
        this.important = important;
        this.format = format;
        this.timeToLive = timeToLive;
        this.loop = loop;
    }


}
