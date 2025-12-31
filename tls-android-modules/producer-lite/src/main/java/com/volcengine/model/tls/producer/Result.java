package com.volcengine.model.tls.producer;

import java.util.List;

public class Result {
    private boolean success;
    private List<Attempt> attempts;
    private int attemptCount;

    public Result(boolean success, List<Attempt> attempts, int attemptCount) {
        this.success = success; this.attempts = attempts; this.attemptCount = attemptCount;
    }
    public boolean isSuccess() { return success; }
    public List<Attempt> getAttempts() { return attempts; }
    public int getAttemptCount() { return attemptCount; }
}
