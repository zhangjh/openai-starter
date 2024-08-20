package me.zhangjh.openai.pojo;

import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import lombok.Data;

@Data
public class FunctionCallParam {

    private String functionName;

    private StringBuilder functionArgumentSb = new StringBuilder();

    private String toolsCallId;

    // 每一轮回调都需要构造一个对应的assistantMessage
    private ChatRequestAssistantMessage assistantMessage;
}
