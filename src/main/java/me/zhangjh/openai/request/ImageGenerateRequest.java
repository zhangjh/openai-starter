package me.zhangjh.openai.request;

import lombok.Data;

import java.util.List;

/**
 * @author zhangjh
 */
@Data
public class ImageGenerateRequest {

    // Azure openAI not support image generate image
    private List<String> sampleImages;

    private String prompt;

    private String userId;

}
