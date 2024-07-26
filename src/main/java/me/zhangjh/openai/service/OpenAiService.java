package me.zhangjh.openai.service;

import com.azure.ai.openai.models.ChatCompletions;
import me.zhangjh.openai.request.ImageGenerateRequest;
import me.zhangjh.openai.request.TextGenerateRequest;
import me.zhangjh.openai.dto.ImageGenerateDTO;

import javax.servlet.http.HttpServletResponse;
import java.util.function.Function;

/**
 * @author zhangjh
 */
public interface OpenAiService {

    ImageGenerateDTO generateImage(ImageGenerateRequest request);

    /**
     * 流式接口，回调函数方式
     * */
    void generateTextWithCb(TextGenerateRequest request, Function<String, Object> cb);

    /**
     * 支持function call的接口，不支持流式
     * */
    ChatCompletions generateTextWithFunctionCall(TextGenerateRequest request) throws Exception;

    /**
     * 供上层controller调用，流式返回无需自行再封装
     * 能力上跟同名的有返回值的接口相同，仅返回结果通过response流式返回
     * */
    void generateTextStream(TextGenerateRequest request, HttpServletResponse response);

}
