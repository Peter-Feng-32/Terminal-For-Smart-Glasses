package com.termux.app.tooz;

import java.nio.charset.StandardCharsets;

public class FrameBlock {
    private int id;
    private int x;
    private int y;
    private boolean overlay;
    private boolean important;
    private String format;
    private int timeToLive;
    private int loop;

    public FrameBlock(int id) {
        this.id = id;
        this.x = 0;
        this.y = 0;
        this.overlay = false;
        this.important = false;
        this.format = "jpeg";
        this.timeToLive = -1;
        this.loop = 1;
    }

    public FrameBlock(int id, int x, int y, boolean overlay, boolean important, String format, int timeToLive, int loop) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.overlay = overlay;
        this.important = important;
        this.format = format;
        this.timeToLive = timeToLive;
        this.loop = loop;
    }

    public void configure(int x, int y, boolean overlay, boolean important, String format, int timeToLive, int loop) {
        this.x = x;
        this.y = y;
        this.overlay = overlay;
        this.important = important;
        this.format = format;
        this.timeToLive = timeToLive;
        this.loop = loop;
    }

    public byte[] serialize() {
        String frameIDBlockString = String.format(
            "{\"frameId\":%d,\"x\":%d,\"y\":%d,\"overlay\":%b,\"timeToLive\":%d,\"important\":%b,\"format\":%s,\"loop\":%d}",
            id, x, y, overlay, timeToLive, important, format, loop
        );
        return frameIDBlockString.getBytes(StandardCharsets.UTF_8);
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setOverlay(boolean overlay) {
        this.overlay = overlay;
    }

    public void setImportant(boolean important) {
        this.important = important;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }

    public void setLoop(int loop) {
        this.loop = loop;
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isOverlay() {
        return overlay;
    }

    public boolean isImportant() {
        return important;
    }

    public String getFormat() {
        return format;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public int getLoop() {
        return loop;
    }

}
