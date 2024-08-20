package me.zhangjh.openai.calling;

import lombok.Data;
import me.zhangjh.openai.annotation.FieldDesc;

/**
 * @author zhangjh
 */
@Data
public class TimeReq {

    @FieldDesc(value = "时区", required = "true")
    private String timezone;
}