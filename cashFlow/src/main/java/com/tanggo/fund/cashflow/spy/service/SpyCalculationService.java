package com.tanggo.fund.cashflow.spy.service;

import com.tanggo.fund.cashflow.spy.entity.SpyFlowResult;
import com.tanggo.fund.cashflow.spy.entity.SpyRawData;
import com.tanggo.fund.cashflow.spy.repository.SpyFlowResultRepository;
import com.tanggo.fund.cashflow.spy.websocket.SpyWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SPY计算服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SpyCalculationService {
    
    private final SpyFlowResultRepository flowResultRepository;
    private final SpyWebSocketHandler webSocketHandler;
    
    /**
     * 异步计算SPY资金流向
     */
    @Async
    public void calculateSpyFlowAsync(SpyRawData rawData) {
        log.info("开始异步计算SPY流向数据: dataDate={}", rawData.getDataDate());
        
        try {
            SpyFlowResult result = calculateSpyFlow(rawData);
            flowResultRepository.save(result);
            
            // 广播实时更新
            webSocketHandler.broadcastSpyUpdate(result);
            
            log.info("SPY流向计算完成: 净流入=${}, 置信度={}", 
                result.getFinalNetInflow(), result.getOverallConfidence());
        } catch (Exception e) {
            log.error("SPY流向计算失败", e);
        }
    }
    
    /**
     * 计算SPY资金流向
     */
    public SpyFlowResult calculateSpyFlow(SpyRawData rawData) {
        // 1. 数据质量评估
        Integer dataQualityScore = calculateDataQualityScore(rawData);
        
        // 2. 流向强度计算
        BigDecimal flowIntensity = calculateFlowIntensity(rawData);
        
        // 3. 置信度计算
        BigDecimal overallConfidence = calculateOverallConfidence(rawData, dataQualityScore);
        
        // 4. 13维度分类
        return SpyFlowResult.builder()
            .dataDate(rawData.getDataDate())
            .timestamp(Instant.now())
            .finalNetInflow(rawData.getDailyNetInflow())
            .flowIntensity(flowIntensity)
            .dataQualityScore(new BigDecimal(dataQualityScore))
            .overallConfidence(overallConfidence)
            .validationPassed(dataQualityScore >= 70 && overallConfidence.compareTo(new BigDecimal("60")) >= 0)
            .primarySource(rawData.getDataSource())
            .yahooContribution(rawData.getDailyNetInflow()) // 目前主要来源是Yahoo
            .build();
    }
    
    /**
     * 计算数据质量评分
     */
    private Integer calculateDataQualityScore(SpyRawData rawData) {
        int score = 100;
        
        // 扣分项：缺失关键数据
        if (rawData.getDailyNetInflow() == null) score -= 30;
        if (rawData.getAum() == null) score -= 20;
        if (rawData.getMarketPrice() == null) score -= 15;
        if (rawData.getSharesOutstanding() == null) score -= 10;
        
        // 扣分项：数据异常
        if (rawData.getConfidenceScore() != null && rawData.getConfidenceScore() < 70) {
            score -= (80 - rawData.getConfidenceScore()) / 2;
        }
        
        // 扣分项：计算流入与报告流入差异大
        if (rawData.getCalculatedInflow() != null && rawData.getDailyNetInflow() != null) {
            BigDecimal diff = rawData.getCalculatedInflow().subtract(rawData.getDailyNetInflow()).abs();
            if (rawData.getDailyNetInflow().abs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diffPercent = diff.divide(rawData.getDailyNetInflow().abs(), 4, BigDecimal.ROUND_HALF_UP);
                if (diffPercent.compareTo(new BigDecimal("0.20")) > 0) { // 超过20%差异
                    score -= 15;
                }
            }
        }
        
        // 奖励项：数据源可靠
        if ("YAHOO_FINANCE".equals(rawData.getDataSource())) {
            score += 5;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * 计算流向强度
     */
    private BigDecimal calculateFlowIntensity(SpyRawData rawData) {
        if (rawData.getFlowIntensity() != null) {
            return rawData.getFlowIntensity();
        }
        
        if (rawData.getDailyNetInflow() != null && rawData.getAum() != null && 
            rawData.getAum().compareTo(BigDecimal.ZERO) > 0) {
            return rawData.getDailyNetInflow().abs().divide(rawData.getAum(), 6, BigDecimal.ROUND_HALF_UP);
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * 计算总体置信度
     */
    private BigDecimal calculateOverallConfidence(SpyRawData rawData, Integer dataQualityScore) {
        int confidence = 50; // 基础置信度
        
        // 加分项：数据质量高
        confidence += dataQualityScore / 5; // 数据质量每5分转化为1分置信度
        
        // 加分项：数据源置信度
        if (rawData.getConfidenceScore() != null) {
            confidence += rawData.getConfidenceScore() / 4;
        }
        
        // 加分项：数据完整性
        int completeness = 0;
        if (rawData.getDailyNetInflow() != null) completeness += 25;
        if (rawData.getAum() != null) completeness += 20;
        if (rawData.getMarketPrice() != null) completeness += 15;
        if (rawData.getSharesOutstanding() != null) completeness += 10;
        if (rawData.getCreationUnits() != null) completeness += 10;
        if (rawData.getRedemptionUnits() != null) completeness += 10;
        if (rawData.getNav() != null) completeness += 10;
        
        confidence += completeness / 10;
        
        // 减分项：数据异常
        if (rawData.getFlowIntensity() != null && 
            rawData.getFlowIntensity().compareTo(new BigDecimal("0.05")) > 0) {
            confidence -= 10; // 流入强度过高
        }
        
        // 减分项：价格异常
        if (rawData.getMarketPrice() != null && 
            (rawData.getMarketPrice().compareTo(new BigDecimal("100")) < 0 || 
             rawData.getMarketPrice().compareTo(new BigDecimal("800")) > 0)) {
            confidence -= 15;
        }
        
        return new BigDecimal(Math.max(0, Math.min(100, confidence)));
    }
}