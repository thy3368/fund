package com.tanggo.fund.cashflow.spy.controller;

import com.tanggo.fund.cashflow.spy.entity.SpyFlowResult;
import com.tanggo.fund.cashflow.spy.entity.SpyRawData;
import com.tanggo.fund.cashflow.spy.repository.SpyFlowResultRepository;
import com.tanggo.fund.cashflow.spy.repository.SpyRawDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SPY资金流向API控制器
 */
@RestController
@RequestMapping("/api/spy")
@RequiredArgsConstructor
@Slf4j
public class SpyFlowController {
    
    private final SpyFlowResultRepository flowResultRepository;
    private final SpyRawDataRepository rawDataRepository;
    
    /**
     * 获取最新的SPY流向数据
     */
    @GetMapping("/latest")
    public ResponseEntity<SpyFlowResult> getLatestFlow() {
        Optional<SpyFlowResult> latest = flowResultRepository.findTopByOrderByDataDateDesc();
        
        return latest.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 根据日期获取SPY流向数据
     */
    @GetMapping("/date/{date}")
    public ResponseEntity<SpyFlowResult> getFlowByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Optional<SpyFlowResult> result = flowResultRepository.findByDataDate(date);
        
        return result.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 获取日期范围内的SPY流向数据
     */
    @GetMapping("/range")
    public ResponseEntity<List<SpyFlowResult>> getFlowByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<SpyFlowResult> results = flowResultRepository.findByDataDateBetweenOrderByDataDateDesc(startDate, endDate);
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 获取最近N天的SPY流向数据
     */
    @GetMapping("/recent/{days}")
    public ResponseEntity<List<SpyFlowResult>> getRecentFlow(@PathVariable int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        List<SpyFlowResult> results = flowResultRepository.findRecentResults(startDate);
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 获取SPY数据统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "30") int days) {
        
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        Map<String, Object> stats = new HashMap<>();
        
        // 置信度统计
        Object[] confidenceStats = flowResultRepository.getConfidenceStats(startDate);
        if (confidenceStats != null && confidenceStats.length >= 3) {
            Map<String, Object> confidence = new HashMap<>();
            confidence.put("average", confidenceStats[0]);
            confidence.put("minimum", confidenceStats[1]);
            confidence.put("maximum", confidenceStats[2]);
            stats.put("confidence", confidence);
        }
        
        // 净流入统计
        Object[] inflowStats = flowResultRepository.getNetInflowStats(startDate);
        if (inflowStats != null && inflowStats.length >= 3) {
            Map<String, Object> inflow = new HashMap<>();
            inflow.put("total", inflowStats[0]);
            inflow.put("average", inflowStats[1]);
            inflow.put("count", inflowStats[2]);
            stats.put("netInflow", inflow);
        }
        
        // 数据质量统计
        Double avgQuality = flowResultRepository.getAverageDataQualityScore(startDate);
        stats.put("averageDataQuality", avgQuality);
        
        // 验证失败记录数
        List<SpyFlowResult> failedValidations = flowResultRepository
            .findByValidationPassedFalseAndDataDateGreaterThanEqual(startDate);
        stats.put("validationFailures", failedValidations.size());
        
        // 数据可用性统计
        List<Object[]> availabilityStats = rawDataRepository.getDataSourceAvailabilityStats(startDate);
        Map<String, Object> availability = new HashMap<>();
        availability.put("dataSources", availabilityStats);
        
        Double avgConfidence = rawDataRepository.getAverageConfidenceScore(startDate);
        availability.put("averageConfidence", avgConfidence);
        stats.put("dataAvailability", availability);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取原始数据（用于调试）
     */
    @GetMapping("/raw/{date}")
    public ResponseEntity<List<SpyRawData>> getRawDataByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<SpyRawData> rawData = rawDataRepository.findByDataDateBetweenOrderByDataDateDesc(date, date);
        
        return ResponseEntity.ok(rawData);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        
        // 检查最新数据
        Optional<SpyFlowResult> latest = flowResultRepository.findTopByOrderByDataDateDesc();
        health.put("hasLatestData", latest.isPresent());
        
        if (latest.isPresent()) {
            health.put("latestDataDate", latest.get().getDataDate());
            health.put("latestConfidence", latest.get().getOverallConfidence());
            health.put("latestValidation", latest.get().getValidationPassed());
        }
        
        // 检查数据新鲜度
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        boolean hasRecentData = latest.isPresent() && 
            (latest.get().getDataDate().equals(today) || latest.get().getDataDate().equals(yesterday));
        health.put("hasRecentData", hasRecentData);
        
        // 系统状态
        health.put("status", hasRecentData ? "HEALTHY" : "WARNING");
        health.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(health);
    }
}