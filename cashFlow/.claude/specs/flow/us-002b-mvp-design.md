# US-002B MVP设计文档 - SPY & 上证综合指数

## 概述

### MVP范围
本MVP专注于实现两个核心指数的资金流向监控：
- **SPY (SPDR S&P 500 ETF)**: 代表美股市场
- **上证综合指数 (000001.SS)**: 代表中国A股市场

### 业务目标
- 验证多源融合算法的可行性
- 建立完整的数据采集->加工->查询流程
- 为后续扩展到42个指数打下技术基础

## 系统架构

### 整体架构图
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   数据采集层     │    │   数据加工层     │    │   数据服务层     │
│                │    │                │    │                │
│ SPY数据采集     │────▶│ @Async异步计算   │────▶│ REST API       │
│ 上证数据采集     │    │ 多源融合引擎     │    │ WebSocket      │
│ @Scheduled定时   │    │ DuckDB存储      │    │ Redis缓存      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
              │                     │                     │
              └─────────────────────┴─────────────────────┘
                        ThreadPoolExecutor
                        (轻量级异步处理)
```

### 技术栈
- **后端**: Spring Boot 3.5.4 + Java 17
- **数据库**: DuckDB (主库) + Redis (缓存)
- **异步处理**: Spring @Async + ThreadPoolExecutor
- **API**: RESTful + WebSocket
- **监控**: Prometheus + Grafana

## 数据模型设计

### DuckDB表结构

#### 1. ETF原始数据表 (etf_raw_data)
```sql
CREATE TABLE etf_raw_data (
    id BIGINT PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,           -- SPY
    data_date DATE NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    -- ETF基本信息
    aum DECIMAL(15,2),                     -- 资产管理规模
    shares_outstanding BIGINT,             -- 流通份额
    nav DECIMAL(10,4),                     -- 净值
    market_price DECIMAL(10,4),           -- 市场价格
    
    -- 资金流向数据
    daily_net_inflow DECIMAL(15,2),       -- 日净流入
    total_inflow DECIMAL(15,2),           -- 总流入
    total_outflow DECIMAL(15,2),          -- 总流出
    creation_units INTEGER,               -- 申购单位数
    redemption_units INTEGER,             -- 赎回单位数
    shares_change BIGINT,                 -- 份额变化
    
    -- 元数据
    data_source VARCHAR(50),              -- 数据源
    confidence_score INTEGER,             -- 置信度(0-100)
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(ticker, data_date, data_source)
);
```

#### 2. 中国指数多源数据表 (china_index_raw_data)
```sql
CREATE TABLE china_index_raw_data (
    id BIGINT PRIMARY KEY,
    index_code VARCHAR(20) NOT NULL,      -- 000001.SS
    data_date DATE NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    -- 北向资金数据 (权重40%)
    northbound_net_inflow DECIMAL(15,2),  -- 北向资金净流入(人民币)
    northbound_balance DECIMAL(15,2),     -- 北向资金余额
    northbound_turnover DECIMAL(15,2),    -- 北向资金成交额
    
    -- ETF流向数据 (权重25%)
    related_etf_inflow DECIMAL(15,2),     -- 相关ETF流入
    etf_list TEXT,                        -- 相关ETF列表JSON
    
    -- 期货持仓数据 (权重20%)
    futures_net_position DECIMAL(15,2),   -- 期货净持仓变化
    futures_volume DECIMAL(15,2),         -- 期货成交量
    futures_contract VARCHAR(10),         -- 期货合约代码 IF/IC/IH
    
    -- 融资融券数据 (权重15%)
    margin_balance DECIMAL(15,2),         -- 融资余额
    margin_change DECIMAL(15,2),          -- 融资余额变化
    short_balance DECIMAL(15,2),          -- 融券余额
    
    -- 元数据
    data_source VARCHAR(50),
    confidence_score INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(index_code, data_date, data_source)
);
```

#### 3. 融合计算结果表 (index_flow_result)
```sql
CREATE TABLE index_flow_result (
    id BIGINT PRIMARY KEY,
    index_symbol VARCHAR(20) NOT NULL,    -- SPY / 000001.SS
    index_name VARCHAR(100),              -- SPDR S&P 500 ETF / 上证综合指数
    data_date DATE NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    -- 融合计算结果
    final_net_inflow DECIMAL(15,2),       -- 最终净流入(统一美元)
    final_net_inflow_cny DECIMAL(15,2),   -- 最终净流入(人民币)
    flow_intensity DECIMAL(8,4),          -- 流入强度 (净流入/市值)
    
    -- 各数据源贡献
    source1_contribution DECIMAL(15,2),   -- 数据源1贡献
    source1_weight DECIMAL(5,4),         -- 数据源1权重
    source2_contribution DECIMAL(15,2),   -- 数据源2贡献  
    source2_weight DECIMAL(5,4),         -- 数据源2权重
    source3_contribution DECIMAL(15,2),   -- 数据源3贡献
    source3_weight DECIMAL(5,4),         -- 数据源3权重
    source4_contribution DECIMAL(15,2),   -- 数据源4贡献
    source4_weight DECIMAL(5,4),         -- 数据源4权重
    
    -- 质量指标
    overall_confidence DECIMAL(5,2),      -- 整体置信度
    data_quality_score DECIMAL(5,2),      -- 数据质量评分
    calculation_method VARCHAR(50),       -- 计算方法
    
    -- 13维度分类
    geographic_dimension VARCHAR(50),     -- 地理维度
    currency_dimension VARCHAR(10),       -- 货币维度  
    market_cap_dimension VARCHAR(20),     -- 市值维度
    sector_dimension VARCHAR(50),         -- 行业维度
    
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(index_symbol, data_date)
);
```

#### 4. 数据质量监控表 (data_quality_monitor)
```sql
CREATE TABLE data_quality_monitor (
    id BIGINT PRIMARY KEY,
    check_date DATE NOT NULL,
    check_timestamp TIMESTAMP NOT NULL,
    
    -- SPY质量指标
    spy_data_completeness DECIMAL(5,2),   -- SPY数据完整性
    spy_source_availability INTEGER,      -- SPY数据源可用数
    spy_calculation_accuracy DECIMAL(5,2), -- SPY计算准确性
    
    -- 上证指数质量指标
    sse_data_completeness DECIMAL(5,2),   -- 上证数据完整性
    sse_northbound_reliability DECIMAL(5,2), -- 北向资金可靠性
    sse_etf_reliability DECIMAL(5,2),     -- ETF数据可靠性
    sse_futures_reliability DECIMAL(5,2), -- 期货数据可靠性
    sse_margin_reliability DECIMAL(5,2),  -- 融资融券可靠性
    
    -- 整体指标
    overall_system_health DECIMAL(5,2),   -- 系统整体健康度
    cross_validation_pass_rate DECIMAL(5,2), -- 交叉验证通过率
    
    created_at TIMESTAMP DEFAULT NOW()
);
```

## 数据采集设计

### SPY数据采集

#### 数据源配置
```yaml
spy_data_sources:
  primary:
    - name: "ETF.com"
      endpoint: "https://api.etf.com/v1/funds/SPY/flows"
      rate_limit: "1000/day"
      priority: 1
      
  secondary:
    - name: "Yahoo Finance"
      endpoint: "https://query1.finance.yahoo.com/v8/finance/chart/SPY"
      rate_limit: "2000/hour"
      priority: 2
      
  backup:
    - name: "Alpha Vantage"
      endpoint: "https://www.alphavantage.co/query?function=ETF_PROFILE&symbol=SPY"
      rate_limit: "5/minute"
      priority: 3
