package com.tanggo.fund.cashflow.spy.service;

import com.tanggo.fund.cashflow.spy.dto.SpyFlowData;
import com.tanggo.fund.cashflow.spy.entity.SpyRawData;
import com.tanggo.fund.cashflow.spy.repository.SpyDataSourceRepository;
import com.tanggo.fund.cashflow.spy.repository.SpyRawDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SPY数据采集器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpyDataCollector {

    private final SpyDataSourceRepository dataSourceService;
    private final SpyRawDataRepository rawDataRepository;
    private final SpyDataValidationService validationService;
    private final SpyCalculationService calculationService;

    @Scheduled(fixedRate = 300000) // 5分钟执行一次
    public void collectSpyData() {
        log.info("开始SPY数据采集...");

        try {
            // 1. 从主数据源获取数据
            SpyFlowData primaryData = dataSourceService.fetchFromPrimarySource();

            // 2. 数据验证
            ValidationResult validation = validationService.validateSpyData(primaryData);

            if (validation.isValid()) {
                // 3. 存储原始数据
                SpyRawData rawData = convertToRawData(primaryData);
                rawDataRepository.save(rawData);

                // 4. 异步触发计算
                calculationService.calculateSpyFlowAsync(rawData);

                log.info("SPY数据采集成功: 净流入=${}, 数据源={}",
                    primaryData.getDailyNetInflow(), primaryData.getDataSource());
            } else {
                // 使用备用数据源
                handleDataSourceFailover(validation);
            }

        } catch (Exception e) {
            log.error("SPY数据采集失败", e);
            handleCollectionFailure(e);
        }
    }

    /**
     * 处理数据源故障转移
     */
    private void handleDataSourceFailover(ValidationResult validation) {
        log.warn("主数据源验证失败: {}, 尝试备用数据源", validation.getErrors());

        try {
            SpyFlowData backupData = dataSourceService.fetchFromBackupSource();
            ValidationResult backupValidation = validationService.validateSpyData(backupData);

            if (backupValidation.isValid()) {
                SpyRawData rawData = convertToRawData(backupData);
                rawData.setDataSource("BACKUP_" + rawData.getDataSource());
                rawDataRepository.save(rawData);
                calculationService.calculateSpyFlowAsync(rawData);

                log.info("备用数据源采集成功: {}", backupData.getDataSource());
            } else {
                log.error("备用数据源验证也失败: {}", backupValidation.getErrors());
                // 这里可以发送告警
            }
        } catch (Exception e) {
            log.error("备用数据源也失败", e);
            // 发送告警通知
        }
    }

    /**
     * 处理采集失败
     */
    private void handleCollectionFailure(Exception e) {
        log.error("SPY数据采集完全失败", e);
        // 这里可以集成告警系统，比如发送邮件、Slack通知等
        // alertService.sendAlert("SPY数据采集失败: " + e.getMessage());
    }

    /**
     * 转换为原始数据实体
     */
    private SpyRawData convertToRawData(SpyFlowData data) {
        // 计算验证数据
        BigDecimal calculatedInflow = BigDecimal.ZERO;
        if (data.getSharesChange() != null && data.getMarketPrice() != null) {
            calculatedInflow = data.getMarketPrice().multiply(BigDecimal.valueOf(data.getSharesChange()));
        }

        BigDecimal flowIntensity = BigDecimal.ZERO;
        if (data.getAum() != null && data.getDailyNetInflow() != null && data.getAum().compareTo(BigDecimal.ZERO) > 0) {
            flowIntensity = data.getDailyNetInflow().divide(data.getAum(), 6, BigDecimal.ROUND_HALF_UP);
        }

        return SpyRawData.builder()
            .dataDate(data.getDataDate())
            .timestamp(Instant.now())
            .ticker(data.getTicker())
            .aum(data.getAum())
            .sharesOutstanding(data.getSharesOutstanding())
            .nav(data.getNav())
            .marketPrice(data.getMarketPrice())
            .dailyNetInflow(data.getDailyNetInflow())
            .totalInflow(data.getTotalInflow())
            .totalOutflow(data.getTotalOutflow())
            .creationUnits(data.getCreationUnits())
            .redemptionUnits(data.getRedemptionUnits())
            .sharesChange(data.getSharesChange())
            .calculatedInflow(calculatedInflow)
            .flowIntensity(flowIntensity)
            .dataSource(data.getDataSource())
            .confidenceScore(data.getConfidenceScore())
            .build();
    }
}
