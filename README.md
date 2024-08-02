# OpenAI Spring-Boot Starter

## 简介
[chatgpt-starter项目](https://github.com/zhangjh/chatgpt-starter/blob/master/Readme.md)的升级版本，支持azure部署的OpenAI service。
同时将Function call能力封装进了sdk。

## 如何使用
1. 工程中添加依赖
   ```xml
     <dependency>
         <groupId>me.zhangjh</groupId>
         <artifactId>openai-starter</artifactId>
         <version>${最新版本}</version>
     </dependency>
   ```
2. 修改配置文件application.properties
   ```azure
    ai.chat.model=xxx    //gpt-4o
    ai.image.model=xxx   //dall-e-3

    chat.api.key=xxx
    chat.endpoint=xxx

    img.api.key=xxx
    img.endpoint=xxx
   ```
3. 暴露的接口方法
   ```java
   // me.zhangjh.openai.service.OpenAiService
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
   ```
4. 使用方式可以参考单元测试
    ```java
   // me.zhangjh.openai.AppTests 
   ```
5. 如果需要使用Function Call能力，需要将供Function call调用的函数添加上@Desc注解，该注解主要用来告知AI方法的作用描述