```

#### 采集流程
```java
@Component
public class SpyDataCollector {
    
    @Scheduled(fixedRate = 300000) // 5分钟
    public void collectSpyData() {
        try {
            // 1. 从主数据源获取数据
            SpyFlowData primaryData = etfComService.getSpyFlows();
            
            // 2. 数据验证
            if (validateSpyData(primaryData)) {
                // 3. 存储原始数据
                rawDataRepository.save(primaryData);
                
                // 4. 异步触发融合计算
                fusionCalculationService.calculateSpyFlowAsync(primaryData);
                
                log.info("SPY数据采集成功: 净流入=${}", primaryData.getNetInflow());
            } else {
                // 使用备用数据源
                handleDataSourceFailover();
            }
        } catch (Exception e) {
            log.error("SPY数据采集失败", e);
            alertService.sendAlert("SPY数据采集异常");
        }
    }
}
```

### 上证指数数据采集

#### 多源数据采集器
```java
@Component
public class SseIndexDataCollector {
    
    @Scheduled(fixedRate = 300000) // 5分钟
    public void collectSseIndexData() {
        SseIndexRawData.Builder builder = SseIndexRawData.builder()
            .indexCode("000001.SS")
            .dataDate(LocalDate.now());
            
        // 1. 采集北向资金数据 (权重40%)
        try {
            NorthboundData northbound = eastMoneyService.getNorthboundFlows();
            builder.northboundNetInflow(northbound.getNetInflow())
                   .northboundBalance(northbound.getBalance());
        } catch (Exception e) {
            log.warn("北向资金数据采集失败", e);
        }
        
        // 2. 采集相关ETF数据 (权重25%)
        try {
            List<String> relatedEtfs = Arrays.asList("FXI", "ASHR", "510050.SS");
            BigDecimal etfInflow = calculateEtfInflow(relatedEtfs);
            builder.relatedEtfInflow(etfInflow);
        } catch (Exception e) {
            log.warn("ETF数据采集失败", e);
        }
        
        // 3. 采集期货数据 (权重20%)
        try {
            FuturesData futures = cffexService.getFuturesPosition("IF");
            builder.futuresNetPosition(futures.getNetPosition())
                   .futuresVolume(futures.getVolume());
        } catch (Exception e) {
            log.warn("期货数据采集失败", e);
        }
        
        // 4. 采集融资融券数据 (权重15%)
        try {
            MarginData margin = exchangeService.getMarginData();
            builder.marginBalance(margin.getBalance())
                   .marginChange(margin.getChange());
        } catch (Exception e) {
            log.warn("融资融券数据采集失败", e);
        }
        
        // 5. 保存原始数据
        SseIndexRawData rawData = builder.build();
        rawDataRepository.save(rawData);
        
        // 6. 异步触发融合计算
        fusionCalculationService.calculateSseFlowAsync(rawData);
    }
}
```

## 数据加工设计

### 异步处理配置

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean("calculationExecutor")
    public TaskExecutor calculationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("calculation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean("dataProcessingExecutor")
    public TaskExecutor dataProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("data-processing-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 多源融合计算服务

```java
@Service
public class FusionCalculationService {
    
