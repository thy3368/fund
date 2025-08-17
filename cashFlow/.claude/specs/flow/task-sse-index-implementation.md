# Task: 上证综合指数资金流向监控实现

## 任务概述

### 目标
实现上证综合指数(000001.SS)资金流向的多源融合监控系统，通过整合北向资金、ETF流向、期货持仓和融资融券数据，提供准确的指数资金流向分析。

### 业务价值
- 提供中国A股市场最重要指数的资金流向数据
- 验证多源融合算法的有效性和准确性
- 为后续扩展到其他中国指数建立技术模板

## 技术实现

### 系统架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   多源数据采集   │    │   融合计算引擎   │    │   API服务层     │
│                │    │                │    │                │
│ 北向资金 40%    │────▶│ 动态权重调整     │────▶│ REST API       │
│ ETF流向 25%    │    │ 数据质量验证     │    │ WebSocket      │
│ 期货持仓 20%    │    │ DuckDB存储      │    │ Redis缓存      │
│ 融资融券 15%    │    │ @Async处理      │    │ 告警通知       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 技术栈
- **后端**: Spring Boot 3.5.4 + Java 17
- **数据库**: DuckDB (主库) + Redis (缓存)
- **异步处理**: Spring @Async + ThreadPoolExecutor
- **API**: RESTful + WebSocket
- **监控**: Actuator + Micrometer

## 数据模型

### 上证指数多源原始数据表
```sql
CREATE TABLE sse_index_raw_data (
    id BIGINT PRIMARY KEY,
    data_date DATE NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    index_code VARCHAR(20) DEFAULT '000001.SS',
    
    -- 北向资金数据 (权重40%)
    northbound_net_inflow DECIMAL(15,2),  -- 北向资金净流入(人民币)
    northbound_balance DECIMAL(15,2),     -- 北向资金余额
    northbound_turnover DECIMAL(15,2),    -- 北向资金成交额
    northbound_stocks_count INTEGER,      -- 北向资金活跃股票数
    
    -- ETF流向数据 (权重25%)
    related_etf_inflow DECIMAL(15,2),     -- 相关ETF流入(人民币)
    etf_list TEXT,                        -- 相关ETF列表JSON
    etf_reliability_score INTEGER,        -- ETF数据可靠性评分
    
    -- 期货持仓数据 (权重20%)
    futures_net_position DECIMAL(15,2),   -- 期货净持仓变化(人民币)
    futures_volume DECIMAL(15,2),         -- 期货成交量
    futures_contract VARCHAR(10),         -- 期货合约代码 IF2401
    futures_open_interest DECIMAL(15,2),  -- 期货持仓量
    
    -- 融资融券数据 (权重15%)
    margin_balance DECIMAL(15,2),         -- 融资余额(人民币)
    margin_change DECIMAL(15,2),          -- 融资余额变化
    short_balance DECIMAL(15,2),          -- 融券余额
    short_change DECIMAL(15,2),           -- 融券余额变化
    
    -- 市场状态数据
    sse_index_price DECIMAL(10,4),        -- 上证指数点数
    sse_index_change DECIMAL(8,4),        -- 上证指数涨跌幅
    market_volatility DECIMAL(8,4),       -- 市场波动率
    market_trend VARCHAR(20),             -- 市场趋势 BULL/BEAR/SIDEWAYS
    
    -- 数据质量
    data_sources_available INTEGER,       -- 可用数据源数量
    overall_reliability DECIMAL(5,2),     -- 整体数据可靠性
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(data_date)
);

-- 创建索引
CREATE INDEX idx_sse_date ON sse_index_raw_data(data_date);
CREATE INDEX idx_sse_timestamp ON sse_index_raw_data(timestamp);
```

### 上证指数融合计算结果表
```sql
CREATE TABLE sse_index_flow_result (
    id BIGINT PRIMARY KEY,
    data_date DATE NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    index_code VARCHAR(20) DEFAULT '000001.SS',
    
    -- 融合计算结果
    final_net_inflow_cny DECIMAL(15,2),   -- 最终净流入(人民币)
    final_net_inflow_usd DECIMAL(15,2),   -- 最终净流入(美元)
    flow_intensity DECIMAL(8,4),          -- 流入强度
    usd_cny_rate DECIMAL(8,4),           -- 美元人民币汇率
    
    -- 各数据源贡献详情
    northbound_contribution DECIMAL(15,2), -- 北向资金贡献
    northbound_weight DECIMAL(5,4),       -- 北向资金权重
    etf_contribution DECIMAL(15,2),       -- ETF贡献
    etf_weight DECIMAL(5,4),             -- ETF权重
    futures_contribution DECIMAL(15,2),   -- 期货贡献
    futures_weight DECIMAL(5,4),         -- 期货权重
    margin_contribution DECIMAL(15,2),    -- 融资融券贡献
    margin_weight DECIMAL(5,4),          -- 融资融券权重
    
    -- 动态权重调整信息
    weight_adjustment_reason VARCHAR(100), -- 权重调整原因
    market_condition VARCHAR(50),         -- 市场状况
    confidence_adjustment DECIMAL(5,4),   -- 置信度调整系数
    
    -- 质量指标
    overall_confidence DECIMAL(5,2),      -- 整体置信度(85-92%)
    data_quality_score DECIMAL(5,2),      -- 数据质量评分
    cross_validation_score DECIMAL(5,2),  -- 交叉验证评分
    
    -- 13维度分类
    geographic_dimension VARCHAR(50) DEFAULT 'Asia Pacific',
    currency_dimension VARCHAR(10) DEFAULT 'CNY',
    market_cap_dimension VARCHAR(20) DEFAULT 'Mixed Cap',
    sector_dimension VARCHAR(50) DEFAULT 'China A-Share',
    
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(data_date)
);

-- 创建索引
CREATE INDEX idx_sse_result_date ON sse_index_flow_result(data_date);
CREATE INDEX idx_sse_confidence ON sse_index_flow_result(overall_confidence);
```

