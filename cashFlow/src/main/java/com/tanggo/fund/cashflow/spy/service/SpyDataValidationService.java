package com.tanggo.fund.cashflow.spy.service;

import com.tanggo.fund.cashflow.spy.dto.SpyFlowData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * SPY数据验证服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SpyDataValidationService {
    
    /**
     * 验证SPY数据
     */
    public ValidationResult validateSpyData(SpyFlowData data) {
        ValidationResult result = new ValidationResult();
        
        // 1. 必填字段检查
        validateRequiredFields(data, result);
        
        // 2. 数据逻辑性检查
        validateDataLogic(data, result);
        
        // 3. 规模合理性检查
        validateScaleReasonableness(data, result);
        
        // 4. 数据一致性检查
        validateDataConsistency(data, result);
        
        return result;
    }
    
    /**
     * 验证必填字段
     */
    private void validateRequiredFields(SpyFlowData data, ValidationResult result) {
        if (data.getDailyNetInflow() == null) {
            result.addError("SPY净流入数据缺失");
        }
        
        if (data.getMarketPrice() == null || data.getMarketPrice().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("SPY市场价格数据无效");
        }
        
        if (data.getAum() == null || data.getAum().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("SPY资产规模数据无效");
        }
        
        if (data.getSharesOutstanding() == null || data.getSharesOutstanding() <= 0) {
            result.addError("SPY流通份额数据无效");
        }
        
        if (data.getDataSource() == null || data.getDataSource().isEmpty()) {
            result.addError("数据源信息缺失");
        }
    }
    
    /**
     * 验证数据逻辑性
     */
    private void validateDataLogic(SpyFlowData data, ValidationResult result) {
        // 检查净流入是否等于总流入减去总流出
        if (data.getTotalInflow() != null && data.getTotalOutflow() != null && data.getDailyNetInflow() != null) {
            BigDecimal calculated = data.getTotalInflow().subtract(data.getTotalOutflow());
            BigDecimal reported = data.getDailyNetInflow();
            BigDecimal diff = calculated.subtract(reported).abs();
            
            if (reported.abs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal threshold = reported.abs().multiply(new BigDecimal("0.05")); // 5%阈值
                if (diff.compareTo(threshold) > 0) {
                    result.addWarning("SPY净流入计算不一致，差异: " + diff);
                }
            }
        }
        
        // 检查NAV和市场价格的合理性
        if (data.getNav() != null && data.getMarketPrice() != null) {
            BigDecimal priceDiff = data.getMarketPrice().subtract(data.getNav()).abs();
            BigDecimal threshold = data.getNav().multiply(new BigDecimal("0.02")); // 2%阈值
            
            if (priceDiff.compareTo(threshold) > 0) {
                result.addWarning("SPY市场价格与NAV差异过大: " + priceDiff);
            }
        }
    }
    
    /**
     * 验证规模合理性
     */
    private void validateScaleReasonableness(SpyFlowData data, ValidationResult result) {
        if (data.getAum() != null && data.getDailyNetInflow() != null) {
            // 日流入不应超过AUM的10%
            BigDecimal maxInflow = data.getAum().multiply(new BigDecimal("0.10"));
            if (data.getDailyNetInflow().abs().compareTo(maxInflow) > 0) {
                result.addError("SPY日流入超过AUM的10%，疑似异常数据: " + 
                    data.getDailyNetInflow().abs() + " vs " + maxInflow);
            }
            
            // 流入强度检查
            BigDecimal flowIntensity = data.getDailyNetInflow().abs().divide(data.getAum(), 6, BigDecimal.ROUND_HALF_UP);
            if (flowIntensity.compareTo(new BigDecimal("0.05")) > 0) { // 5%
                result.addWarning("SPY流入强度较高: " + flowIntensity.multiply(BigDecimal.valueOf(100)) + "%");
            }
        }
        
        // 市场价格合理性检查 (SPY价格通常在200-600美元之间)
        if (data.getMarketPrice() != null) {
            if (data.getMarketPrice().compareTo(new BigDecimal("100")) < 0 || 
                data.getMarketPrice().compareTo(new BigDecimal("800")) > 0) {
                result.addWarning("SPY价格超出正常范围: " + data.getMarketPrice());
            }
        }
    }
    
    /**
     * 验证数据一致性
     */
    private void validateDataConsistency(SpyFlowData data, ValidationResult result) {
        // 检查申购赎回单位与净流入的一致性
        if (data.getCreationUnits() != null && data.getRedemptionUnits() != null && 
            data.getDailyNetInflow() != null && data.getMarketPrice() != null) {
            
            int netUnits = data.getCreationUnits() - data.getRedemptionUnits();
            BigDecimal expectedInflow = BigDecimal.valueOf(netUnits * 50000L) // SPY每单位50,000份额
                .multiply(data.getMarketPrice());
            
            BigDecimal actualInflow = data.getDailyNetInflow();
            BigDecimal diff = expectedInflow.subtract(actualInflow).abs();
            
            if (actualInflow.abs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal threshold = actualInflow.abs().multiply(new BigDecimal("0.20")); // 20%阈值
                if (diff.compareTo(threshold) > 0) {
                    result.addWarning("SPY申购赎回单位与净流入不一致，预期: " + expectedInflow + 
                        ", 实际: " + actualInflow);
                }
            }
        }
        
        // 检查份额变化与净流入的一致性
        if (data.getSharesChange() != null && data.getDailyNetInflow() != null && 
            data.getMarketPrice() != null && data.getMarketPrice().compareTo(BigDecimal.ZERO) > 0) {
            
            BigDecimal expectedInflow = BigDecimal.valueOf(data.getSharesChange()).multiply(data.getMarketPrice());
            BigDecimal actualInflow = data.getDailyNetInflow();
            BigDecimal diff = expectedInflow.subtract(actualInflow).abs();
            
            if (actualInflow.abs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal threshold = actualInflow.abs().multiply(new BigDecimal("0.15")); // 15%阈值
                if (diff.compareTo(threshold) > 0) {
                    result.addWarning("SPY份额变化与净流入不一致，基于份额: " + expectedInflow + 
                        ", 报告值: " + actualInflow);
                }
            }
        }
    }
}