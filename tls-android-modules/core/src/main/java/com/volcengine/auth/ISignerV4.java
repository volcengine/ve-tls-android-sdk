package com.volcengine.auth;

import com.volcengine.model.Credentials;
import com.volcengine.model.RequestParam;
import com.volcengine.model.SignRequest;

public interface ISignerV4 {
    SignRequest getSignRequest(RequestParam requestParam, Credentials credentials) throws Exception;
}