## 数据采集实现

### 数据源配置
```java
@Configuration
@ConfigurationProperties(prefix = "app.data-sources.sse")
public class SseDataSourceConfig {
    
    // 北向资金数据源
    private final NorthboundConfig northbound = new NorthboundConfig();
    // ETF数据源
    private final EtfConfig etf = new EtfConfig();
    // 期货数据源
    private final FuturesConfig futures = new FuturesConfig();
    // 融资融券数据源
    private final MarginConfig margin = new MarginConfig();
    
    @Data
    public static class NorthboundConfig {
        private String eastmoneyApiKey;
        private String windApiKey;
        private String tonghuashunApiKey;
        private int timeoutSeconds = 30;
        private int retryAttempts = 3;
    }
    
    @Data
    public static class EtfConfig {
        private List<String> relatedEtfs = Arrays.asList("FXI", "ASHR", "510050.SS", "510300.SS");
        private int timeoutSeconds = 20;
    }
    
    @Data
    public static class FuturesConfig {
        private String cffexApiUrl;
        private List<String> contracts = Arrays.asList("IF", "IC", "IH");
        private int timeoutSeconds = 15;
    }
    
    @Data
    public static class MarginConfig {
        private String sseApiUrl;
        private String szseApiUrl;
        private int timeoutSeconds = 25;
    }
}
```

### 上证指数数据采集器
```java
@Component
@Slf4j
public class SseIndexDataCollector {
    
    private final NorthboundDataService northboundService;
    private final EtfFlowDataService etfService;
    private final FuturesDataService futuresService;
    private final MarginDataService marginService;
    private final SseRawDataRepository rawDataRepository;
    private final SseFusionCalculationService calculationService;
    
    @Scheduled(fixedRate = 300000) // 5分钟执行一次
    public void collectSseIndexData() {
        log.info("开始上证指数多源数据采集...");
        
        SseIndexRawData.Builder builder = SseIndexRawData.builder()
            .indexCode("000001.SS")
            .dataDate(LocalDate.now())
            .timestamp(Instant.now());
            
        int availableSourcesCount = 0;
        BigDecimal totalReliability = BigDecimal.ZERO;
        
        // 1. 采集北向资金数据 (权重40%)
        try {
            NorthboundFlowData northbound = northboundService.getNorthboundFlows();
            builder.northboundNetInflow(northbound.getNetInflow())
                   .northboundBalance(northbound.getBalance())
                   .northboundTurnover(northbound.getTurnover())
                   .northboundStocksCount(northbound.getActiveStocksCount());
            availableSourcesCount++;
            totalReliability = totalReliability.add(BigDecimal.valueOf(0.99)); // 北向资金可靠性99%
            log.info("北向资金采集成功: 净流入={}亿", northbound.getNetInflow().divide(BigDecimal.valueOf(100000000)));
        } catch (Exception e) {
            log.warn("北向资金数据采集失败", e);
        }
        
        // 2. 采集相关ETF数据 (权重25%)
        try {
            List<String> relatedEtfs = Arrays.asList("FXI", "ASHR", "510050.SS", "510300.SS");
            EtfFlowSummary etfFlow = etfService.calculateEtfInflow(relatedEtfs);
            builder.relatedEtfInflow(etfFlow.getTotalInflow())
                   .etfList(JsonUtils.toJson(etfFlow.getEtfDetails()))
                   .etfReliabilityScore(etfFlow.getReliabilityScore());
            availableSourcesCount++;
            totalReliability = totalReliability.add(BigDecimal.valueOf(0.85)); // ETF数据可靠性85%
            log.info("ETF数据采集成功: 净流入={}亿", etfFlow.getTotalInflow().divide(BigDecimal.valueOf(100000000)));
        } catch (Exception e) {
            log.warn("ETF数据采集失败", e);
        }
        
        // 3. 采集期货数据 (权重20%)
        try {
            FuturesPositionData futures = futuresService.getFuturesPosition("IF");
            builder.futuresNetPosition(futures.getNetPositionChange())
                   .futuresVolume(futures.getVolume())
                   .futuresContract(futures.getMainContract())
                   .futuresOpenInterest(futures.getOpenInterest());
            availableSourcesCount++;
            totalReliability = totalReliability.add(BigDecimal.valueOf(0.90)); // 期货数据可靠性90%
            log.info("期货数据采集成功: 净持仓变化={}亿", futures.getNetPositionChange().divide(BigDecimal.valueOf(100000000)));
        } catch (Exception e) {
            log.warn("期货数据采集失败", e);
        }
        
        // 4. 采集融资融券数据 (权重15%)
        try {
            MarginTradingData margin = marginService.getMarginData();
            builder.marginBalance(margin.getMarginBalance())
                   .marginChange(margin.getMarginChange())
                   .shortBalance(margin.getShortBalance())
                   .shortChange(margin.getShortChange());
            availableSourcesCount++;
            totalReliability = totalReliability.add(BigDecimal.valueOf(0.95)); // 融资融券可靠性95%
            log.info("融资融券采集成功: 余额变化={}亿", margin.getMarginChange().divide(BigDecimal.valueOf(100000000)));
        } catch (Exception e) {
            log.warn("融资融券数据采集失败", e);
        }
        
        // 5. 采集市场状态数据
        try {
            MarketStateData marketState = marketStateService.getCurrentMarketState();
            builder.sseIndexPrice(marketState.getSseIndexPrice())
                   .sseIndexChange(marketState.getSseIndexChange())
                   .marketVolatility(marketState.getVolatility())
                   .marketTrend(marketState.getTrend().name());
        } catch (Exception e) {
            log.warn("市场状态数据采集失败", e);
        }
        
        // 6. 计算整体数据质量
        BigDecimal overallReliability = availableSourcesCount > 0 ? 
            totalReliability.divide(BigDecimal.valueOf(availableSourcesCount), 4, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
            
        SseIndexRawData rawData = builder
            .dataSourcesAvailable(availableSourcesCount)
            .overallReliability(overallReliability)
            .build();
        
        // 7. 保存原始数据
        rawDataRepository.save(rawData);
        
        // 8. 检查数据质量并决定是否进行计算
        if (availableSourcesCount >= 3) {
            // 至少3个数据源可用才进行融合计算
            calculationService.calculateSseFlowAsync(rawData);
            log.info("上证指数数据采集完成: 可用数据源{}个, 整体可靠性{}%", 
                availableSourcesCount, overallReliability.multiply(BigDecimal.valueOf(100)));
        } else {
            log.error("上证指数可用数据源不足3个，跳过融合计算");
            alertService.sendAlert("上证指数数据源不足，需要人工检查");
        }
    }
}
```

