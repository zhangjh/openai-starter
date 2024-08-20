package me.zhangjh.openai.impl;

import com.alibaba.fastjson2.JSONObject;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.util.BinaryData;
import com.azure.core.util.IterableStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.zhangjh.openai.annotation.FieldDesc;
import me.zhangjh.openai.annotation.FunDesc;
import me.zhangjh.openai.constant.RoleEnum;
import me.zhangjh.openai.pojo.*;
import me.zhangjh.openai.request.ImageGenerateRequest;
import me.zhangjh.openai.request.TextGenerateRequest;
import me.zhangjh.openai.service.OpenAiService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Autowired
    private ApplicationContext context;

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
    public void generateTextWithToolsCallStream(TextGenerateRequest request, Function<String, Object> cb) {
        ChatCompletionsOptionsParam optionsParam = generateChatCompletionsWithFunctionCall(request);
        ChatCompletionsOptions options = optionsParam.getOptions();
        options.setStream(true);
        IterableStream<ChatCompletions> chatCompletionsStream = textOpenAIClient.getChatCompletionsStream(aiChatModel, options);

        handleToolsCallStreamResponse(chatCompletionsStream,
                optionsParam, request.getNeedStreamResponseWhenToolsCalled(),
                request.getNeedJsonFormatWhenToolsCalled(),
                false,
                cb);
    }

    @SneakyThrows
    /*
     * @param chatCompletionsStream 调用ai后返回的可迭代流式响应
     * @param optionsParam 调用ai的参数
     * @param needStreamResponseWhenToolsCalled，
     *  通常回调后的结果会输出JSON便于后续系统对接，直接流式输出没有意义，增加一个选项支持输出最终结果
     * @param needJsonFormatWhenToolsCalled 调用toolCalls后再次调用AI时是否需要AI返回JSON格式响应
     * @param toolsCalled 是否已调用toolsCall
     * @param cb 回调函数，因为是流式输出，提供回调机制支持流式处理结果
     * */
    private void handleToolsCallStreamResponse(IterableStream<ChatCompletions> chatCompletionsStream,
                                               ChatCompletionsOptionsParam optionsParam,
                                               Boolean needStreamResponseWhenToolsCalled,
                                               Boolean needJsonFormatWhenToolsCalled,
                                               Boolean toolsCalled,
                                               Function<String, Object> cb) {
        List<FunctionCallParam> functionCallParamList = new ArrayList<>();
        FunctionCallParam callParam = new FunctionCallParam();
        boolean needToolsCall = false;
        StringBuilder responseContentSb = new StringBuilder();
        for (ChatCompletions chatCompletions : chatCompletionsStream) {
            List<ChatChoice> choices = chatCompletions.getChoices();
            if(CollectionUtils.isEmpty(choices)) {
                continue;
            }
            List<ChatCompletionsToolCall> toolCalls = choices.get(0).getDelta().getToolCalls();
            if(CollectionUtils.isNotEmpty(toolCalls)) {
                needToolsCall = true;
                break;
            }
        }
        for (ChatCompletions chatCompletions : chatCompletionsStream) {
            List<ChatChoice> choices = chatCompletions.getChoices();
            if(CollectionUtils.isEmpty(choices)) {
                continue;
            }
            // 指定了候选问题只有一个
            ChatChoice choice = choices.get(0);
            List<ChatCompletionsToolCall> toolCalls = choice.getDelta().getToolCalls();
            if(CollectionUtils.isEmpty(toolCalls)) {
                String content = choice.getDelta().getContent();
                if(content != null) {
                    // 未调用toolsCall或者调用了但指定需要流式输出的，流式输出结果
                    if(!toolsCalled || needStreamResponseWhenToolsCalled) {
                        // 虽然还未调toolsCall，但已经确定需要调用了，此时就不流式返回了
                        if(needToolsCall) {
                            continue;
                        }
                        cb.apply(content);
                    } else {
                        responseContentSb.append(content);
                    }
                }
                continue;
            }
            // 需要tools call，从流式响应里构造tools call参数
            for (ChatCompletionsToolCall toolCall : toolCalls) {
                FunctionCall functionCall = ((ChatCompletionsFunctionToolCall) toolCall).getFunction();
                String functionName = functionCall.getName();
                String toolCallId = toolCall.getId();
                String arguments = functionCall.getArguments();
                // 流式参数响应，toolCallId为空时表示延续上个函数的参数，不为空时表示开始一个新的回调
                if(StringUtils.isNotEmpty(toolCallId)) {
                    if(StringUtils.isNotEmpty(callParam.getToolsCallId())) {
                        functionCallParamList.add(callParam);
                    }
                    callParam = new FunctionCallParam();
                    callParam.setFunctionName(functionName);
                    callParam.setToolsCallId(toolCallId);
                    // tools call请求需要将toolCalls信息加入messages，需要在开始时加入，后面的流式结果里没有toolCallsId
                    ChatRequestAssistantMessage assistantMessage = new ChatRequestAssistantMessage("");
                    assistantMessage.setToolCalls(toolCalls);
                    callParam.setAssistantMessage(assistantMessage);
                }
                callParam.setFunctionArgumentSb(callParam.getFunctionArgumentSb().append(arguments));
            }
        }
        if(needToolsCall) {
            if(StringUtils.isNotEmpty(callParam.getToolsCallId())) {
                functionCallParamList.add(callParam);
            }
            Object instance = optionsParam.getInstance();
            Method[] methods = optionsParam.getMethods();
            List<ChatRequestMessage> messages = optionsParam.getMessages();
            ChatCompletionsOptions options = optionsParam.getOptions();

            List<ChatCompletionsToolCall> toolCalls = new ArrayList<>();
            List<ChatRequestToolMessage> toolMessages = new ArrayList<>();
            for (Method method : methods) {
                // 防止存在同名方法
                FunDesc funDesc = AnnotationUtils.findAnnotation(method, FunDesc.class);
                if(funDesc == null) {
                    continue;
                }
                for (FunctionCallParam functionCallParam : functionCallParamList) {
                    String functionName = functionCallParam.getFunctionName();
                    StringBuilder functionArgumentSb = functionCallParam.getFunctionArgumentSb();
                    String toolsCallId = functionCallParam.getToolsCallId();
                    if(method.getName().equals(functionName)) {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        Object functionArgumentsObj = com.alibaba.fastjson.JSONObject.parseObject(functionArgumentSb.toString(), parameterTypes[0]);
                        String result = method.invoke(instance, functionArgumentsObj).toString();
                        Assert.isTrue(StringUtils.isNotEmpty(result), "function call failed");
                        toolCalls.addAll(functionCallParam.getAssistantMessage().getToolCalls());
                        ChatRequestToolMessage toolMessage = new ChatRequestToolMessage(result, toolsCallId);
                        toolMessages.add(toolMessage);
                    }
                }
            }
            ChatRequestAssistantMessage assistantMessage = new ChatRequestAssistantMessage("");
            assistantMessage.setToolCalls(toolCalls);
            messages.add(assistantMessage);
            messages.addAll(toolMessages);
            // 带入函数调用结果再次调用ai
            if(Boolean.TRUE.equals(needJsonFormatWhenToolsCalled)) {
                options.setResponseFormat(new ChatCompletionsJsonResponseFormat());
            }
            chatCompletionsStream = textOpenAIClient.getChatCompletionsStream(aiChatModel, options);
            handleToolsCallStreamResponse(chatCompletionsStream, optionsParam,
                    needStreamResponseWhenToolsCalled,
                    needJsonFormatWhenToolsCalled,
                    true,
                    cb);
        } else {
            String finalResponse = responseContentSb.toString();
            if(StringUtils.isNotEmpty(finalResponse)) {
                cb.apply("{'finalResponse': '" + finalResponse + "'}");
            }
        }
    }

    private ChatCompletionsOptionsParam generateChatCompletionsWithFunctionCall(TextGenerateRequest request) {
        ChatOption chatOption = request.getChatOption();
        String callFunctionClass = chatOption.getCallFunctionClass();
        String callFunctionBeanName = chatOption.getCallFunctionBeanName();
        List<String> callFunctionMethodList = chatOption.getCallFunctionMethodList();
        String userMessage = request.getUserMessage();

        Object instance = null;
        boolean needToolsCall = true;
        if(StringUtils.isNotEmpty(callFunctionBeanName)) {
            log.info("callFunctionClass is bean");
            // 从spring容器中获取该bean
            instance = context.getBean(callFunctionBeanName);
        } else if(StringUtils.isNotEmpty(callFunctionClass)) {
            log.info("callFunctionClass is not bean");
            try {
                // 反射生成实例
                Class<?> clazz = Class.forName(callFunctionClass);
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            needToolsCall = false;
        }

        List<FunctionDefinition> definitions = new ArrayList<>();
        Method[] methods = new Method[0];
        if(needToolsCall) {
            definitions = getFunctionDefinitionList(instance, callFunctionMethodList);
            methods = instance.getClass().getDeclaredMethods();
        }

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
        messages.add(new ChatRequestUserMessage(userMessage));
        ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
        if(StringUtils.isNotEmpty(request.getChatOption().getUser())) {
            options.setUser(request.getChatOption().getUser());
        }
        List<ChatCompletionsToolDefinition> toolDefinitions = new ArrayList<>();
        for (FunctionDefinition definition : definitions) {
            ChatCompletionsFunctionToolDefinition toolDefinition = new ChatCompletionsFunctionToolDefinition(definition);
            toolDefinitions.add(toolDefinition);
        }
        if(CollectionUtils.isNotEmpty(toolDefinitions)) {
            options.setTools(toolDefinitions);
        }
        options.setMaxTokens(chatOption.getMaxTokens())
                .setN(1)
                .setTemperature(chatOption.getTemperature())
                .setStream(chatOption.getStream());
        ChatCompletionsOptionsParam optionsParam = new ChatCompletionsOptionsParam();
        optionsParam.setOptions(options);
        optionsParam.setInstance(instance);
        optionsParam.setMethods(methods);
        optionsParam.setMessages(messages);
        return optionsParam;
    }
    /**
     * 构造形如下面结构的tools_call数据：
     * "type": "function",
     *         "function": {
     *             "name": "get_delivery_date",
     *             "description": "Get the delivery date for a customer's order.
     *             Call this whenever you need to know the delivery date,
     *             for example when a customer asks 'Where is my package'",
     *             "parameters": {
     *                 "type": "object",
     *                 "properties": {
     *                     "order_id": {
     *                         "type": "string",
     *                         "description": "The customer's order ID.",
     *                     },
     *                 },
     *                 "required": ["order_id"],
     *                 "additionalProperties": False,
     *             },
     *         }
     * */
    private List<FunctionDefinition> getFunctionDefinitionList(Object callFunctionInstance,
                                                               List<String> methodNames) {
        List<FunctionDefinition> functions = new ArrayList<>();
        Method[] methods = callFunctionInstance.getClass().getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if(methodNames.contains(method.getName())) {
                FunctionDefinition definition = new FunctionDefinition(methodName);
                definition.setParameters(getFunctionSchema(method));
                FunDesc desc = AnnotationUtils.findAnnotation(method, FunDesc.class);
                Assert.isTrue(desc != null, "方法" + methodName + "未标注@FunDesc注解");
                definition.setDescription(desc.value());
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
        List<String> requiredFields = new ArrayList<>();
        for (Parameter parameter : parameters) {
            // parameter是对象，反射获取该对象的字段
            Field[] fields = parameter.getType().getDeclaredFields();
            for (Field field : fields) {
                FieldDesc fieldDesc = AnnotationUtils.findAnnotation(field, FieldDesc.class);
                Assert.isTrue(fieldDesc != null, "参数类" + parameter + "未标注@FieldDesc注解");
                String fieldDescValue = "";
                if("true".equals(fieldDesc.required())) {
                    requiredFields.add(field.getName());
                }
                fieldDescValue = fieldDesc.value();

                if(isEnum(field)) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "string");
                    map.put("description", fieldDescValue);
                    map.put("enum", Arrays.stream(field.getType().getEnumConstants())
                            .map(Object::toString).collect(Collectors.toList()));
                    parameterMap.put(field.getName(), map);
                } else if (List.class.isAssignableFrom(field.getType())){
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "array");
                    map.put("description", fieldDescValue);
                    map.put("items", new HashMap<String, Object>() {{
                        put("type", "string");
                    }});
                    parameterMap.put(field.getName(), map);
                } else {
                    String finalFieldDescValue = fieldDescValue;
                    parameterMap.put(field.getName(), new HashMap<String, Object>() {{
                        put("type", getTypeMapping(field));
                        put("description", finalFieldDescValue);
                    }});
                }
            }
        }
        schema.put("properties", parameterMap);
        schema.put("required", requiredFields);
        return BinaryData.fromObject(schema);
    }

    private boolean isEnum(Field field) {
        return field.getType().isEnum();
    }

    private String getTypeMapping(Field field) {
        Class<?> fieldType = field.getType();
        if(String.class.isAssignableFrom(fieldType)) {
            return "string";
        }
        if(Number.class.isAssignableFrom(fieldType)) {
            return "number";
        }
        if(Boolean.class.isAssignableFrom(fieldType)) {
            return "boolean";
        }
        return "object";
    }
}