    private final ExecutorService calculationExecutor;
    
    public FusionCalculationService() {
        this.calculationExecutor = Executors.newFixedThreadPool(4);
    }
    
    @Async("calculationExecutor")
    public CompletableFuture<Void> calculateSpyFlowAsync(SpyFlowData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                IndexFlowResult result = calculateSpyFlow(data);
                
                // 保存计算结果
                resultRepository.save(result);
                
                // 发送实时通知
                webSocketService.broadcastFlowUpdate(result);
                
                log.info("SPY融合计算完成: {}", result.getFinalNetInflow());
            } catch (Exception e) {
                log.error("SPY融合计算失败", e);
                alertService.sendAlert("SPY计算异常: " + e.getMessage());
            }
        }, calculationExecutor);
    }
    
    @Async("calculationExecutor")  
    public CompletableFuture<Void> calculateSseFlowAsync(SseIndexRawData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                IndexFlowResult result = calculateSseFlow(data);
                
                // 保存计算结果
                resultRepository.save(result);
                
                // 发送实时通知
                webSocketService.broadcastFlowUpdate(result);
                
                log.info("上证指数融合计算完成: {}", result.getFinalNetInflow());
            } catch (Exception e) {
                log.error("上证指数融合计算失败", e);
                alertService.sendAlert("上证指数计算异常: " + e.getMessage());
            }
        }, calculationExecutor);
    }
    
    private IndexFlowResult calculateSpyFlow(SpyRawData data) {
        // SPY直接使用ETF流向数据
        return IndexFlowResult.builder()
            .indexSymbol("SPY")
            .indexName("SPDR S&P 500 ETF")
            .finalNetInflow(data.getDailyNetInflow())
            .source1Contribution(data.getDailyNetInflow())
            .source1Weight(BigDecimal.ONE)
            .overallConfidence(data.getConfidenceScore())
            .calculationMethod("DIRECT_ETF")
            .geographicDimension("North America")
            .currencyDimension("USD")
            .marketCapDimension("Large Cap")
            .build();
    }
    
    private IndexFlowResult calculateSseFlow(SseIndexRawData data) {
        // 上证指数使用多源融合
        FusionWeights weights = getWeights(data);
        
        BigDecimal finalInflow = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        // 北向资金 (权重40%)
        if (data.getNorthboundNetInflow() != null) {
            BigDecimal contribution = data.getNorthboundNetInflow().multiply(weights.getNorthboundWeight());
            finalInflow = finalInflow.add(contribution);
            totalWeight = totalWeight.add(weights.getNorthboundWeight());
        }
        
        // ETF流向 (权重25%)
        if (data.getRelatedEtfInflow() != null) {
            BigDecimal contribution = data.getRelatedEtfInflow().multiply(weights.getEtfWeight());
            finalInflow = finalInflow.add(contribution);
            totalWeight = totalWeight.add(weights.getEtfWeight());
        }
        
        // 期货持仓 (权重20%)
        if (data.getFuturesNetPosition() != null) {
            BigDecimal contribution = data.getFuturesNetPosition().multiply(weights.getFuturesWeight());
            finalInflow = finalInflow.add(contribution);
            totalWeight = totalWeight.add(weights.getFuturesWeight());
        }
        
        // 融资融券 (权重15%)
        if (data.getMarginChange() != null) {
            BigDecimal contribution = data.getMarginChange().multiply(weights.getMarginWeight());
            finalInflow = finalInflow.add(contribution);
            totalWeight = totalWeight.add(weights.getMarginWeight());
        }
        
        // 标准化权重
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            finalInflow = finalInflow.divide(totalWeight, 2, RoundingMode.HALF_UP);
        }
        
        return IndexFlowResult.builder()
            .indexSymbol("000001.SS")
            .indexName("上证综合指数")
            .finalNetInflow(convertToUsd(finalInflow))
            .finalNetInflowCny(finalInflow)
            .source1Contribution(data.getNorthboundNetInflow())
            .source1Weight(weights.getNorthboundWeight())
            .source2Contribution(data.getRelatedEtfInflow())
            .source2Weight(weights.getEtfWeight())
            .calculationMethod("MULTI_SOURCE_FUSION")
            .geographicDimension("Asia Pacific")
            .currencyDimension("CNY")
            .marketCapDimension("Mixed Cap")
            .build();
    }
}
```

### 动态权重调整

```java
@Service
public class DynamicWeightService {
    