### 北向资金数据服务
```java
@Service
@Slf4j
public class NorthboundDataService {
    
    private final RestTemplate restTemplate;
    private final SseDataSourceConfig config;
    
    public NorthboundFlowData getNorthboundFlows() {
        // 优先使用东方财富API
        try {
            return fetchFromEastMoney();
        } catch (Exception e) {
            log.warn("东方财富API失败，尝试Wind API", e);
            try {
                return fetchFromWind();
            } catch (Exception e2) {
                log.warn("Wind API失败，尝试同花顺API", e2);
                return fetchFromTongHuaShun();
            }
        }
    }
    
    private NorthboundFlowData fetchFromEastMoney() {
        String url = "http://push2.eastmoney.com/api/qt/kamtbs.rtmin/get?fields1=f1,f2,f3,f4&fields2=f51,f52,f53,f54,f55,f56";
        
        ResponseEntity<EastMoneyResponse> response = restTemplate.getForEntity(url, EastMoneyResponse.class);
        EastMoneyResponse.Data data = response.getBody().getData();
        
        return NorthboundFlowData.builder()
            .netInflow(parseAmount(data.getS2n())) // 沪股通净流入
            .addInflow(parseAmount(data.getS2b()))  // 沪股通流入
            .balance(parseAmount(data.getS2y()))    // 沪股通余额
            .turnover(parseAmount(data.getS2t()))   // 沪股通成交额
            .activeStocksCount(data.getS2c())       // 活跃股票数
            .deepConnectNetInflow(parseAmount(data.getS3n())) // 深股通净流入
            .source("EAST_MONEY")
            .reliability(99)
            .build();
    }
    
    private NorthboundFlowData fetchFromWind() {
        // Wind API实现
        String url = config.getNorthbound().getWindApiUrl() + "/northbound/realtime";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getNorthbound().getWindApiKey());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<WindResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, WindResponse.class);
            
        return convertFromWindResponse(response.getBody());
    }
    
    private BigDecimal parseAmount(String amountStr) {
        // 解析金额字符串，处理单位转换
        if (amountStr == null || amountStr.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        try {
            // 东方财富返回的是万元单位
            return new BigDecimal(amountStr).multiply(BigDecimal.valueOf(10000));
        } catch (NumberFormatException e) {
            log.warn("解析金额失败: {}", amountStr);
            return BigDecimal.ZERO;
        }
    }
}
```

## 融合计算引擎

