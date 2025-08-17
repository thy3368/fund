# US-002B-1A: 股票数据准备和采集 - DuckDB实施方案

## 技术架构优化

### 数据库选型
- **主数据库**: DuckDB (分析型数据库)
- **缓存层**: Redis (实时缓存)
- **消息队列**: Apache Kafka (数据流处理)

### DuckDB架构优势
✅ **高性能OLAP**: 比PostgreSQL快10-100倍的分析查询  
✅ **简化架构**: 单一数据库替代PostgreSQL+InfluxDB  
✅ **列式存储**: 数据压缩比高，存储成本低  
✅ **标准SQL**: 无学习成本，与Spring Data JPA完美集成  

## 核心组件设计

### 1. DuckDB配置和集成

#### Maven依赖
```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- DuckDB Driver -->
    <dependency>
        <groupId>org.duckdb</groupId>
        <artifactId>duckdb_jdbc</artifactId>
        <version>0.9.2</version>
    </dependency>
    
    <!-- HikariCP Connection Pool -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
    </dependency>
</dependencies>
```

#### DuckDB配置类
```java
@Configuration
@EnableJpaRepositories(basePackages = "com.cashflow.repository")
public class DuckDBConfig {
    
    @Bean
    @Primary
    public DataSource duckDBDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setJdbcUrl("jdbc:duckdb:./data/cashflow.duckdb");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // DuckDB特定优化
        config.addDataSourceProperty("memory_limit", "4GB");
        config.addDataSourceProperty("threads", "8");
        config.addDataSourceProperty("max_memory", "4GB");
        
        return new HikariDataSource(config);
    }
    
    @Bean
    public JdbcTemplate duckDBTemplate(@Qualifier("duckDBDataSource") DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(300); // 5分钟查询超时
        return template;
    }
    
    @Bean
    public EntityManagerFactory entityManagerFactory(@Qualifier("duckDBDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.cashflow.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.hbm2ddl.auto", "update");
        jpaProperties.put("hibernate.show_sql", "false");
        jpaProperties.put("hibernate.format_sql", "true");
        jpaProperties.put("hibernate.jdbc.batch_size", "1000");
        jpaProperties.put("hibernate.order_inserts", "true");
        jpaProperties.put("hibernate.order_updates", "true");
        jpaProperties.put("hibernate.jdbc.batch_versioned_data", "true");
        
        factory.setJpaProperties(jpaProperties);
        factory.afterPropertiesSet();
        
        return factory.getObject();
    }
}
```

### 2. 数据模型设计

#### 主数据表结构
```sql
-- 股票基础信息表
CREATE TABLE stock_metadata (
    symbol VARCHAR PRIMARY KEY,
    company_name VARCHAR NOT NULL,
    market VARCHAR NOT NULL, -- NYSE, NASDAQ, SSE, SZSE, HKEX
    sector VARCHAR,
    market_cap_usd DECIMAL(20,2),
    geographic_dimension VARCHAR,
    market_cap_dimension VARCHAR,
    style_dimension VARCHAR,
    sector_dimension VARCHAR,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 资金流动数据表 (按月分区)
CREATE TABLE stock_cash_flow_data (
    id VARCHAR PRIMARY KEY,
    symbol VARCHAR NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    net_inflow DECIMAL(20,2) NOT NULL,
    total_volume DECIMAL(20,2),
    institutional_flow DECIMAL(20,2),
    retail_flow DECIMAL(20,2),
    foreign_flow DECIMAL(20,2),
    
    -- 13维度分类
    geographic_dimension VARCHAR,
    currency_dimension VARCHAR,
    market_cap_dimension VARCHAR,
    style_dimension VARCHAR,
    sector_dimension VARCHAR,
    cross_border_dimension VARCHAR,
    timezone_dimension VARCHAR,
    source_dimension VARCHAR,
    time_dimension VARCHAR,
    risk_sentiment_dimension VARCHAR,
    liquidity_dimension VARCHAR,
    geopolitical_dimension VARCHAR,
    quality_dimension VARCHAR,
    
    data_source VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建分区表 (按月分区提高查询性能)
CREATE TABLE stock_cash_flow_data_202501 AS 
    SELECT * FROM stock_cash_flow_data 
    WHERE timestamp >= '2025-01-01' AND timestamp < '2025-02-01';

-- 创建列式存储索引
CREATE INDEX idx_cash_flow_symbol_time ON stock_cash_flow_data (symbol, timestamp);
CREATE INDEX idx_cash_flow_dimensions ON stock_cash_flow_data 
    (geographic_dimension, sector_dimension, risk_sentiment_dimension);
CREATE INDEX idx_cash_flow_data_source ON stock_cash_flow_data (data_source, timestamp);
```

