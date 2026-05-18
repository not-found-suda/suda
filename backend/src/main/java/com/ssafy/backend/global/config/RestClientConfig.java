package com.ssafy.backend.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ExternalApiProperties.class)
public class RestClientConfig {

  @Bean
  public RestClient restClient(ExternalApiProperties externalApiProperties) {
    Duration connectTimeout = externalApiProperties.timeout().connect();
    Duration readTimeout = externalApiProperties.timeout().read();

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);

    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
