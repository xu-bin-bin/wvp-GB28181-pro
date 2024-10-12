package com.genersoft.iot.vmp.streamPush.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.DynamicTask;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.media.bean.MediaInfo;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.zlm.dto.StreamAuthorityInfo;
import com.genersoft.iot.vmp.service.bean.ErrorCallback;
import com.genersoft.iot.vmp.service.bean.MessageForPushChannel;
import com.genersoft.iot.vmp.service.redisMsg.IRedisRpcService;
import com.genersoft.iot.vmp.service.redisMsg.RedisPushStreamResponseListener;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.streamPush.bean.StreamPush;
import com.genersoft.iot.vmp.streamPush.dao.StreamPushMapper;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushPlayService;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.UUID;

@Service
@Slf4j
@DS("master")
public class StreamPushPlayServiceImpl implements IStreamPushPlayService {

    @Autowired
    private StreamPushMapper streamPushMapper;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private UserSetting userSetting;
    
    @Autowired
    private DynamicTask dynamicTask;

    @Autowired
    private IRedisRpcService redisRpcService;

    @Autowired
    private RedisPushStreamResponseListener redisPushStreamResponseListener;

    @Override
    public void start(Integer id, ErrorCallback<StreamInfo> callback, String platformDeviceId, String platformName ) {
        StreamPush streamPush = streamPushMapper.queryOne(id);
        Assert.notNull(streamPush, "推流信息未找到");
        MediaInfo mediaInfo = redisCatchStorage.getPushListItem(streamPush.getApp(), streamPush.getStream());
        if (mediaInfo != null) {
            String callId = null;
            StreamAuthorityInfo streamAuthorityInfo = redisCatchStorage.getStreamAuthorityInfo(streamPush.getApp(), streamPush.getStream());
            if (streamAuthorityInfo != null) {
                callId = streamAuthorityInfo.getCallId();
            }
            callback.run(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), mediaServerService.getStreamInfoByAppAndStream(mediaInfo.getMediaServer(),
                    streamPush.getApp(), streamPush.getStream(), mediaInfo, callId));
            return;
        }
        Assert.isTrue(streamPush.isStartOfflinePush(), "通道未推流");
        // 发送redis消息以使设备上线，流上线后被
        log.info("[ app={}, stream={} ]通道未推流，发送redis信息控制设备开始推流", streamPush.getApp(), streamPush.getStream());
        MessageForPushChannel messageForPushChannel = MessageForPushChannel.getInstance(1,
                streamPush.getApp(), streamPush.getStream(), streamPush.getGbDeviceId(), platformDeviceId,
                platformName, userSetting.getServerId(), null);
        redisCatchStorage.sendStreamPushRequestedMsg(messageForPushChannel);
        // 设置超时
        String timeOutTaskKey = UUID.randomUUID().toString();
        dynamicTask.startDelay(timeOutTaskKey, () -> {
            redisRpcService.unPushStreamOnlineEvent(streamPush.getApp(), streamPush.getStream());
            log.info("[ app={}, stream={} ] 等待设备开始推流超时", streamPush.getApp(), streamPush.getStream());
            callback.run(ErrorCode.ERROR100.getCode(), "timeout", null);

        }, userSetting.getPlatformPlayTimeout());
        //
        long key = redisRpcService.onStreamOnlineEvent(streamPush.getApp(), streamPush.getStream(), (streamInfo) -> {
            dynamicTask.stop(timeOutTaskKey);
            if (streamInfo == null) {
                log.warn("等待推流得到结果未空： {}/{}", streamPush.getApp(), streamPush.getStream());
                callback.run(ErrorCode.ERROR100.getCode(), "fail", null);
            }else {
                callback.run(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), streamInfo);
            }
        });
        // 添加回复的拒绝或者错误的通知
        // redis消息例如： PUBLISH VM_MSG_STREAM_PUSH_RESPONSE  '{"code":1,"msg":"失败","app":"1","stream":"2"}'
        redisPushStreamResponseListener.addEvent(streamPush.getApp(), streamPush.getStream(), response -> {
            if (response.getCode() != 0) {
                dynamicTask.stop(timeOutTaskKey);
                redisRpcService.unPushStreamOnlineEvent(streamPush.getApp(), streamPush.getStream());
                redisRpcService.removeCallback(key);
                callback.run(response.getCode(), response.getMsg(), null);
            }
        });
    }
}