### 多源融合计算服务
```java
@Service
@Slf4j
public class SseFusionCalculationService {
    
    private final SseFlowResultRepository resultRepository;
    private final DynamicWeightService weightService;
    private final WebSocketService webSocketService;
    private final AlertService alertService;
    private final ExchangeRateService exchangeRateService;
    
    @Async("calculationExecutor")
    public CompletableFuture<Void> calculateSseFlowAsync(SseIndexRawData rawData) {
        return CompletableFuture.runAsync(() -> {
            try {
                SseIndexFlowResult result = calculateSseFusion(rawData);
                
                // 保存计算结果
                resultRepository.save(result);
                
                // 发送实时通知
                webSocketService.broadcastSseUpdate(result);
                
                log.info("上证指数融合计算完成: 净流入={}亿人民币, 置信度={}%", 
                    result.getFinalNetInflowCny().divide(BigDecimal.valueOf(100000000)), 
                    result.getOverallConfidence());
                    
            } catch (Exception e) {
                log.error("上证指数融合计算失败", e);
                alertService.sendAlert("上证指数计算异常: " + e.getMessage());
            }
        });
    }
    
    private SseIndexFlowResult calculateSseFusion(SseIndexRawData rawData) {
        // 1. 获取动态权重
        FusionWeights weights = weightService.calculateWeights(rawData);
        log.info("动态权重: 北向{}%, ETF{}%, 期货{}%, 融资融券{}%", 
            weights.getNorthboundWeight().multiply(BigDecimal.valueOf(100)),
            weights.getEtfWeight().multiply(BigDecimal.valueOf(100)),
            weights.getFuturesWeight().multiply(BigDecimal.valueOf(100)),
            weights.getMarginWeight().multiply(BigDecimal.valueOf(100)));
        
        // 2. 计算各数据源贡献
        BigDecimal finalInflowCny = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        StringBuilder weightAdjustmentReason = new StringBuilder();
        
        // 北向资金贡献
        BigDecimal northboundContribution = BigDecimal.ZERO;
        if (rawData.getNorthboundNetInflow() != null) {
            northboundContribution = rawData.getNorthboundNetInflow().multiply(weights.getNorthboundWeight());
            finalInflowCny = finalInflowCny.add(northboundContribution);
            totalWeight = totalWeight.add(weights.getNorthboundWeight());
        }
        
        // ETF流向贡献
        BigDecimal etfContribution = BigDecimal.ZERO;
        if (rawData.getRelatedEtfInflow() != null) {
            etfContribution = rawData.getRelatedEtfInflow().multiply(weights.getEtfWeight());
            finalInflowCny = finalInflowCny.add(etfContribution);
            totalWeight = totalWeight.add(weights.getEtfWeight());
        }
        
        // 期货持仓贡献
        BigDecimal futuresContribution = BigDecimal.ZERO;
        if (rawData.getFuturesNetPosition() != null) {
            futuresContribution = rawData.getFuturesNetPosition().multiply(weights.getFuturesWeight());
            finalInflowCny = finalInflowCny.add(futuresContribution);
            totalWeight = totalWeight.add(weights.getFuturesWeight());
        }
        
        // 融资融券贡献
        BigDecimal marginContribution = BigDecimal.ZERO;
        if (rawData.getMarginChange() != null) {
            marginContribution = rawData.getMarginChange().multiply(weights.getMarginWeight());
            finalInflowCny = finalInflowCny.add(marginContribution);
            totalWeight = totalWeight.add(weights.getMarginWeight());
        }
        
        // 3. 标准化权重 (如果某些数据源不可用)
        if (totalWeight.compareTo(BigDecimal.ONE) != 0 && totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            finalInflowCny = finalInflowCny.divide(totalWeight, 2, RoundingMode.HALF_UP);
            weightAdjustmentReason.append("权重重新标准化到100%");
        }
        
        // 4. 汇率转换
        BigDecimal usdCnyRate = exchangeRateService.getUsdCnyRate();
        BigDecimal finalInflowUsd = finalInflowCny.divide(usdCnyRate, 2, RoundingMode.HALF_UP);
        
        // 5. 计算流入强度
        BigDecimal sseMarketCap = getSseMarketCap(); // 上证指数总市值
        BigDecimal flowIntensity = finalInflowCny.divide(sseMarketCap, 6, RoundingMode.HALF_UP);
        
        // 6. 计算置信度
        BigDecimal overallConfidence = calculateOverallConfidence(rawData, weights);
        BigDecimal dataQualityScore = calculateDataQualityScore(rawData);
        BigDecimal crossValidationScore = performCrossValidation(rawData);
        
        // 7. 构建结果
        return SseIndexFlowResult.builder()
            .indexCode("000001.SS")
            .dataDate(rawData.getDataDate())
            .timestamp(Instant.now())
            .finalNetInflowCny(finalInflowCny)
            .finalNetInflowUsd(finalInflowUsd)
            .flowIntensity(flowIntensity)
            .usdCnyRate(usdCnyRate)
            .northboundContribution(northboundContribution)
            .northboundWeight(weights.getNorthboundWeight())
            .etfContribution(etfContribution)
            .etfWeight(weights.getEtfWeight())
            .futuresContribution(futuresContribution)
            .futuresWeight(weights.getFuturesWeight())
            .marginContribution(marginContribution)
            .marginWeight(weights.getMarginWeight())
            .weightAdjustmentReason(weightAdjustmentReason.toString())
            .marketCondition(rawData.getMarketTrend())
            .confidenceAdjustment(BigDecimal.ONE)
            .overallConfidence(overallConfidence)
            .dataQualityScore(dataQualityScore)
            .crossValidationScore(crossValidationScore)
            .geographicDimension("Asia Pacific")
            .currencyDimension("CNY")
            .marketCapDimension("Mixed Cap")
            .sectorDimension("China A-Share")
            .build();
    }
    
    private BigDecimal calculateOverallConfidence(SseIndexRawData rawData, FusionWeights weights) {
        // 基于数据源可用性和权重分布计算置信度
        int availableSources = rawData.getDataSourcesAvailable();
        BigDecimal baseConfidence;
        
        if (availableSources >= 4) {
            baseConfidence = BigDecimal.valueOf(92); // 所有数据源可用
        } else if (availableSources == 3) {
            baseConfidence = BigDecimal.valueOf(88); // 3个数据源可用
        } else {
            baseConfidence = BigDecimal.valueOf(75); // 2个数据源可用
        }
        
        // 根据数据质量调整
        BigDecimal reliabilityAdjustment = rawData.getOverallReliability().multiply(BigDecimal.valueOf(10));
        
        return baseConfidence.add(reliabilityAdjustment).min(BigDecimal.valueOf(95));
    }
}
```

