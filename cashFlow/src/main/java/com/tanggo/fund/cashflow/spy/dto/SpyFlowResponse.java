package com.tanggo.fund.cashflow.spy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * SPY流向API响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpyFlowResponse {
    
    private Boolean success;
    private SpyData data;
    private String error;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpyData {
        private String symbol;
        private String name;
        private String timeRange;
        private SpyFlowSummary summary;
        private List<SpyTimeSeriesPoint> timeSeries;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpyFlowSummary {
        private BigDecimal totalNetInflow;
        private BigDecimal flowIntensity;
        private Instant lastUpdated;
        private Integer confidence;
        private String primarySource;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpyTimeSeriesPoint {
        private Instant timestamp;
        private BigDecimal netInflow;
        private BigDecimal confidence;
        private BigDecimal flowIntensity;
    }
}