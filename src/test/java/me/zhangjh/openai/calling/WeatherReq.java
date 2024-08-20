package me.zhangjh.openai.calling;

import lombok.Data;
import me.zhangjh.openai.annotation.FieldDesc;

/**
 * @author zhangjh
 */
@Data
public class WeatherReq {

    @FieldDesc(value = "城市名称", required = "true")
    private String city;
}