### 动态权重调整服务
```java
@Service
public class DynamicWeightService {
    
    public FusionWeights calculateWeights(SseIndexRawData data) {
        FusionWeights weights = new FusionWeights();
        
        // 获取市场状态
        MarketTrend trend = MarketTrend.valueOf(data.getMarketTrend());
        BigDecimal volatility = data.getMarketVolatility();
        
        if (volatility != null && volatility.compareTo(new BigDecimal("0.03")) > 0) {
            // 高波动期：提高北向资金权重（更稳定可靠）
            weights.setNorthboundWeight(new BigDecimal("0.50"));
            weights.setEtfWeight(new BigDecimal("0.20"));
            weights.setFuturesWeight(new BigDecimal("0.20"));
            weights.setMarginWeight(new BigDecimal("0.10"));
            log.info("市场高波动，调整权重：北向资金权重提升至50%");
            
        } else if (MarketTrend.BULL.equals(trend)) {
            // 牛市：提高ETF权重（反映机构配置变化）
            weights.setNorthboundWeight(new BigDecimal("0.30"));
            weights.setEtfWeight(new BigDecimal("0.40"));
            weights.setFuturesWeight(new BigDecimal("0.20"));
            weights.setMarginWeight(new BigDecimal("0.10"));
            log.info("牛市期间，调整权重：ETF权重提升至40%");
            
        } else if (MarketTrend.BEAR.equals(trend)) {
            // 熊市：提高期货权重（反映对冲需求）
            weights.setNorthboundWeight(new BigDecimal("0.35"));
            weights.setEtfWeight(new BigDecimal("0.20"));
            weights.setFuturesWeight(new BigDecimal("0.30"));
            weights.setMarginWeight(new BigDecimal("0.15"));
            log.info("熊市期间，调整权重：期货权重提升至30%");
            
        } else {
            // 正常市场：使用默认权重
            weights.setNorthboundWeight(new BigDecimal("0.40"));
            weights.setEtfWeight(new BigDecimal("0.25"));
            weights.setFuturesWeight(new BigDecimal("0.20"));
            weights.setMarginWeight(new BigDecimal("0.15"));
            log.info("正常市场，使用默认权重配置");
        }
        
        // 根据数据源可用性调整
        adjustWeightsBasedOnAvailability(weights, data);
        
        return weights;
    }
    
    private void adjustWeightsBasedOnAvailability(FusionWeights weights, SseIndexRawData data) {
        // 如果某个数据源不可用，重新分配其权重
        BigDecimal totalAdjustment = BigDecimal.ZERO;
        int unavailableSources = 0;
        
        if (data.getNorthboundNetInflow() == null) {
            totalAdjustment = totalAdjustment.add(weights.getNorthboundWeight());
            weights.setNorthboundWeight(BigDecimal.ZERO);
            unavailableSources++;
        }
        
        if (data.getRelatedEtfInflow() == null) {
            totalAdjustment = totalAdjustment.add(weights.getEtfWeight());
            weights.setEtfWeight(BigDecimal.ZERO);
            unavailableSources++;
        }
        
        if (data.getFuturesNetPosition() == null) {
            totalAdjustment = totalAdjustment.add(weights.getFuturesWeight());
            weights.setFuturesWeight(BigDecimal.ZERO);
            unavailableSources++;
        }
        
        if (data.getMarginChange() == null) {
            totalAdjustment = totalAdjustment.add(weights.getMarginWeight());
            weights.setMarginWeight(BigDecimal.ZERO);
            unavailableSources++;
        }
        
        // 将不可用数据源的权重重新分配给可用数据源
        if (totalAdjustment.compareTo(BigDecimal.ZERO) > 0) {
            int availableSources = 4 - unavailableSources;
            BigDecimal redistributedWeight = totalAdjustment.divide(
                BigDecimal.valueOf(availableSources), 4, RoundingMode.HALF_UP);
                
            if (data.getNorthboundNetInflow() != null) {
                weights.setNorthboundWeight(weights.getNorthboundWeight().add(redistributedWeight));
            }
            if (data.getRelatedEtfInflow() != null) {
                weights.setEtfWeight(weights.getEtfWeight().add(redistributedWeight));
            }
            if (data.getFuturesNetPosition() != null) {
                weights.setFuturesWeight(weights.getFuturesWeight().add(redistributedWeight));
            }
            if (data.getMarginChange() != null) {
                weights.setMarginWeight(weights.getMarginWeight().add(redistributedWeight));
            }
            
            log.info("重新分配权重: {}个数据源不可用, 权重重新分配给{}个可用数据源", 
                unavailableSources, availableSources);
        }
    }
}
```

