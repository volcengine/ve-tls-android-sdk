package com.volcengine.model.tls;

public class LogContent {
    private String key;
    private String value;
    public LogContent() {}
    public LogContent(String key, String value) { this.key = key; this.value = value; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
