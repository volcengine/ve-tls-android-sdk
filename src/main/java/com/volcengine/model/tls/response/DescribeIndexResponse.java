package com.volcengine.model.tls.response;

import com.alibaba.fastjson.annotation.JSONField;
import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.KeyValueInfo;
import com.volcengine.model.tls.ValueInfo;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.util.StringUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.volcengine.model.Header;

import java.util.List;

import static com.volcengine.model.tls.Const.*;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class DescribeIndexResponse extends CommonResponse {

    @JSONField(name = TOPIC_ID)
    String topicId;
    @JSONField(name = FULL_TEXT)
    FullTextInfo fullTextInfo;
    @JSONField(name = KEY_VALUE)
    List<KeyValueInfo> keyValue;
    @JSONField(name = CREATE_TIME)
    String createTime;
    @JSONField(name = MODIFY_TIME)
    String modifyTime;

    public DescribeIndexResponse(Header[] headers) {
        super(headers);
    }

    @Override
    public DescribeIndexResponse deSerialize(byte[] data, Class clazz) throws LogException {
        DescribeIndexResponse response = (DescribeIndexResponse) super.deSerialize(data, clazz);
        this.setTopicId(response.getTopicId());
//        html unescape
        FullTextInfo fullTextInfo = response.getFullTextInfo();
        if (fullTextInfo != null && StringUtils.isNotEmpty(fullTextInfo.getDelimiter())) {
            String delimiter = fullTextInfo.getDelimiter();
            fullTextInfo.setDelimiter(StringUtils.replaceWhiteSpaceCharacter(delimiter));
        }
        this.setFullTextInfo(fullTextInfo);
        List<KeyValueInfo> keyValue = response.getKeyValue();
        if (keyValue != null && keyValue.size() > 0) {
            for (KeyValueInfo kv : keyValue) {
                ValueInfo value = kv.getValue();
                if (value != null && StringUtils.isNotEmpty(value.getDelimiter())) {
                    String delimiter = value.getDelimiter();
                    value.setDelimiter(StringUtils.replaceWhiteSpaceCharacter(delimiter));
                }
            }
        }
        this.setKeyValue(keyValue);
        this.setCreateTime(response.getCreateTime());
        this.setModifyTime(response.getModifyTime());
        return this;
    }
}
