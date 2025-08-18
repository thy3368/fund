package com.tanggo.fund.cashflow.spy.service;

import com.tanggo.fund.cashflow.spy.dto.SpyFlowData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SPY数据验证服务测试
 */
class SpyDataValidationServiceTest {
    
    private SpyDataValidationService validationService;
    
    @BeforeEach
    void setUp() {
        validationService = new SpyDataValidationService();
    }
    
    @Test
    void testValidSpyData() {
        SpyFlowData data = createValidSpyData();
        
        ValidationResult result = validationService.validateSpyData(data);
        
        // Debug: print warnings if any
        if (result.hasWarnings()) {
            System.out.println("Warnings found: " + result.getWarnings());
        }
        
        assertTrue(result.isValid(), "有效数据应该通过验证");
        assertFalse(result.hasWarnings(), "有效数据不应该有警告");
    }
    
    @Test
    void testMissingRequiredFields() {
        SpyFlowData data = SpyFlowData.builder()
            .ticker("SPY")
            .dataDate(LocalDate.now())
            .dataSource("TEST")
            // 缺少必填字段
            .build();
        
        ValidationResult result = validationService.validateSpyData(data);
        
        assertFalse(result.isValid(), "缺少必填字段应该验证失败");
        assertTrue(result.getErrors().size() > 0, "应该有错误信息");
    }
    
    @Test
    void testExcessiveInflowRatio() {
        SpyFlowData data = createValidSpyData();
        // 设置过高的日流入比例（超过AUM的10%）
        data.setDailyNetInflow(new BigDecimal("50000000000")); // 500亿，超过450亿AUM的10%
        
        ValidationResult result = validationService.validateSpyData(data);
        
        assertFalse(result.isValid(), "过高的流入比例应该验证失败");
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.contains("日流入超过AUM的10%")));
    }
    
    @Test
    void testPriceNavDifference() {
        SpyFlowData data = createValidSpyData();
        // 设置NAV和市场价格差异过大
        data.setNav(new BigDecimal("400"));
        data.setMarketPrice(new BigDecimal("450")); // 超过2%阈值
        
        ValidationResult result = validationService.validateSpyData(data);
        
        assertTrue(result.hasWarnings(), "价格差异过大应该有警告");
        assertTrue(result.getWarnings().stream()
            .anyMatch(warning -> warning.contains("市场价格与NAV差异过大")));
    }
    
    @Test
    void testUnreasonablePrice() {
        SpyFlowData data = createValidSpyData();
        data.setMarketPrice(new BigDecimal("50")); // 异常低价
        
        ValidationResult result = validationService.validateSpyData(data);
        
        assertTrue(result.hasWarnings(), "异常价格应该有警告");
    }
    
    private SpyFlowData createValidSpyData() {
        // 计算一致的数据：50个申购单位 - 26个赎回单位 = 24个净申购单位
        // 24 * 50,000份额/单位 * $421/份额 = $505,200,000净流入
        BigDecimal netInflow = new BigDecimal("505200000");
        Long sharesChange = 24L * 50000L; // 1,200,000份额
        
        return SpyFlowData.builder()
            .ticker("SPY")
            .dataDate(LocalDate.now())
            .aum(new BigDecimal("450000000000")) // 4500亿
            .sharesOutstanding(935000000L) // 9.35亿份额
            .nav(new BigDecimal("420.50"))
            .marketPrice(new BigDecimal("421.00"))
            .dailyNetInflow(netInflow) // 5.052亿流入
            .totalInflow(netInflow.add(new BigDecimal("100000000"))) // 总流入稍大
            .totalOutflow(new BigDecimal("100000000")) // 总流出
            .creationUnits(50)
            .redemptionUnits(26)
            .sharesChange(sharesChange) // 1,200,000份额变化
            .dataSource("YAHOO_FINANCE")
            .confidenceScore(85)
            .build();
    }
}