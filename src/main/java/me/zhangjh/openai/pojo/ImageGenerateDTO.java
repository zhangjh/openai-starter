package me.zhangjh.openai.pojo;

import lombok.Data;

import java.util.List;

/**
 * @author zhangjh
 */
@Data
public class ImageGenerateDTO {

    private String userId;

    private String prompt;

    private List<String> imgs;
}
