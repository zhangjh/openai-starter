package me.zhangjh.openai.dto;

import lombok.Data;

import java.util.List;

/**
 * @author zhangjh
 */
@Data
public class ChatOption {

    // 答最大token数支持4096
    private Integer maxTokens = 4096;

    // 温度默认0.7
    private Double temperature = 0.7;

    private String user;

    // 候选回答数
    private Integer n = 1;

    // 是否流式输出
    private Boolean stream = true;

    // 是否开启function call，开启时需要传递下面两个参数
    // function call函数所在的类
    private String callFunctionClass;

    // function call函数的方法名集合，支持被function call的函数只接受使用类作为方法入参
    private List<String> callFunctionMethodList;
}
