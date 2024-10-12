package com.genersoft.iot.vmp.streamPush.bean;

import lombok.Data;

@Data
public class RedisPushStreamMessage {

    private String gbId;
    private String app;
    private String stream;
    private String name;
    private boolean status;

    public StreamPush buildstreamPush() {
        StreamPush push = new StreamPush();
        push.setApp(app);
        push.setStream(stream);
        push.setGbName(name);
        push.setGbDeviceId(gbId);
        push.setStartOfflinePush(true);
        push.setGbStatus(status?"ON":"OFF");
        return push;
    }
}