#### JPA实体类
```java
@Entity
@Table(name = "stock_cash_flow_data")
@Cacheable
public class StockCashFlowData {
    
    @Id
    private String id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal netInflow;
    
    @Column(precision = 20, scale = 2)
    private BigDecimal totalVolume;
    
    @Column(precision = 20, scale = 2)
    private BigDecimal institutionalFlow;
    
    @Column(precision = 20, scale = 2)
    private BigDecimal retailFlow;
    
    @Column(precision = 20, scale = 2)
    private BigDecimal foreignFlow;
    
    // 13维度枚举字段
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private GeographicDimension geographicDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CurrencyDimension currencyDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private MarketCapDimension marketCapDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private StyleDimension styleDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SectorDimension sectorDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private CrossBorderDimension crossBorderDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TimezoneDimension timezoneDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SourceDimension sourceDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private TimeDimension timeDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private RiskSentimentDimension riskSentimentDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LiquidityDimension liquidityDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private GeopoliticalDimension geopoliticalDimension;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private QualityDimension qualityDimension;
    
    @Column(nullable = false, length = 20)
    private String dataSource;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // Getters, setters, builders...
}
```

### 3. 高性能数据访问层

#### Repository接口
```java
@Repository
public interface StockCashFlowRepository extends JpaRepository<StockCashFlowData, String> {
    
    @Query(value = """
        SELECT * FROM stock_cash_flow_data 
        WHERE symbol = ?1 AND timestamp >= ?2 AND timestamp <= ?3
        ORDER BY timestamp DESC
        """, nativeQuery = true)
    List<StockCashFlowData> findBySymbolAndTimestampBetween(
        String symbol, LocalDateTime start, LocalDateTime end);
    
    @Query(value = """
        SELECT 
            geographic_dimension,
            SUM(net_inflow) as total_inflow,
            COUNT(*) as record_count,
            AVG(net_inflow) as avg_inflow
        FROM stock_cash_flow_data 
        WHERE timestamp >= ?1 AND timestamp <= ?2
        GROUP BY geographic_dimension
        ORDER BY total_inflow DESC
        """, nativeQuery = true)
    List<Object[]> findGlobalFlowSummary(LocalDateTime start, LocalDateTime end);
}
```