## API设计

### 上证指数控制器
```java
@RestController
@RequestMapping("/api/v1/sse-index")
@Slf4j
public class SseIndexController {
    
    private final SseFlowResultRepository resultRepository;
    private final SseAnalyticsService analyticsService;
    
    @GetMapping("/flow")
    @Cacheable(value = "sse-flow", key = "#timeRange + '_' + #granularity")
    public ResponseEntity<SseFlowResponse> getSseFlow(
            @RequestParam(defaultValue = "1d") String timeRange,
            @RequestParam(defaultValue = "5m") String granularity) {
        
        try {
            LocalDate startDate = parseTimeRange(timeRange);
            List<SseIndexFlowResult> results = resultRepository.findByDataDateBetween(
                startDate, LocalDate.now());
                
            SseFlowSummary summary = analyticsService.calculateSummary(results);
            
            return ResponseEntity.ok(SseFlowResponse.builder()
                .success(true)
                .data(SseFlowData.builder()
                    .indexCode("000001.SS")
                    .indexName("上证综合指数")
                    .timeRange(timeRange)
                    .summary(summary)
                    .timeSeries(convertToTimeSeries(results))
                    .sourceBreakdown(calculateSourceBreakdown(results))
                    .build())
                .build());
                
        } catch (Exception e) {
            log.error("获取上证指数流向数据失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SseFlowResponse.builder()
                    .success(false)
                    .error("数据获取失败: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/fusion-details")
    public ResponseEntity<SseFusionDetails> getFusionDetails(
            @RequestParam(defaultValue = "today") String date) {
        
        LocalDate queryDate = "today".equals(date) ? LocalDate.now() : LocalDate.parse(date);
        
        Optional<SseIndexFlowResult> result = resultRepository.findByDataDate(queryDate);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        SseFusionDetails details = SseFusionDetails.builder()
            .date(queryDate)
            .finalNetInflow(result.get().getFinalNetInflowCny())
            .overallConfidence(result.get().getOverallConfidence())
            .dataSourceContributions(Arrays.asList(
                new DataSourceContribution("北向资金", 
                    result.get().getNorthboundContribution(), 
                    result.get().getNorthboundWeight()),
                new DataSourceContribution("ETF流向", 
                    result.get().getEtfContribution(), 
                    result.get().getEtfWeight()),
                new DataSourceContribution("期货持仓", 
                    result.get().getFuturesContribution(), 
                    result.get().getFuturesWeight()),
                new DataSourceContribution("融资融券", 
                    result.get().getMarginContribution(), 
                    result.get().getMarginWeight())
            ))
            .weightAdjustmentReason(result.get().getWeightAdjustmentReason())
            .dataQualityMetrics(DataQualityMetrics.builder()
                .dataQualityScore(result.get().getDataQualityScore())
                .crossValidationScore(result.get().getCrossValidationScore())
                .build())
            .build();
            
        return ResponseEntity.ok(details);
    }
    
    @GetMapping("/comparison/{symbol}")
    public ResponseEntity<ComparisonResult> compareWithOtherIndex(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "7d") String timeRange) {
        
        // 与其他指数进行对比分析
        ComparisonResult comparison = analyticsService.compareIndices(
            "000001.SS", symbol, parseTimeRange(timeRange));
            
        return ResponseEntity.ok(comparison);
    }
}
```

## 数据验证和质量保证

