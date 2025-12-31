package com.volcengine.model.tls.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.volcengine.model.Header;
@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class CloseKafkaConsumerResponse extends CommonResponse {
    public CloseKafkaConsumerResponse(Header[] headers) { super(headers); }
}
