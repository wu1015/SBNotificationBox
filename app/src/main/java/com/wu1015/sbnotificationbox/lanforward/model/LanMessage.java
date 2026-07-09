package com.wu1015.sbnotificationbox.lanforward.model;

/**
 * 局域网消息实体。
 * 支持文字、图片、文件三种类型。
 */
public class LanMessage {

    public enum Type {
        TEXT,       // 文字消息（直接显示）
        IMAGE,      // 图片消息（需确认接收）
        FILE        // 文件消息（需确认接收）
    }

    public enum Direction {
        SENT,       // 我发送的
        RECEIVED    // 我接收的
    }

    private Type type;
    private Direction direction;
    private String deviceName;
    private String content;        // 文字内容
    private String fileName;       // 文件名（图片/文件）
    private String filePath;       // 本地存储路径（接收后）
    private long fileSize;         // 文件大小（字节）
    private long timestamp;
    private boolean fileReceived;  // 文件是否已接收

    // === 文字消息构造 ===
    public static LanMessage createText(String deviceName, String content, Direction direction) {
        LanMessage msg = new LanMessage();
        msg.type = Type.TEXT;
        msg.deviceName = deviceName;
        msg.content = content;
        msg.direction = direction;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    // === 图片/文件消息构造 ===
    public static LanMessage createFile(String deviceName, String fileName,
                                        long fileSize, Type type, Direction direction) {
        LanMessage msg = new LanMessage();
        msg.type = type;
        msg.deviceName = deviceName;
        msg.fileName = fileName;
        msg.fileSize = fileSize;
        msg.direction = direction;
        msg.timestamp = System.currentTimeMillis();
        msg.fileReceived = false;
        return msg;
    }

    public LanMessage() {}

    // Getters & Setters
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isFileReceived() { return fileReceived; }
    public void setFileReceived(boolean fileReceived) { this.fileReceived = fileReceived; }

    /** 用于列表显示的摘要 */
    public String getSummary() {
        switch (type) {
            case TEXT:
                return (content != null && content.length() > 50)
                        ? content.substring(0, 50) + "…" : content;
            case IMAGE:
                return "[Image] " + fileName;
            case FILE:
                return "[File] " + fileName + " (" + formatSize(fileSize) + ")";
        }
        return "";
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}
