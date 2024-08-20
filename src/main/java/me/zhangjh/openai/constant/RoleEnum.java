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

    public static RoleEnum getRoleEnum(String role) {
        for (RoleEnum roleEnum : RoleEnum.values()) {
            if (roleEnum.getRole().equals(role)) {
                return roleEnum;
            }
        }
        return null;
    }
}
