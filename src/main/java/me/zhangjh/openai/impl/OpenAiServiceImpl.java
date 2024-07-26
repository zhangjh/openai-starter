package me.zhangjh.openai.impl;

import com.alibaba.fastjson2.JSONObject;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.util.BinaryData;
import com.azure.core.util.IterableStream;
import lombok.extern.slf4j.Slf4j;
import me.zhangjh.openai.constant.RoleEnum;
import me.zhangjh.openai.dto.ChatOption;
import me.zhangjh.openai.dto.ContextMessage;
import me.zhangjh.openai.dto.ImageGenerateDTO;
import me.zhangjh.openai.request.ImageGenerateRequest;
import me.zhangjh.openai.request.TextGenerateRequest;
import me.zhangjh.openai.service.OpenAiService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author zhangjh
 */
@Component
@Slf4j
public class OpenAiServiceImpl implements OpenAiService {

    @Value("${ai.chat.model}")
    private String aiChatModel;

    @Value("${ai.image.model}")
    private String aiImageModel;

    @Autowired
    private OpenAIClient imgOpenAIClient;

    @Autowired
    private OpenAIClient textOpenAIClient;

    @Override
    public ImageGenerateDTO generateImage(ImageGenerateRequest request) {
        ImageGenerationOptions imageGenerationOptions = new ImageGenerationOptions(request.getPrompt());
        ImageGenerations imageGenerations = imgOpenAIClient.getImageGenerations(aiImageModel,
                imageGenerationOptions);
        log.info("imageGenerations: {}", JSONObject.toJSONString(imageGenerations));
        List<String> imgs = new ArrayList<>();
        for (ImageGenerationData data : imageGenerations.getData()) {
            imgs.add(data.getUrl());
        }
        ImageGenerateDTO imageGenerateDTO = new ImageGenerateDTO();
        imageGenerateDTO.setPrompt(request.getPrompt());
        imageGenerateDTO.setImgs(imgs);
        imageGenerateDTO.setUserId(request.getUserId());
        return imageGenerateDTO;
    }

    @Override
    public void generateTextWithCb(TextGenerateRequest request, Function<String, Object> cb) {
        request.getChatOption().setStream(true);
        IterableStream<ChatCompletions> chatCompletionsStream =
                this.generateTextStream(request);
        // 下面这段可以在调用端自行处理
        for (ChatCompletions chatCompletion : chatCompletionsStream) {
            if (CollectionUtils.isNotEmpty(chatCompletion.getChoices())) {
                ChatChoice choice = chatCompletion.getChoices().get(0);
                if (choice.getDelta() != null) {
                    String content = choice.getDelta().getContent();
                    if (content != null) {
                        cb.apply(content);
                    }
                }
            }
        }
    }

