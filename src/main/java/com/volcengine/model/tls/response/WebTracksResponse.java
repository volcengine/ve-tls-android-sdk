package com.volcengine.model.tls.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.volcengine.model.Header;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class WebTracksResponse extends CommonResponse {
    public WebTracksResponse(Header[] headers) {
        super(headers);
    }
}
