package com.tanggo.fund.cashflow.spy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * SPY资金流向计算结果实体
 */
@Entity
@Table(name = "spy_flow_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpyFlowResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "data_date", nullable = false)
    private LocalDate dataDate;
    
    @Column(name = "timestamp", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant timestamp;
    
    // 计算结果
    @Column(name = "final_net_inflow", precision = 15, scale = 2)
    private BigDecimal finalNetInflow; // 最终净流入(美元)
    
    @Column(name = "flow_intensity", precision = 8, scale = 4)
    private BigDecimal flowIntensity; // 流入强度
    
    @Column(name = "volume_weighted_price", precision = 10, scale = 4)
    private BigDecimal volumeWeightedPrice; // 成交量加权价格
    
    // 数据源贡献
    @Column(name = "etf_com_contribution", precision = 15, scale = 2)
    private BigDecimal etfComContribution; // ETF.com数据贡献
    
    @Column(name = "yahoo_contribution", precision = 15, scale = 2)
    private BigDecimal yahooContribution; // Yahoo Finance贡献
    
    @Column(name = "primary_source", length = 50)
    private String primarySource; // 主要数据源
    
    // 质量指标
    @Column(name = "overall_confidence", precision = 5, scale = 2)
    private BigDecimal overallConfidence; // 整体置信度
    
    @Column(name = "data_quality_score", precision = 5, scale = 2)
    private BigDecimal dataQualityScore; // 数据质量评分
    
    @Column(name = "validation_passed")
    private Boolean validationPassed; // 验证是否通过
    
    // 13维度分类
    @Column(name = "geographic_dimension", length = 50)
    @Builder.Default
    private String geographicDimension = "North America";
    
    @Column(name = "currency_dimension", length = 10)
    @Builder.Default
    private String currencyDimension = "USD";
    
    @Column(name = "market_cap_dimension", length = 20)
    @Builder.Default
    private String marketCapDimension = "Large Cap";
    
    @Column(name = "sector_dimension", length = 50)
    @Builder.Default
    private String sectorDimension = "Broad Market";
    
    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    @Builder.Default
    private Instant createdAt = Instant.now();
}