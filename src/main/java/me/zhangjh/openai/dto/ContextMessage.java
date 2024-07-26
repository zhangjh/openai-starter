package me.zhangjh.openai.dto;

import lombok.Data;
import me.zhangjh.openai.constant.RoleEnum;

/**
 * @author zhangjh
 */
@Data
public class ContextMessage {

    private RoleEnum role;

    private String content;

    public ContextMessage() {
    }

    public ContextMessage(RoleEnum role, String content) {
        this.role = role;
        this.content = content;
    }
}
