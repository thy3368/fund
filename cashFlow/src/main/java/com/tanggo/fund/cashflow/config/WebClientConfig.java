package com.tanggo.fund.cashflow.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient配置
 */
@Configuration
@Slf4j
public class WebClientConfig {
    
    /**
     * 配置WebClient用于数据源API调用
     */
    @Bean
    public WebClient webClient() {
        // 配置HTTP客户端
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 连接超时10秒
            .responseTimeout(Duration.ofSeconds(30)) // 响应超时30秒
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
        
        // 配置交换策略 - 增加内存缓冲区大小
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024); // 1MB
            })
            .build();
        
        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .defaultHeader("User-Agent", "CashFlow-Monitor/1.0")
            .defaultHeader("Accept", "application/json")
            .build();
        
        log.info("WebClient配置完成: 连接超时=10s, 响应超时=30s, 内存缓冲=1MB");
        
        return client;
    }
}