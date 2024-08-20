package me.zhangjh.openai.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import me.zhangjh.openai.impl.OpenAiServiceImpl;
import me.zhangjh.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhangjh
 */
@Configuration
public class OpenAiConfig {

    @Value("${chat.api.key}")
    private String chatApiKey;

    @Value("${chat.endpoint}")
    private String chatEndpoint;

    @Value("${img.api.key}")
    private String imgApiKey;

    @Value("${img.endpoint}")
    private String imgEndpoint;

    @Bean
    @ConditionalOnMissingBean
    public OpenAiService openAiService() {
        return new OpenAiServiceImpl();
    }

    @Bean
    @ConditionalOnProperty(prefix = "chat", name = "api.enabled", havingValue = "true")
    public OpenAIClient textOpenAIClient() {
        return new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(chatApiKey))
                .endpoint(chatEndpoint)
                .buildClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "img", name = "api.enabled", havingValue = "true")
    public OpenAIClient imgOpenAIClient() {
        return new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(imgApiKey))
                .endpoint(imgEndpoint)
                .buildClient();
    }
}