#### 高性能分析服务
```java
@Service
@Transactional(readOnly = true)
public class CashFlowAnalysisService {
    
    private final JdbcTemplate duckDBTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Cacheable(value = "globalFlowAnalysis", key = "#request.hashCode()")
    public GlobalFlowAnalysisResult analyzeGlobalFlow(GlobalFlowRequest request) {
        
        String sql = """
            WITH hourly_flows AS (
                SELECT 
                    DATE_TRUNC('hour', timestamp) as flow_hour,
                    geographic_dimension,
                    currency_dimension,
                    risk_sentiment_dimension,
                    SUM(net_inflow) as hourly_inflow,
                    SUM(total_volume) as hourly_volume,
                    COUNT(DISTINCT symbol) as stock_count
                FROM stock_cash_flow_data 
                WHERE timestamp >= ? AND timestamp <= ?
                    AND quality_dimension IN ('HQ', 'MQ')
                GROUP BY 1, 2, 3, 4
            ),
            flow_trends AS (
                SELECT *,
                    LAG(hourly_inflow, 1) OVER (
                        PARTITION BY geographic_dimension, currency_dimension 
                        ORDER BY flow_hour
                    ) as prev_hour_inflow,
                    AVG(hourly_inflow) OVER (
                        PARTITION BY geographic_dimension, currency_dimension 
                        ORDER BY flow_hour 
                        ROWS 23 PRECEDING
                    ) as ma24h_inflow
                FROM hourly_flows
            ),
            risk_sentiment_pivot AS (
                SELECT 
                    flow_hour,
                    geographic_dimension,
                    SUM(CASE WHEN risk_sentiment_dimension = 'RISK_ON' THEN hourly_inflow ELSE 0 END) as risk_on_flow,
                    SUM(CASE WHEN risk_sentiment_dimension = 'RISK_OFF' THEN hourly_inflow ELSE 0 END) as risk_off_flow,
                    SUM(CASE WHEN risk_sentiment_dimension = 'PANIC' THEN hourly_inflow ELSE 0 END) as panic_flow
                FROM flow_trends
                GROUP BY 1, 2
            )
            SELECT 
                ft.flow_hour,
                ft.geographic_dimension,
                ft.currency_dimension,
                ft.hourly_inflow,
                ft.prev_hour_inflow,
                ft.ma24h_inflow,
                CASE 
                    WHEN ft.prev_hour_inflow IS NULL OR ft.prev_hour_inflow = 0 THEN NULL
                    ELSE (ft.hourly_inflow - ft.prev_hour_inflow) / ABS(ft.prev_hour_inflow) * 100
                END as hour_change_pct,
                rsp.risk_on_flow,
                rsp.risk_off_flow,
                rsp.panic_flow,
                CASE 
                    WHEN rsp.risk_on_flow > rsp.risk_off_flow + rsp.panic_flow THEN 'RISK_ON_DOMINANT'
                    WHEN rsp.risk_off_flow + rsp.panic_flow > rsp.risk_on_flow THEN 'RISK_OFF_DOMINANT'
                    ELSE 'BALANCED'
                END as market_sentiment
            FROM flow_trends ft
            LEFT JOIN risk_sentiment_pivot rsp ON ft.flow_hour = rsp.flow_hour 
                AND ft.geographic_dimension = rsp.geographic_dimension
            WHERE ft.flow_hour >= DATE_TRUNC('hour', NOW() - INTERVAL '24 hours')
            ORDER BY ft.flow_hour DESC, ft.hourly_inflow DESC
            LIMIT 1000;
            """;
            
        List<Map<String, Object>> results = duckDBTemplate.queryForList(
            sql, request.getStartTime(), request.getEndTime());
            
        return GlobalFlowAnalysisResult.builder()
            .analysisTime(LocalDateTime.now())
            .timeRange(request.getTimeRange())
            .flowData(convertToFlowData(results))
            .summary(calculateSummary(results))
            .build();
    }
    
    @Cacheable(value = "crossBorderFlow", key = "#timeRange")
    public CrossBorderFlowResult analyzeCrossBorderFlow(String timeRange) {
        String sql = """
            SELECT 
                cross_border_dimension,
                geographic_dimension as source_market,
                LEAD(geographic_dimension) OVER (ORDER BY net_inflow DESC) as target_market,
                SUM(net_inflow) as total_flow,
                COUNT(DISTINCT symbol) as affected_stocks,
                AVG(net_inflow) as avg_flow_per_stock,
                STDDEV(net_inflow) as flow_volatility
            FROM stock_cash_flow_data 
            WHERE timestamp >= CURRENT_DATE - INTERVAL '7 days'
                AND cross_border_dimension IN ('USD_FLOW', 'EUR_FLOW', 'JPY_CARRY', 'SB', 'NB')
            GROUP BY cross_border_dimension, geographic_dimension
            HAVING SUM(ABS(net_inflow)) > 1000000  -- 过滤小额流动
            ORDER BY total_flow DESC;
            """;
            
        List<Map<String, Object>> results = duckDBTemplate.queryForList(sql);
        return CrossBorderFlowResult.fromQueryResults(results);
    }
}
```

### 4. 实时数据处理引擎

