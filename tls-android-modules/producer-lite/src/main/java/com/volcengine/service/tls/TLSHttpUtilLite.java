package com.volcengine.service.tls;

import com.volcengine.model.ApiInfo;
import com.volcengine.model.NameValuePair;
import com.volcengine.model.ServiceInfo;
import com.volcengine.service.BaseServiceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.volcengine.model.tls.Const.PUT_LOGS;
import com.volcengine.util.Const;

public class TLSHttpUtilLite extends BaseServiceImpl {
    public TLSHttpUtilLite(ServiceInfo info, Map<String, ApiInfo> apiInfoList) { super(info, apiInfoList); }

    public static final Map<String, ApiInfo> API_INFO_LIST = new HashMap<String, ApiInfo>() {
        {
            put(PUT_LOGS, new ApiInfo(
                    new HashMap<String, Object>() {
                        {
                            put(Const.Method, Const.POST);
                            put(Const.Path, PUT_LOGS);
                            put(Const.Query, new ArrayList<NameValuePair>() { });
                            put(Const.CONNECTION_TIMEOUT, 15000);
                            put(Const.SOCKET_TIMEOUT, 15000);
                        }
                    }
            ));
        }
    };
}