    public FusionWeights calculateWeights(SseIndexRawData data) {
        // 获取市场状态
        MarketState state = marketStateService.getCurrentState();
        
        FusionWeights weights = new FusionWeights();
        
        if (state.getVolatility().compareTo(new BigDecimal("0.03")) > 0) {
            // 高波动期：提高北向资金权重
            weights.setNorthboundWeight(new BigDecimal("0.50"));
            weights.setEtfWeight(new BigDecimal("0.20"));
            weights.setFuturesWeight(new BigDecimal("0.20"));
            weights.setMarginWeight(new BigDecimal("0.10"));
        } else if (MarketTrend.BULL.equals(state.getTrend())) {
            // 牛市：提高ETF权重
            weights.setNorthboundWeight(new BigDecimal("0.30"));
            weights.setEtfWeight(new BigDecimal("0.40"));
            weights.setFuturesWeight(new BigDecimal("0.20"));
            weights.setMarginWeight(new BigDecimal("0.10"));
        } else {
            // 正常市场：使用默认权重
            weights.setNorthboundWeight(new BigDecimal("0.40"));
            weights.setEtfWeight(new BigDecimal("0.25"));
            weights.setFuturesWeight(new BigDecimal("0.20"));
            weights.setMarginWeight(new BigDecimal("0.15"));
        }
        
        return weights;
    }
}
```

## API设计

### REST API

#### 获取指数流向数据
```http
GET /api/v1/index-flows/{symbol}?timeRange=1d&granularity=5m