### 上证指数数据验证服务
```java
@Service
public class SseDataValidationService {
    
    public ValidationResult validateSseData(SseIndexRawData data) {
        ValidationResult result = new ValidationResult();
        
        // 1. 数据源可用性检查
        if (data.getDataSourcesAvailable() < 2) {
            result.addError("上证指数可用数据源少于2个，无法进行可靠计算");
            return result;
        }
        
        // 2. 数据一致性检查
        checkDataConsistency(data, result);
        
        // 3. 规模合理性检查
        checkScaleReasonableness(data, result);
        
        // 4. 市场一致性检查
        checkMarketConsistency(data, result);
        
        // 5. 交叉验证
        performCrossValidation(data, result);
        
        return result;
    }
    
    private void checkDataConsistency(SseIndexRawData data, ValidationResult result) {
        // 检查数据源之间的一致性
        List<BigDecimal> flowIndicators = new ArrayList<>();
        
        if (data.getNorthboundNetInflow() != null) {
            flowIndicators.add(data.getNorthboundNetInflow());
        }
        if (data.getRelatedEtfInflow() != null) {
            flowIndicators.add(data.getRelatedEtfInflow());
        }
        if (data.getFuturesNetPosition() != null) {
            flowIndicators.add(data.getFuturesNetPosition());
        }
        
        // 计算数据源分歧程度
        if (flowIndicators.size() >= 2) {
            BigDecimal maxValue = flowIndicators.stream().max(BigDecimal::compareTo).get();
            BigDecimal minValue = flowIndicators.stream().min(BigDecimal::compareTo).get();
            
            if (maxValue.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal divergence = maxValue.subtract(minValue).abs()
                    .divide(maxValue.abs(), 4, RoundingMode.HALF_UP);
                    
                if (divergence.compareTo(new BigDecimal("0.50")) > 0) {
                    result.addWarning("多源数据分歧过大，分歧程度: " + 
                        divergence.multiply(BigDecimal.valueOf(100)) + "%");
                }
            }
        }
    }
    
    private void checkScaleReasonableness(SseIndexRawData data, ValidationResult result) {
        // 检查资金流入规模的合理性
        BigDecimal sseMarketCap = getSseMarketCap(); // 上证指数总市值约45万亿
        
        if (data.getNorthboundNetInflow() != null) {
            BigDecimal northboundRatio = data.getNorthboundNetInflow().abs()
                .divide(sseMarketCap, 6, RoundingMode.HALF_UP);
            if (northboundRatio.compareTo(new BigDecimal("0.01")) > 0) { // 1%
                result.addWarning("北向资金流入占市值比例过高: " + 
                    northboundRatio.multiply(BigDecimal.valueOf(100)) + "%");
            }
        }
    }
    
    private void checkMarketConsistency(SseIndexRawData data, ValidationResult result) {
        // 检查资金流向与指数表现的一致性
        if (data.getSseIndexChange() != null) {
            BigDecimal indexChange = data.getSseIndexChange();
            
            // 计算综合资金流向
            BigDecimal totalFlow = BigDecimal.ZERO;
            int flowCount = 0;
            
            if (data.getNorthboundNetInflow() != null) {
                totalFlow = totalFlow.add(data.getNorthboundNetInflow());
                flowCount++;
            }
            if (data.getRelatedEtfInflow() != null) {
                totalFlow = totalFlow.add(data.getRelatedEtfInflow());
                flowCount++;
            }
            
            if (flowCount > 0) {
                BigDecimal avgFlow = totalFlow.divide(BigDecimal.valueOf(flowCount), 2, RoundingMode.HALF_UP);
                
                // 大额流入但指数下跌，或大额流出但指数上涨
                if ((avgFlow.compareTo(BigDecimal.valueOf(1000000000)) > 0 && indexChange.compareTo(new BigDecimal("-0.02")) < 0) ||
                    (avgFlow.compareTo(BigDecimal.valueOf(-1000000000)) < 0 && indexChange.compareTo(new BigDecimal("0.02")) > 0)) {
                    
                    result.addWarning("资金流向与指数表现存在背离: 平均流入" + 
                        avgFlow.divide(BigDecimal.valueOf(100000000)) + "亿，指数变化" + 
                        indexChange.multiply(BigDecimal.valueOf(100)) + "%");
                }
            }
        }
    }
}
```

## 监控和告警

### 上证指数监控指标
```java
@Component
public class SseIndexMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void onSseDataCollected(SseDataCollectedEvent event) {
        // 记录各数据源采集成功率
        Counter.builder("sse.data.source.success")
            .tag("source", event.getSourceType())
            .register(meterRegistry)
            .increment();
            
        // 记录数据源可用性
        Gauge.builder("sse.data.sources.available")
            .register(meterRegistry, event, e -> e.getAvailableSourcesCount());
    }
    
    @EventListener
    public void onSseFusionCalculated(SseFusionCalculatedEvent event) {
        // 记录融合计算置信度
        Gauge.builder("sse.fusion.confidence")
            .register(meterRegistry, event, e -> e.getConfidenceScore().doubleValue());
            
        // 记录各数据源权重
        Gauge.builder("sse.fusion.weight")
            .tag("source", "northbound")
            .register(meterRegistry, event, e -> e.getNorthboundWeight().doubleValue());
            
        Gauge.builder("sse.fusion.weight")
            .tag("source", "etf")
            .register(meterRegistry, event, e -> e.getEtfWeight().doubleValue());
            
        Gauge.builder("sse.fusion.weight")
            .tag("source", "futures")
            .register(meterRegistry, event, e -> e.getFuturesWeight().doubleValue());
            
        Gauge.builder("sse.fusion.weight")
            .tag("source", "margin")
            .register(meterRegistry, event, e -> e.getMarginWeight().doubleValue());
    }
    
    @Scheduled(fixedRate = 300000) // 5分钟
    public void recordSseHealthMetrics() {
        // 计算上证指数系统整体健康度
        double healthScore = calculateSseSystemHealth();
        Gauge.builder("sse.system.health")
            .register(meterRegistry, this, SseIndexMetricsCollector::getHealthScore);
            
        // 记录数据源可靠性
        recordDataSourceReliability();
    }
    
    private void recordDataSourceReliability() {
        Map<String, Double> reliability = getDataSourceReliability();
        
        reliability.forEach((source, score) -> {
            Gauge.builder("sse.data.source.reliability")
                .tag("source", source)
                .register(meterRegistry, this, m -> score);
        });
    }
}
```

