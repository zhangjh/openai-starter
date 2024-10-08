package me.zhangjh.openai;

import lombok.extern.slf4j.Slf4j;
import me.zhangjh.openai.calling.CallFunction;
import me.zhangjh.openai.pojo.ChatOption;
import me.zhangjh.openai.pojo.ImageGenerateDTO;
import me.zhangjh.openai.request.ImageGenerateRequest;
import me.zhangjh.openai.request.TextGenerateRequest;
import me.zhangjh.openai.service.OpenAiService;
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
        openAiService.generateTextWithToolsCallStream(request, content -> {
            log.info(content);
            return null;
        });
    }

    @Test
    public void generateTextWithFunctionCallTest() throws Exception {
        TextGenerateRequest request = new TextGenerateRequest();
        request.setUserMessage("现在几点了？今天杭州天气怎么样？");
        request.setNeedJsonFormatWhenToolsCalled(false);
        ChatOption chatOption = new ChatOption();
        chatOption.setCallFunctionClass(CallFunction.class.getName());
        chatOption.setCallFunctionMethodList(Arrays.asList("getCurrentTime", "getWeather"));
        request.setChatOption(chatOption);
        openAiService.generateTextWithToolsCallStream(request, content -> {
            log.info("content: {}", content);
            return null;
        });
    }
}