# 响应示例
{
  "success": true,
  "data": {
    "indexSymbol": "SPY",
    "indexName": "SPDR S&P 500 ETF",
    "timeRange": "1d",
    "summary": {
      "totalNetInflow": 1250000000,
      "flowIntensity": 0.0028,
      "lastUpdated": "2024-01-17T15:30:00Z",
      "confidence": 95
    },
    "timeSeries": [
      {
        "timestamp": "2024-01-17T09:30:00Z",
        "netInflow": 320000000,
        "confidence": 95
      }
    ],
    "dataSourceBreakdown": {
      "etfFlow": {
        "contribution": 1250000000,
        "weight": 1.0,
        "reliability": 99
      }
    }
  }
}
```

#### 对比分析API
```http
POST /api/v1/index-flows/compare

# 请求体
{
  "symbols": ["SPY", "000001.SS"],
  "timeRange": "7d",
  "metrics": ["netInflow", "flowIntensity"],
  "normalization": "percentage"
}

# 响应
{
  "success": true,
  "data": {
    "comparison": [
      {
        "symbol": "SPY",
        "netInflow": 8750000000,
        "flowIntensity": 0.0194,
        "normalizedInflow": 100.0
      },
      {
        "symbol": "000001.SS", 
        "netInflow": 6450000000,
        "flowIntensity": 0.0143,
        "normalizedInflow": 73.7
      }
    ],
    "insights": [
      "SPY资金流入强度比上证指数高35%",
      "两个指数均呈现净流入态势"
    ]
  }
}
```

### WebSocket接口

```javascript
// 连接WebSocket
const ws = new WebSocket('ws://localhost:8080/ws/index-flows');

// 订阅指数更新
ws.send(JSON.stringify({
  action: 'subscribe',
  symbols: ['SPY', '000001.SS'],
  updateInterval: '5m'
}));

// 接收实时更新
ws.onmessage = function(event) {
  const update = JSON.parse(event.data);
  console.log('实时更新:', update);
  // {
  //   "symbol": "SPY",
  //   "timestamp": "2024-01-17T15:35:00Z",
  //   "netInflow": 1275000000,
  //   "change": 25000000,
  //   "confidence": 96
  // }
};
```

## 质量保证设计

### 数据验证服务

```java
@Service
public class DataValidationService {
    
    public ValidationResult validateSpyData(SpyFlowData data) {
        ValidationResult result = new ValidationResult();
        
        // 1. 数据完整性检查
        if (data.getNetInflow() == null || data.getCreationUnits() == null) {
            result.addError("SPY关键字段缺失");
        }
        
        // 2. 数据逻辑性检查
        BigDecimal calculatedInflow = calculateInflow(data);
        BigDecimal reportedInflow = data.getNetInflow();
        BigDecimal diff = calculatedInflow.subtract(reportedInflow).abs();
        BigDecimal threshold = reportedInflow.multiply(new BigDecimal("0.10"));
        
        if (diff.compareTo(threshold) > 0) {
            result.addWarning("SPY计算流入与报告流入差异超过10%");
        }
        
        // 3. 规模合理性检查
        BigDecimal maxInflow = data.getAum().multiply(new BigDecimal("0.10"));
        if (data.getNetInflow().abs().compareTo(maxInflow) > 0) {
            result.addError("SPY日流入超过AUM的10%，疑似异常");
        }
        
        return result;
    }
    
