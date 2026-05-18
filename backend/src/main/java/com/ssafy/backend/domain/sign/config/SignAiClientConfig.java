package com.ssafy.backend.domain.sign.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SignAiClientConfig {

  @Bean
  @Qualifier("signAiRestClient")
  public RestClient signAiRestClient(SignAiProperties properties) {
    Duration connectTimeout = properties.timeout().connect();
    Duration readTimeout = properties.timeout().read();

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);

    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
