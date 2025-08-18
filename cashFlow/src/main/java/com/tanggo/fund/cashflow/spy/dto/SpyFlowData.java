package com.tanggo.fund.cashflow.spy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SPY流向数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpyFlowData {
    
    private String ticker;
    private LocalDate dataDate;
    
    // ETF基本信息
    private BigDecimal aum; // 资产管理规模
    private Long sharesOutstanding; // 流通份额
    private BigDecimal nav; // 净值
    private BigDecimal marketPrice; // 市场价格
    
    // 资金流向数据
    private BigDecimal dailyNetInflow; // 日净流入
    private BigDecimal totalInflow; // 总流入
    private BigDecimal totalOutflow; // 总流出
    private Integer creationUnits; // 申购单位数
    private Integer redemptionUnits; // 赎回单位数
    private Long sharesChange; // 份额变化
    
    // 元数据
    private String dataSource;
    private Integer confidenceScore;
}