package dev.infrai.creator;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "infrai")
public class ScreeningConfig {
    private String baseUrl;
    private String apiKey;
    private int maxRetries;

    @Bean OpenAIClient moderationClient() {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build();
    }

    @Bean HttpClient imageHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
