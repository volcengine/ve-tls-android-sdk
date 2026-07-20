package com.volcengine.auth;

public class MetaData {
    private String date;
    private String service;
    private String region;
    private String algorithm;
    private String signedHeaders;
    private String credentialScope;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getSignedHeaders() { return signedHeaders; }
    public void setSignedHeaders(String signedHeaders) { this.signedHeaders = signedHeaders; }
    public String getCredentialScope() { return credentialScope; }
    public void setCredentialScope(String credentialScope) { this.credentialScope = credentialScope; }
}
