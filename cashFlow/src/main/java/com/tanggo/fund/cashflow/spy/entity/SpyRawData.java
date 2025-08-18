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
 * SPY原始数据实体
 */
@Entity
@Table(name = "spy_raw_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpyRawData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "data_date", nullable = false)
    private LocalDate dataDate;
    
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    // ETF基本信息
    @Column(name = "ticker", length = 10)
    @Builder.Default
    private String ticker = "SPY";
    
    @Column(name = "aum", precision = 15, scale = 2)
    private BigDecimal aum; // 资产管理规模
    
    @Column(name = "shares_outstanding")
    private Long sharesOutstanding; // 流通份额
    
    @Column(name = "nav", precision = 10, scale = 4)
    private BigDecimal nav; // 净值
    
    @Column(name = "market_price", precision = 10, scale = 4)
    private BigDecimal marketPrice; // 市场价格
    
    // 资金流向数据
    @Column(name = "daily_net_inflow", precision = 15, scale = 2)
    private BigDecimal dailyNetInflow; // 日净流入(美元)
    
    @Column(name = "total_inflow", precision = 15, scale = 2)
    private BigDecimal totalInflow; // 总流入
    
    @Column(name = "total_outflow", precision = 15, scale = 2)
    private BigDecimal totalOutflow; // 总流出
    
    @Column(name = "creation_units")
    private Integer creationUnits; // 申购单位数
    
    @Column(name = "redemption_units")
    private Integer redemptionUnits; // 赎回单位数
    
    @Column(name = "shares_change")
    private Long sharesChange; // 份额变化
    
    // 验证数据
    @Column(name = "calculated_inflow", precision = 15, scale = 2)
    private BigDecimal calculatedInflow; // 基于份额计算的流入
    
    @Column(name = "flow_intensity", precision = 8, scale = 4)
    private BigDecimal flowIntensity; // 流入强度(净流入/AUM)
    
    // 元数据
    @Column(name = "data_source", length = 50)
    private String dataSource; // 数据源
    
    @Column(name = "confidence_score")
    private Integer confidenceScore; // 置信度(0-100)
    
    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    // 唯一约束
    @Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"data_date", "data_source"})
    })
    public static class UniqueConstraints {}
}