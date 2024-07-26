package me.zhangjh.openai.constant;

import lombok.Getter;

/**
 * @author zhangjh
 */

@Getter
public enum RoleEnum {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    private final String role;

    RoleEnum(String role) {
        this.role = role;
    }
}
