package me.zhangjh.openai.request;

import lombok.Data;
import me.zhangjh.openai.dto.ChatOption;
import me.zhangjh.openai.dto.ContextMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangjh
 */
@Data
public class TextGenerateRequest {

    // 可以自定义该字段，为AI定义角色身份等，说明一些注意事项等，默认为系统角色
    private String systemMessage;

    // 可以将上下文信息传给模型，保留最多十条
    private List<ContextMessage> contextMessages;

    // 本次用户问题
    private String userMessage;

    private ChatOption chatOption = new ChatOption();

    public void addContextMessage(ContextMessage contextMessage) {
        if (contextMessages == null) {
            contextMessages = new ArrayList<>();
        }
        // 如果超过十条，淘汰最旧的一条，加入当前的信息
        if (contextMessages.size() >= 10) {
            // 删除contextMessages最旧的一条
            contextMessages.remove(0);
        }
        contextMessages.add(contextMessage);
    }

}