#### 数据采集处理器
```java
@Service
public class RealTimeCashFlowProcessor {
    
    private final List<CashFlowDataSource> dataSources;
    private final StockCashFlowRepository repository;
    private final DimensionClassifier classifier;
    private final JdbcTemplate duckDBTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Scheduled(fixedRate = 300000) // 5分钟
    @Async("cashFlowTaskExecutor")
    public void processRealTimeData() {
        log.info("开始处理实时资金流数据 - {}", LocalDateTime.now());
        
        Set<String> activeSymbols = getActiveSymbols();
        log.info("处理股票数量: {}", activeSymbols.size());
        
        dataSources.parallelStream()
            .filter(CashFlowDataSource::isHealthy)
            .forEach(source -> processDataSourceAsync(source, activeSymbols));
    }
    
    private void processDataSourceAsync(CashFlowDataSource source, Set<String> symbols) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        try {
            List<StockCashFlowData> processedData = source.getRealTimeCashFlow(symbols)
                .map(this::enrichWithDimensions)
                .filter(this::validateDataQuality)
                .collectList()
                .block(Duration.ofMinutes(2));
                
            if (processedData != null && !processedData.isEmpty()) {
                batchInsertToDuckDB(processedData);
                updateRedisCache(processedData);
                publishToKafka(processedData);
                
                log.info("数据源 {} 处理完成: {} 条记录", 
                        source.getClass().getSimpleName(), processedData.size());
            }
        } catch (Exception e) {
            log.error("数据源 {} 处理失败", source.getClass().getSimpleName(), e);
        } finally {
            stopWatch.stop();
            log.info("数据源 {} 处理耗时: {}ms", 
                    source.getClass().getSimpleName(), stopWatch.getTotalTimeMillis());
        }
    }
    
    private void batchInsertToDuckDB(List<StockCashFlowData> dataList) {
        String sql = """
            INSERT INTO stock_cash_flow_data (
                id, symbol, timestamp, net_inflow, total_volume, 
                institutional_flow, retail_flow, foreign_flow,
                geographic_dimension, currency_dimension, market_cap_dimension,
                style_dimension, sector_dimension, cross_border_dimension,
                timezone_dimension, source_dimension, time_dimension,
                risk_sentiment_dimension, liquidity_dimension, 
                geopolitical_dimension, quality_dimension,
                data_source, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
        List<Object[]> batchArgs = dataList.stream()
            .map(this::convertToObjectArray)
            .collect(Collectors.toList());
            
        duckDBTemplate.batchUpdate(sql, batchArgs);
        log.info("批量插入DuckDB完成: {} 条记录", batchArgs.size());
    }
    
    private void updateRedisCache(List<StockCashFlowData> dataList) {
        dataList.forEach(data -> {
            String cacheKey = "cashflow:realtime:" + data.getSymbol();
            redisTemplate.opsForValue().set(cacheKey, data, Duration.ofMinutes(10));
        });
    }
    
    private StockCashFlowData enrichWithDimensions(StockCashFlowData data) {
        // 使用维度分类器进行13维度分类
        classifier.classifyAllDimensions(data);
        return data;
    }
}
```

### 5. 性能优化配置

#### 应用配置
```yaml
# application-duckdb.yml
spring:
  datasource:
    driver-class-name: org.duckdb.DuckDBDriver
    url: jdbc:duckdb:./data/cashflow.duckdb?memory_limit=4GB&threads=8
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc.batch_size: 1000
        order_inserts: true
        order_updates: true
        jdbc.batch_versioned_data: true
        
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

  task:
    execution:
      pool:
        core-size: 8
        max-size: 16
        queue-capacity: 100
        thread-name-prefix: "cash-flow-"

# 缓存配置
cache:
  redis:
    time-to-live: 600  # 10分钟
    cache-null-values: false
    
# DuckDB优化参数
duckdb:
  memory-limit: 4GB
  threads: 8
  max-memory: 4GB
  enable-profiling: false
  enable-progress-bar: false
```

## 验收标准

### 功能验收
✅ DuckDB成功初始化并创建必要的表结构  
✅ 支持13维度数据的高效存储和查询  
✅ 实时数据采集每5分钟更新一次  
✅ 批量插入性能 > 10,000 records/second  
✅ 复杂OLAP查询响应时间 < 5秒  

### 性能验收  
✅ 单表数据量支持 > 1亿条记录  
✅ 多维度聚合查询 < 3秒响应  
✅ 并发查询支持 > 50 QPS  
✅ 内存使用 < 4GB  
✅ 磁盘使用压缩比 > 5:1  

这个DuckDB方案将大幅提升系统的分析性能，同时简化架构复杂度。