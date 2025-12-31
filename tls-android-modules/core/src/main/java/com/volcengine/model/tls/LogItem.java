package com.volcengine.model.tls;

import java.util.ArrayList;
import java.util.List;

public class LogItem {
    private long time;
    private List<LogContent> contents;
    private Integer timeNs;
    public LogItem() { this.contents = new ArrayList<>(); }
    public LogItem(long time) { this.time = time; this.contents = new ArrayList<>(); }
    public LogItem(long time, List<LogContent> contents) { this.time = time; this.contents = contents; }
    public void addContent(String key, String value) { this.addContent(new LogContent(key, value)); }
    public void addContent(LogContent content) { this.contents.add(content); }
    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }
    public List<LogContent> getContents() { return contents; }
    public void setContents(List<LogContent> contents) { this.contents = contents; }
    public Integer getTimeNs() { return timeNs; }
    public void setTimeNs(Integer timeNs) { this.timeNs = timeNs; }
}
