package com.tanggo.fund.cashflow.spy.repository;

import com.tanggo.fund.cashflow.spy.entity.SpyFlowResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SPY计算结果仓库
 */
@Repository
public interface SpyFlowResultRepository extends JpaRepository<SpyFlowResult, Long> {
    
    /**
     * 根据日期查找结果
     */
    Optional<SpyFlowResult> findByDataDate(LocalDate dataDate);
    
    /**
     * 查找最新的结果
     */
    Optional<SpyFlowResult> findTopByOrderByDataDateDesc();
    
    /**
     * 根据日期范围查找结果
     */
    List<SpyFlowResult> findByDataDateBetweenOrderByDataDateDesc(LocalDate startDate, LocalDate endDate);
    
    /**
     * 获取最近N天的数据
     */
    @Query("SELECT s FROM SpyFlowResult s WHERE s.dataDate >= :startDate ORDER BY s.dataDate DESC")
    List<SpyFlowResult> findRecentResults(@Param("startDate") LocalDate startDate);
    
    /**
     * 获取置信度统计
     */
    @Query("SELECT AVG(s.overallConfidence), MIN(s.overallConfidence), MAX(s.overallConfidence) " +
           "FROM SpyFlowResult s WHERE s.dataDate >= :startDate")
    Object[] getConfidenceStats(@Param("startDate") LocalDate startDate);
    
    /**
     * 获取净流入统计
     */
    @Query("SELECT SUM(s.finalNetInflow), AVG(s.finalNetInflow), COUNT(s) " +
           "FROM SpyFlowResult s WHERE s.dataDate >= :startDate")
    Object[] getNetInflowStats(@Param("startDate") LocalDate startDate);
    
    /**
     * 获取数据质量评分平均值
     */
    @Query("SELECT AVG(s.dataQualityScore) FROM SpyFlowResult s WHERE s.dataDate >= :startDate")
    Double getAverageDataQualityScore(@Param("startDate") LocalDate startDate);
    
    /**
     * 查找验证未通过的记录
     */
    List<SpyFlowResult> findByValidationPassedFalseAndDataDateGreaterThanEqual(LocalDate startDate);
}