    public ValidationResult validateSseData(SseIndexRawData data) {
        ValidationResult result = new ValidationResult();
        
        // 验证数据源可用性
        int availableSources = countAvailableSources(data);
        if (availableSources < 3) {
            result.addWarning("上证指数可用数据源少于3个");
        }
        
        // 验证数据源一致性
        if (hasSignificantDivergence(data)) {
            result.addError("上证指数多源数据分歧过大，需要人工审核");
        }
        
        return result;
    }
}
```

### 异常检测服务

```java
@Service
public class AnomalyDetectionService {
    
    @EventListener
    public void handleFlowResult(IndexFlowResult result) {
        // 1. 历史对比检测
        BigDecimal historicalAvg = getHistoricalAverage(result.getIndexSymbol(), 30);
        BigDecimal currentFlow = result.getFinalNetInflow();
        
        if (isOutlier(currentFlow, historicalAvg)) {
            alertService.sendAlert(
                String.format("%s异常流入检测: 当前%s, 30日均值%s", 
                    result.getIndexName(), currentFlow, historicalAvg));
        }
        
        // 2. 跨市场一致性检测
        if ("SPY".equals(result.getIndexSymbol())) {
            checkSpyMarketConsistency(result);
        } else if ("000001.SS".equals(result.getIndexSymbol())) {
            checkSseMarketConsistency(result);
        }
    }
    
    private void checkSpyMarketConsistency(IndexFlowResult spy) {
        // 检查SPY流向与S&P500指数表现的一致性
        BigDecimal spyInflow = spy.getFinalNetInflow();
        BigDecimal sp500Return = marketDataService.getIndexReturn("^GSPC");
        
        // 大额流入但指数下跌，或大额流出但指数上涨时报警
        if ((spyInflow.compareTo(BigDecimal.valueOf(1000000000)) > 0 && sp500Return.compareTo(BigDecimal.ZERO) < 0) ||
            (spyInflow.compareTo(BigDecimal.valueOf(-1000000000)) < 0 && sp500Return.compareTo(BigDecimal.ZERO) > 0)) {
            
            alertService.sendAlert(
                String.format("SPY流向与市场表现不一致: 流入%s, S&P500涨跌%s%%", 
                    spyInflow, sp500Return.multiply(BigDecimal.valueOf(100))));
        }
    }
}
```

## 监控和告警

### 监控指标

```java
@Component
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void recordDataCollection(DataCollectionEvent event) {
        // 记录数据采集成功率
        Counter.builder("data.collection.success")
            .tag("source", event.getSource())
            .tag("symbol", event.getSymbol())
            .register(meterRegistry)
            .increment();
    }
    
    @EventListener
    public void recordCalculationLatency(CalculationEvent event) {
        // 记录计算延迟
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("calculation.latency")
            .tag("symbol", event.getSymbol())
            .register(meterRegistry));
    }
    
    @Scheduled(fixedRate = 60000) // 每分钟
    public void recordSystemHealth() {
        // 记录系统健康度
        double healthScore = calculateSystemHealth();
        Gauge.builder("system.health.score")
            .register(meterRegistry, this, MetricsCollector::getHealthScore);
    }
}
```

### 告警规则

```yaml
alerts:
  - name: "SPY数据源异常"
    condition: "spy_data_source_availability < 0.8"
    severity: "warning"
    
  - name: "上证指数计算失败"
    condition: "sse_calculation_success_rate < 0.9" 
    severity: "critical"
    
  - name: "系统整体健康度低"
    condition: "system_health_score < 0.7"
    severity: "warning"
    
  - name: "数据分歧过大"
    condition: "data_divergence_rate > 0.2"
    severity: "critical"
```

## 部署和运维

### Docker配置

```dockerfile
FROM openjdk:17-jre-slim

# 安装DuckDB
RUN apt-get update && apt-get install -y wget
RUN wget https://github.com/duckdb/duckdb/releases/download/v0.8.1/duckdb_cli-linux-amd64.zip
RUN unzip duckdb_cli-linux-amd64.zip && mv duckdb /usr/local/bin/

COPY target/cashflow-mvp.jar app.jar

ENV JAVA_OPTS="-Xmx2g -Xms1g"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

