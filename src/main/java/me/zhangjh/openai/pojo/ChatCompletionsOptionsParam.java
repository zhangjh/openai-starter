package me.zhangjh.openai.pojo;

import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;

@Data
public class ChatCompletionsOptionsParam {

    private ChatCompletionsOptions options;

    private Method[] methods;

    private Object instance;

    private List<ChatRequestMessage> messages;
}
