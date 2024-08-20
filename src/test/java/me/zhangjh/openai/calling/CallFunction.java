package me.zhangjh.openai.calling;

import lombok.extern.slf4j.Slf4j;
import me.zhangjh.openai.annotation.FunDesc;

import java.time.LocalTime;

/**
 * @author zhangjh
 */
@Slf4j
public class CallFunction {

    @FunDesc("获取指定城市天气")
    public String getWeather(WeatherReq req) {
        log.info("getWeather: {}", req);
//        WeatherReq weatherReq = JSONObject.parseObject(req, WeatherReq.class);
        return switch (req.getCity()) {
            case "南京" -> "阴天，25°。";
            case "北京" -> "阴天，23°。";
            case "杭州" -> "晴天，40°";
            default -> "未知城市。";
        };
    }

    @FunDesc("获取当前时间")
    public String getCurrentTime(TimeReq req) {
//        TimeReq timeReq = JSONObject.parseObject(req, TimeReq.class);
        return "现在时间是：" + req.getTimezone() + ":" + LocalTime.now();
    }
}
