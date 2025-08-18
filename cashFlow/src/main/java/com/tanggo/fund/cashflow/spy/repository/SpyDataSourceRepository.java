package com.tanggo.fund.cashflow.spy.repository;

import com.tanggo.fund.cashflow.spy.dto.SpyFlowData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * SPY数据源服务
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class SpyDataSourceRepository {

    private final WebClient webClient;

    @Value("${app.data-sources.etf-com.api-key:}")
    private String etfComApiKey;

    @Value("${app.data-sources.alpha-vantage.api-key:}")
    private String alphaVantageApiKey;

    /**
     * 从主数据源获取SPY数据
     */
    public SpyFlowData fetchFromPrimarySource() {
        try {
            return fetchFromYahooFinance(); // 使用免费可靠的Yahoo Finance作为主数据源
        } catch (Exception e) {
            log.error("Yahoo Finance数据获取失败", e);
            throw new RuntimeException("主数据源不可用", e);
        }
    }

    /**
     * 从备用数据源获取SPY数据
     */
    public SpyFlowData fetchFromBackupSource() {
        try {
            return fetchFromAlphaVantage(); // Alpha Vantage作为备用
        } catch (Exception e) {
            log.error("Alpha Vantage数据获取失败", e);
            throw new RuntimeException("备用数据源不可用", e);
        }
    }

    /**
     * 从Yahoo Finance获取SPY数据
     */
    private SpyFlowData fetchFromYahooFinance() {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/SPY";

        try {
            Map<String, Object> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .block();

            return parseYahooResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Yahoo Finance API调用失败: {}", e.getMessage());
            throw new RuntimeException("Yahoo Finance API错误", e);
        }
    }

    /**
     * 从Alpha Vantage获取SPY数据
     */
    private SpyFlowData fetchFromAlphaVantage() {
        if (alphaVantageApiKey == null || alphaVantageApiKey.isEmpty()) {
            throw new RuntimeException("Alpha Vantage API密钥未配置");
        }

        String url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=SPY&apikey=" + alphaVantageApiKey;

        try {
            Map<String, Object> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            return parseAlphaVantageResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Alpha Vantage API调用失败: {}", e.getMessage());
            throw new RuntimeException("Alpha Vantage API错误", e);
        }
    }

    /**
     * 解析Yahoo Finance响应
     */
    @SuppressWarnings("unchecked")
    private SpyFlowData parseYahooResponse(Map<String, Object> response) {
        try {
            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            Map<String, Object>[] results = (Map<String, Object>[]) chart.get("result");

            if (results == null || results.length == 0) {
                throw new RuntimeException("Yahoo Finance响应数据为空");
            }

            Map<String, Object> result = results[0];
            Map<String, Object> meta = (Map<String, Object>) result.get("meta");

            // 获取基本信息
            BigDecimal currentPrice = new BigDecimal(meta.get("regularMarketPrice").toString());
            BigDecimal previousClose = new BigDecimal(meta.get("previousClose").toString());

            // 计算净流入 (这里使用模拟数据，实际应该从专门的ETF流向API获取)
            BigDecimal mockNetInflow = calculateMockNetInflow(currentPrice, previousClose);

            return SpyFlowData.builder()
                .ticker("SPY")
                .dataDate(LocalDate.now())
                .marketPrice(currentPrice)
                .nav(currentPrice) // NAV通常接近市场价格
                .aum(new BigDecimal("450000000000")) // SPY约4500亿美元AUM
                .sharesOutstanding(935000000L) // SPY约9.35亿份额
                .dailyNetInflow(mockNetInflow)
                .totalInflow(mockNetInflow.max(BigDecimal.ZERO))
                .totalOutflow(mockNetInflow.min(BigDecimal.ZERO).abs())
                .creationUnits(calculateMockCreationUnits(mockNetInflow))
                .redemptionUnits(calculateMockRedemptionUnits(mockNetInflow))
                .sharesChange(calculateMockSharesChange(mockNetInflow, currentPrice))
                .dataSource("YAHOO_FINANCE")
                .confidenceScore(85) // Yahoo Finance基础数据置信度
                .build();

        } catch (Exception e) {
            log.error("解析Yahoo Finance响应失败", e);
            throw new RuntimeException("Yahoo Finance数据解析错误", e);
        }
    }

    /**
     * 解析Alpha Vantage响应
     */
    @SuppressWarnings("unchecked")
    private SpyFlowData parseAlphaVantageResponse(Map<String, Object> response) {
        try {
            Map<String, Object> quote = (Map<String, Object>) response.get("Global Quote");

            if (quote == null) {
                throw new RuntimeException("Alpha Vantage响应数据为空");
            }

            BigDecimal currentPrice = new BigDecimal(quote.get("05. price").toString());
            BigDecimal previousClose = new BigDecimal(quote.get("08. previous close").toString());
            BigDecimal volume = new BigDecimal(quote.get("06. volume").toString());

            // 计算净流入 (模拟数据)
            BigDecimal mockNetInflow = calculateMockNetInflow(currentPrice, previousClose);

            return SpyFlowData.builder()
                .ticker("SPY")
                .dataDate(LocalDate.now())
                .marketPrice(currentPrice)
                .nav(currentPrice)
                .aum(new BigDecimal("450000000000"))
                .sharesOutstanding(935000000L)
                .dailyNetInflow(mockNetInflow)
                .totalInflow(mockNetInflow.max(BigDecimal.ZERO))
                .totalOutflow(mockNetInflow.min(BigDecimal.ZERO).abs())
                .creationUnits(calculateMockCreationUnits(mockNetInflow))
                .redemptionUnits(calculateMockRedemptionUnits(mockNetInflow))
                .sharesChange(calculateMockSharesChange(mockNetInflow, currentPrice))
                .dataSource("ALPHA_VANTAGE")
                .confidenceScore(80) // Alpha Vantage基础数据置信度
                .build();

        } catch (Exception e) {
            log.error("解析Alpha Vantage响应失败", e);
            throw new RuntimeException("Alpha Vantage数据解析错误", e);
        }
    }

    /**
     * 计算模拟净流入数据
     * 注意：这是演示用的模拟计算，实际生产应使用真实的ETF流向数据
     */
    private BigDecimal calculateMockNetInflow(BigDecimal currentPrice, BigDecimal previousClose) {
        BigDecimal priceChange = currentPrice.subtract(previousClose);
        BigDecimal changePercent = priceChange.divide(previousClose, 6, BigDecimal.ROUND_HALF_UP);

        // 简单模拟：价格上涨通常伴随资金流入，下跌伴随流出
        // 实际情况要复杂得多，需要使用专业的ETF流向数据
        BigDecimal baseMagnitude = new BigDecimal("500000000"); // 5亿美元基准
        BigDecimal mockNetInflow = changePercent.multiply(baseMagnitude).multiply(new BigDecimal("10"));

        // 添加随机因素
        double randomFactor = 0.8 + (Math.random() * 0.4); // 0.8-1.2的随机因子
        mockNetInflow = mockNetInflow.multiply(BigDecimal.valueOf(randomFactor));

        return mockNetInflow;
    }

    private Integer calculateMockCreationUnits(BigDecimal netInflow) {
        if (netInflow.compareTo(BigDecimal.ZERO) > 0) {
            return netInflow.divide(new BigDecimal("24000000"), 0, BigDecimal.ROUND_HALF_UP).intValue();
        }
        return 0;
    }

    private Integer calculateMockRedemptionUnits(BigDecimal netInflow) {
        if (netInflow.compareTo(BigDecimal.ZERO) < 0) {
            return netInflow.abs().divide(new BigDecimal("24000000"), 0, BigDecimal.ROUND_HALF_UP).intValue();
        }
        return 0;
    }

    private Long calculateMockSharesChange(BigDecimal netInflow, BigDecimal price) {
        return netInflow.divide(price, 0, BigDecimal.ROUND_HALF_UP).longValue();
    }
}
