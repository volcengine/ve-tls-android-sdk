package com.volcengine.model;

public class Credentials {
    private String accessKeyID;
    private String secretAccessKey;
    private String service; private String region; private String sessionToken;
    public Credentials() {}
    public Credentials(String region, String service) { this.region = region; this.service = service; }
    public String getAccessKeyID() { return accessKeyID; }
    public void setAccessKeyID(String accessKeyID) { this.accessKeyID = accessKeyID; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
}