    @Override
    public ChatCompletions generateTextWithFunctionCall(TextGenerateRequest request) throws Exception {
        ChatOption chatOption = request.getChatOption();
        String callFunctionClass = chatOption.getCallFunctionClass();
        List<String> callFunctionMethodList = chatOption.getCallFunctionMethodList();
        Assert.isTrue(StringUtils.isNotEmpty(callFunctionClass), "function call接口必须传递调用函数所在类名");
        Assert.isTrue(CollectionUtils.isNotEmpty(callFunctionMethodList), "function call接口必须传递调用函数名列表");
        String userMessage = request.getUserMessage();

        List<FunctionDefinition> definitions = getFunctionDefinitionList(callFunctionClass, callFunctionMethodList);
        Object instance = Class.forName(callFunctionClass).getDeclaredConstructor().newInstance();
        Method[] methods = instance.getClass().getDeclaredMethods();

        List<ChatRequestMessage> messages = new ArrayList<>();
        messages.add(new ChatRequestUserMessage(userMessage));
        ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
        options.setFunctions(definitions)
                .setFunctionCall(FunctionCallConfig.AUTO)
                .setMaxTokens(chatOption.getMaxTokens())
                .setTemperature(chatOption.getTemperature())
                .setStream(false);
        ChatCompletions completions = textOpenAIClient.getChatCompletions(aiChatModel, options);
        ChatChoice choice = completions.getChoices().get(0);
        while (CompletionsFinishReason.FUNCTION_CALL.equals(choice.getFinishReason())) {
            FunctionCall functionCall = choice.getMessage().getFunctionCall();
            String functionName = functionCall.getName();
            String functionArguments = functionCall.getArguments();
            log.info("functionName: {}, functionArguments: {}", functionName, functionArguments);
            String result = "";
            for (Method method : methods) {
                if(method.getName().equals(functionName)) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    Object functionArgumentsObj = JSONObject.parseObject(functionArguments, parameterTypes[0]);
                    result = method.invoke(instance, functionArgumentsObj).toString();
                }
            }
            Assert.isTrue(StringUtils.isNotEmpty(result), "function call failed");
            ChatRequestFunctionMessage functionMessage = new ChatRequestFunctionMessage(functionName, result);
            messages.add(functionMessage);
            completions = textOpenAIClient.getChatCompletions(aiChatModel, options);
            ChatChoice newChoice = completions.getChoices().get(0);
            // 循环保护，如果函数调用结果和上次相同，则说明没有新函数调用退出循环
            if(JSONObject.toJSONString(newChoice).equals(JSONObject.toJSONString(choice))) {
                break;
            }
            choice = newChoice;
        }
        return completions;
    }

    @Override
    public void generateTextStream(TextGenerateRequest request, HttpServletResponse response) {
        IterableStream<ChatCompletions> chatCompletions = this.generateTextStream(request);
        for (ChatCompletions chatCompletion : chatCompletions) {
            handleCompletions(chatCompletion, response);
        }
    }

    private IterableStream<ChatCompletions> generateTextStream(TextGenerateRequest request) {
        String userMessage = request.getUserMessage();
        Assert.isTrue(StringUtils.isNotEmpty(userMessage), "输入问题内容为空");
        Assert.isTrue(request.getChatOption().getStream(), "流式接口stream必须为true");
        ChatCompletionsOptions completionsOptions = generateChatCompletions(request);
        // 流式问答
        completionsOptions.setStream(true);
        return textOpenAIClient.getChatCompletionsStream(aiChatModel, completionsOptions);
    }

    private ChatCompletionsOptions generateChatCompletions(TextGenerateRequest request) {
        List<ChatRequestMessage> messages = new ArrayList<>();
        if(StringUtils.isNotEmpty(request.getSystemMessage())) {
            messages.add(new ChatRequestSystemMessage(request.getSystemMessage()));
        }
        if(CollectionUtils.isNotEmpty(request.getContextMessages())) {
            for (ContextMessage contextMessage : request.getContextMessages()) {
                RoleEnum role = contextMessage.getRole();
                String content = contextMessage.getContent();
                if (role == RoleEnum.USER) {
                    messages.add(new ChatRequestUserMessage(content));
                } else if (role == RoleEnum.ASSISTANT) {
                    messages.add(new ChatRequestAssistantMessage(content));
                } else {
                    throw new RuntimeException("错误的上下文角色");
                }
            }
        }
        messages.add(new ChatRequestUserMessage(request.getUserMessage()));
        ChatCompletionsOptions completionsOptions = new ChatCompletionsOptions(messages);
        if(request.getChatOption().getTemperature() != null) {
            completionsOptions.setTemperature(request.getChatOption().getTemperature());
        }
        if(request.getChatOption().getMaxTokens() != null) {
            completionsOptions.setMaxTokens(request.getChatOption().getMaxTokens());
        }
        if(request.getChatOption().getN() != null) {
            completionsOptions.setN(request.getChatOption().getN());
        }
        if(StringUtils.isNotEmpty(request.getChatOption().getUser())) {
            completionsOptions.setUser(request.getChatOption().getUser());
        }
        completionsOptions.setStream(request.getChatOption().getStream());
        return completionsOptions;
    }

    private List<FunctionDefinition> getFunctionDefinitionList(String callFunctionClass,
                                                               List<String> methodNames) throws Exception {
        List<FunctionDefinition> functions = new ArrayList<>();
        Object instance = Class.forName(callFunctionClass).getDeclaredConstructor().newInstance();
        Method[] methods = instance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if(methodNames.contains(method.getName())) {
                FunctionDefinition definition = new FunctionDefinition(methodName);
                definition.setParameters(getFunctionSchema(method));
                functions.add(definition);
            }
        }
        return functions;
    }

    // 当前function calling的函数只支持json string入参，可以在函数接受参数后自行转换
    private BinaryData getFunctionSchema(Method method) {
        Parameter[] parameters = method.getParameters();
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> parameterMap = new HashMap<>();
        for (Parameter parameter : parameters) {
            // parameter是对象，反射获取该对象的字段
            for (Field field : parameter.getType().getDeclaredFields()) {
                parameterMap.put(field.getName(), new HashMap<String, Object>() {{
                    put("type", "string");
                }});
            }
        }
        schema.put("properties", parameterMap);
        return BinaryData.fromObject(schema);
    }

    private void handleCompletions(ChatCompletions chatCompletion, HttpServletResponse response) {
        if (CollectionUtils.isNotEmpty(chatCompletion.getChoices())) {
            ChatChoice choice = chatCompletion.getChoices().get(0);
            if (choice.getDelta() != null) {
                String content = choice.getDelta().getContent();
                if (content != null) {
                    try {
                        response.getWriter().write(content);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

}
