package me.zhangjh.openai;

import com.alibaba.fastjson2.JSONObject;
import com.azure.ai.openai.models.ChatCompletions;
import lombok.extern.slf4j.Slf4j;
import me.zhangjh.openai.calling.CallFunction;
import me.zhangjh.openai.dto.ChatOption;
import me.zhangjh.openai.dto.ImageGenerateDTO;
import me.zhangjh.openai.request.ImageGenerateRequest;
import me.zhangjh.openai.request.TextGenerateRequest;
import me.zhangjh.openai.service.OpenAiService;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;

/**
 * @author zhangjh
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = App.class)
@Slf4j
public class AppTests {

    @Test
    public void contextLoads() {

    }

    @Autowired
    private OpenAiService openAiService;

    @Test
    public void generateImageTest() {
        ImageGenerateRequest request = new ImageGenerateRequest();
        request.setPrompt("A light cream colored mini golden doodle");
        ImageGenerateDTO imageGenerateDTO = openAiService.generateImage(request);
        log.info("imageGenerateDTO:{}", imageGenerateDTO);
    }

    @Test
    public void generateTextTest() {
        TextGenerateRequest request = new TextGenerateRequest();
        request.setUserMessage("中国的首都是哪里？");
        openAiService.generateTextWithCb(request, content -> {
            log.info(content);
            return null;
        });
    }

    @Test
    public void generateTextWithFunctionCallTest() throws Exception {
        TextGenerateRequest request = new TextGenerateRequest();
        request.setUserMessage("现在几点了？今天杭州天气怎么样？");
        ChatOption chatOption = new ChatOption();
        chatOption.setCallFunctionClass(CallFunction.class.getName());
        chatOption.setCallFunctionMethodList(Arrays.asList("getCurrentTime", "getWeather"));
        request.setChatOption(chatOption);
        ChatCompletions chatCompletions = openAiService.generateTextWithFunctionCall(request);
        log.info("chatCompletions:{}", JSONObject.toJSONString(chatCompletions));
    }

    @Test
    public void generateTextWithCb() {
        TextGenerateRequest request = new TextGenerateRequest();
        request.setUserMessage("介绍一下你自己");
        StringBuilder sb = new StringBuilder();
        openAiService.generateTextWithCb(request, content -> {
            if(StringUtils.equals("[done]", content)) {
                log.info("sb:{}", sb);
            } else {
                sb.append(content);
            }
            return null;
        });
    }
}