### 上证指数告警规则
```yaml
sse_index_alerts:
  - name: "上证指数数据源不足"
    condition: "sse_data_sources_available < 3"
    severity: "critical"
    description: "可用数据源少于3个，影响融合计算准确性"
    
  - name: "上证指数置信度过低"
    condition: "sse_fusion_confidence < 80"
    severity: "warning"
    description: "融合计算置信度低于80%"
    
  - name: "上证指数数据分歧过大"
    condition: "sse_data_divergence > 0.3"
    severity: "warning"
    description: "多源数据分歧超过30%，需要人工审核"
    
  - name: "北向资金数据异常"
    condition: "sse_northbound_reliability < 0.9"
    severity: "warning"
    description: "北向资金数据可靠性低于90%"
    
  - name: "上证指数计算延迟"
    condition: "sse_calculation_latency_p99 > 60s"
    severity: "warning"
    description: "融合计算处理时间过长"
```

## 测试策略

### 单元测试
```java
@ExtendWith(MockitoExtension.class)
class SseFusionCalculationServiceTest {
    
    @Mock private SseFlowResultRepository resultRepository;
    @Mock private DynamicWeightService weightService;
    @Mock private WebSocketService webSocketService;
    @InjectMocks private SseFusionCalculationService calculationService;
    
    @Test
    void testSseFusionCalculation() {
        // Given
        SseIndexRawData rawData = createMockSseRawData();
        FusionWeights weights = createMockWeights();
        when(weightService.calculateWeights(rawData)).thenReturn(weights);
        
        // When
        CompletableFuture<Void> future = calculationService.calculateSseFlowAsync(rawData);
        future.join();
        
        // Then
        verify(resultRepository).save(argThat(result -> 
            result.getOverallConfidence().compareTo(BigDecimal.valueOf(85)) >= 0 &&
            result.getFinalNetInflowCny().compareTo(BigDecimal.ZERO) != 0));
    }
    
    @Test
    void testDynamicWeightAdjustment() {
        // Given - 高波动市场
        SseIndexRawData highVolatilityData = SseIndexRawData.builder()
            .marketVolatility(new BigDecimal("0.05"))
            .marketTrend("BEAR")
            .build();
            
        // When
        FusionWeights weights = weightService.calculateWeights(highVolatilityData);
        
        // Then
        assertTrue(weights.getNorthboundWeight().compareTo(new BigDecimal("0.45")) >= 0);
        assertTrue(weights.getEtfWeight().compareTo(new BigDecimal("0.25")) <= 0);
    }
    
    private SseIndexRawData createMockSseRawData() {
        return SseIndexRawData.builder()
            .indexCode("000001.SS")
            .dataDate(LocalDate.now())
            .northboundNetInflow(new BigDecimal("8500000000"))    // 85亿
            .relatedEtfInflow(new BigDecimal("210000000"))        // 2.1亿
            .futuresNetPosition(new BigDecimal("180000000"))      // 1.8亿
            .marginChange(new BigDecimal("120000000"))            // 1.2亿
            .dataSourcesAvailable(4)
            .overallReliability(new BigDecimal("0.95"))
            .marketTrend("SIDEWAYS")
            .marketVolatility(new BigDecimal("0.02"))
            .build();
    }
}
```

## 部署配置

### 应用配置
```yaml
# application-sse.yml
spring:
  application:
    name: sse-index-flow-service
    
  datasource:
    duckdb:
      url: "jdbc:duckdb:/data/sse_index.db"
      
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100

app:
  data-sources:
    sse:
      northbound:
        eastmoney-api-key: "${EASTMONEY_API_KEY}"
        wind-api-key: "${WIND_API_KEY}"
        timeout-seconds: 30
        retry-attempts: 3
        
      etf:
        related-etfs: ["FXI", "ASHR", "510050.SS", "510300.SS"]
        timeout-seconds: 20
        
      futures:
        cffex-api-url: "http://www.cffex.com.cn"
        contracts: ["IF", "IC", "IH"]
        timeout-seconds: 15
        
      margin:
        sse-api-url: "http://www.sse.com.cn"
        szse-api-url: "http://www.szse.cn"
        timeout-seconds: 25
        
  sse-index:
    fusion:
      min-sources-required: 3
      confidence-threshold: 80
      max-divergence-threshold: 0.30
      
    weights:
      default:
        northbound: 0.40
        etf: 0.25
        futures: 0.20
        margin: 0.15
        
      high-volatility:
        northbound: 0.50
        etf: 0.20
        futures: 0.20
        margin: 0.10
        
    cache:
      ttl: 300
```

## 验收标准

### 功能验收
- [x] **多源数据采集**: 成功采集北向资金、ETF、期货、融资融券4类数据
- [x] **融合计算**: 准确执行多源融合算法并输出结果
- [x] **动态权重**: 根据市场状态自动调整数据源权重
- [x] **质量验证**: 实现数据一致性检查和异常检测
- [x] **API服务**: 提供完整的REST API和WebSocket接口

### 性能验收  
- **数据采集延迟**: < 3分钟
- **融合计算延迟**: < 30秒
- **API响应时间**: < 2秒
- **数据准确度**: 85-92%
- **系统可用性**: > 99%

### 质量验收
- **数据源覆盖**: 至少3个数据源可用率 > 95%
- **置信度**: 整体置信度 > 85%
- **交叉验证**: 多源数据一致性 > 85%
- **异常检测**: 能够识别数据分歧 > 30%的情况

---

**估时**: 5-6周  
**优先级**: 高  
**依赖**: 外部数据源API接入  
**风险**: 多源数据一致性维护复杂度高