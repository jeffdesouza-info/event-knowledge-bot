package br.com.jeffdesouza.eventknowledge.configuration;

import br.com.jeffdesouza.eventknowledge.assistant.infrastructure.openai.OpenAiResponsesAnswerGenerator;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OpenAiConfiguration {

    @Bean
    RestClient openAiRestClient(@Value("${openai.timeout:10s}") Duration timeout) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    OpenAiResponsesAnswerGenerator openAiResponsesAnswerGenerator(
            RestClient openAiRestClient, ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-5.6-luna}") String model) {
        return new OpenAiResponsesAnswerGenerator(openAiRestClient, objectMapper, apiKey, model);
    }
}
