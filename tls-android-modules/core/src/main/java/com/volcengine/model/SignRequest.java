package com.volcengine.model;

public class SignRequest {
    private String xDate;
    private String xNotSignBody;
    private String xCredential;
    private String xAlgorithm;
    private String xSignedHeaders;
    private String xSignedQueries;
    private String xSignature;
    private String xSecurityToken;
    private String host;
    private String contentType;
    private String xContentSha256;
    private String authorization;

    public String getXDate() { return xDate; }
    public void setXDate(String xDate) { this.xDate = xDate; }
    public String getXNotSignBody() { return xNotSignBody; }
    public void setXNotSignBody(String xNotSignBody) { this.xNotSignBody = xNotSignBody; }
    public String getXCredential() { return xCredential; }
    public void setXCredential(String xCredential) { this.xCredential = xCredential; }
    public String getXAlgorithm() { return xAlgorithm; }
    public void setXAlgorithm(String xAlgorithm) { this.xAlgorithm = xAlgorithm; }
    public String getXSignedHeaders() { return xSignedHeaders; }
    public void setXSignedHeaders(String xSignedHeaders) { this.xSignedHeaders = xSignedHeaders; }
    public String getXSignedQueries() { return xSignedQueries; }
    public void setXSignedQueries(String xSignedQueries) { this.xSignedQueries = xSignedQueries; }
    public String getXSignature() { return xSignature; }
    public void setXSignature(String xSignature) { this.xSignature = xSignature; }
    public String getXSecurityToken() { return xSecurityToken; }
    public void setXSecurityToken(String xSecurityToken) { this.xSecurityToken = xSecurityToken; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getXContentSha256() { return xContentSha256; }
    public void setXContentSha256(String xContentSha256) { this.xContentSha256 = xContentSha256; }
    public String getAuthorization() { return authorization; }
    public void setAuthorization(String authorization) { this.authorization = authorization; }
}
