package com.tanggo.fund.cashflow.spy.repository;

import com.tanggo.fund.cashflow.spy.entity.SpyRawData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SPY原始数据仓库
 */
@Repository
public interface SpyRawDataRepository extends JpaRepository<SpyRawData, Long> {
    
    /**
     * 根据日期和数据源查找数据
     */
    Optional<SpyRawData> findByDataDateAndDataSource(LocalDate dataDate, String dataSource);
    
    /**
     * 根据日期查找数据
     */
    Optional<SpyRawData> findByDataDate(LocalDate dataDate);
    
    /**
     * 查找最新的数据记录
     */
    Optional<SpyRawData> findTopByOrderByDataDateDescTimestampDesc();
    
    /**
     * 根据日期范围查找数据
     */
    List<SpyRawData> findByDataDateBetweenOrderByDataDateDesc(LocalDate startDate, LocalDate endDate);
    
    /**
     * 根据数据源查找最近N天的数据
     */
    @Query("SELECT s FROM SpyRawData s WHERE s.dataSource = :dataSource " +
           "AND s.dataDate >= :startDate ORDER BY s.dataDate DESC")
    List<SpyRawData> findRecentByDataSource(@Param("dataSource") String dataSource, 
                                           @Param("startDate") LocalDate startDate);
    
    /**
     * 获取数据源可用性统计
     */
    @Query("SELECT s.dataSource, COUNT(s) as count FROM SpyRawData s " +
           "WHERE s.dataDate >= :startDate GROUP BY s.dataSource")
    List<Object[]> getDataSourceAvailabilityStats(@Param("startDate") LocalDate startDate);
    
    /**
     * 获取置信度平均值
     */
    @Query("SELECT AVG(s.confidenceScore) FROM SpyRawData s WHERE s.dataDate >= :startDate")
    Double getAverageConfidenceScore(@Param("startDate") LocalDate startDate);
}