CMD ["java", "-jar", "/app.jar"]
```

### 环境配置

```yaml
# application-prod.yml
spring:
  datasource:
    duckdb:
      url: "jdbc:duckdb:/data/cashflow.db"
      
  redis:
    host: "redis"
    port: 6379
    
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 200
    scheduling:
      pool:
        size: 2
    
app:
  data-sources:
    etf-com:
      base-url: "https://api.etf.com"
      api-key: "${ETF_COM_API_KEY}"
      
    eastmoney:
      base-url: "https://api.eastmoney.com"
      
  alert:
    webhook-url: "${ALERT_WEBHOOK_URL}"
    
  cache:
    ttl: 300 # 5分钟
```

## 测试策略

### 单元测试

```java
@Test
public void testSpyFlowCalculation() {
    // Given
    SpyFlowData data = SpyFlowData.builder()
        .ticker("SPY")
        .dailyNetInflow(new BigDecimal("1250000000"))
        .creationUnits(25000)
        .redemptionUnits(8500)
        .build();
        
    // When
    IndexFlowResult result = fusionEngine.calculateSpyFlow(data);
    
    // Then
    assertThat(result.getFinalNetInflow()).isEqualTo(new BigDecimal("1250000000"));
    assertThat(result.getCalculationMethod()).isEqualTo("DIRECT_ETF");
}

@Test
public void testSseMultiSourceFusion() {
    // Given
    SseIndexRawData data = SseIndexRawData.builder()
        .northboundNetInflow(new BigDecimal("8500000000"))  // 85亿
        .relatedEtfInflow(new BigDecimal("210000000"))      // 2.1亿
        .futuresNetPosition(new BigDecimal("180000000"))    // 1.8亿
        .marginChange(new BigDecimal("120000000"))          // 1.2亿
        .build();
        
    // When
    IndexFlowResult result = fusionEngine.calculateSseFlow(data);
    
    // Then
    BigDecimal expected = new BigDecimal("8500000000").multiply(new BigDecimal("0.4"))
        .add(new BigDecimal("210000000").multiply(new BigDecimal("0.25")))
        .add(new BigDecimal("180000000").multiply(new BigDecimal("0.2")))
        .add(new BigDecimal("120000000").multiply(new BigDecimal("0.15")));
        
    assertThat(result.getFinalNetInflowCny()).isEqualTo(expected);
}
```

### 集成测试

```java
@SpringBootTest
@TestContainers
public class IndexFlowIntegrationTest {
    
    @Container
    static DuckDBContainer duckdb = new DuckDBContainer("duckdb/duckdb:v0.8.1");
    
    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    
    @Test
    public void testEndToEndFlow() {
        // 1. 模拟数据采集
        spyDataCollector.collectSpyData();
        sseDataCollector.collectSseIndexData();
        
        // 2. 验证数据存储
        await().atMost(10, TimeUnit.SECONDS).until(() -> 
            rawDataRepository.findByTickerAndDataDate("SPY", LocalDate.now()).isPresent());
            
        // 3. 验证计算结果
        await().atMost(10, TimeUnit.SECONDS).until(() ->
            resultRepository.findByIndexSymbolAndDataDate("SPY", LocalDate.now()).isPresent());
            
        // 4. 验证API响应
        MockMvcResponse response = mockMvc.perform(get("/api/v1/index-flows/SPY"))
            .andExpect(status().isOk())
            .andReturn();
            
        assertThat(response.getResponse().getContentAsString()).contains("SPY");
    }
}
```

## 性能目标

### MVP性能指标
- **数据采集延迟**: < 5分钟
- **计算处理延迟**: < 30秒  
- **API响应时间**: < 2秒
- **系统可用性**: > 99%
- **数据准确度**: SPY > 95%, 上证指数 > 85%

### 扩展性设计
- **数据库**: DuckDB支持TB级数据分析
- **消息队列**: Kafka支持高吞吐量实时处理
- **缓存**: Redis支持高并发查询
- **微服务**: 为后续扩展到42个指数预留架构空间

---

**版本**: MVP 1.0  
**创建日期**: 2025-01-17  
**目标上线**: 2025-02-28  
**负责人**: 资金流动监控系统开发团队