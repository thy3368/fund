package com.tanggo.fund.cashflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 全球资金流动监控系统主应用
 */
@SpringBootApplication
@EnableScheduling
@Slf4j
public class CashFlowApplication {
    
    public static void main(String[] args) {
        log.info("启动全球资金流动监控系统...");
        SpringApplication.run(CashFlowApplication.class, args);
        log.info("全球资金流动监控系统启动完成");
    }
}
