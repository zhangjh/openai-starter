package me.zhangjh.openai.service;

import me.zhangjh.openai.request.ImageGenerateRequest;
import me.zhangjh.openai.request.TextGenerateRequest;
import me.zhangjh.openai.pojo.ImageGenerateDTO;

import java.util.function.Function;

/**
 * @author zhangjh
 */
public interface OpenAiService {

    ImageGenerateDTO generateImage(ImageGenerateRequest request);

    /**
     * 支持流式的tools call调用
     * 通过回调函数流式返回结果
     * */
    void generateTextWithToolsCallStream(TextGenerateRequest request, Function<String, Object> cb);
}
