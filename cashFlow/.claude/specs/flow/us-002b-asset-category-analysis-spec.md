# US-002B: 资产类别维度分析 - 独立规格说明书

## 概述

### 业务背景
资产类别维度分析是全球资金流动监控系统的核心功能之一，旨在提供全面的资产类别资金流向洞察，帮助投资经理了解不同资产类别的资金配置变化和市场偏好。

### 项目目标
- 建立完整的资产类别资金流动分析体系
- 支持9大主要资产类别的深度分析
- 提供实时和历史资金流向数据
- 实现多维度交叉分析和对比功能
- 构建智能预警和异常检测机制

## 用户故事层次结构

### 父故事
**US-002B: 资产类别维度分析**

**作为** 投资经理  
**我希望** 能够按资产类别查看资金流向（股票、债券、外汇、大宗商品等主要类别）  
**以便于** 了解不同资产类别的资金配置变化和市场偏好

### 子故事拆分

#### US-002B-1: 股票类别资金流向分析 ⭐
**作为** 投资经理  
**我希望** 查看股票类别的资金流向数据  
**以便于** 了解全球股市的资金配置情况

**子故事:**
- US-002B-1A: 股票数据采集
- US-002B-1B: 股票数据加工  
- US-002B-1C: 股票资金流向数据查询

##### US-002B-1A: 股票数据采集
**作为** 系统管理员  
**我希望** 系统能够从多个数据源自动采集股票原始数据  
**以便于** 为后续数据加工提供充足的原始数据基础

**验收标准:**
- WHEN 系统启动时 THEN 应成功连接至少2个外部数据源（东方财富、Alpha Vantage等）
- WHEN 数据采集运行时 THEN 系统应每5分钟从各数据源获取最新股票数据
- WHEN 接收到数据源响应时 THEN 系统应将原始数据暂存到数据缓冲区
- WHEN 数据源不可用时 THEN 系统应自动切换到备用数据源并记录告警
- WHEN 采集完成时 THEN 系统应记录采集统计信息（成功率、延迟、数据量）

**功能要求:**
- 支持多数据源并行采集
- 实现数据源健康监控和自动切换
- 提供采集进度和状态监控
- 支持断点续传和失败重试
- 记录详细的采集日志

##### US-002B-1B: 股票数据加工
**作为** 系统管理员  
**我希望** 系统能够智能加工原始股票数据  
**以便于** 生成高质量的资金流分析数据

**验收标准:**
- WHEN 接收到原始数据时 THEN 系统应进行数据清洗和格式标准化
- WHEN 数据不完整时 THEN 系统应从股票元数据库补充缺失信息
- WHEN 处理OHLCV数据时 THEN 系统应根据Plan A算法计算净资金流入/流出
- WHEN 处理资金流数据时 THEN 系统应直接使用Plan B的现成流入/流出数据
- WHEN 加工完成时 THEN 系统应进行13维度智能分类并评估数据质量

**功能要求:**
- 实现Plan A和Plan B两套数据处理算法
- 支持13维度自动分类（地理、货币、市值、风格等）
- 提供数据质量评分和异常检测
- 实现数据去重和一致性检查
- 支持批量和流式数据处理

##### US-002B-1C: 股票资金流向数据查询
**作为** 投资经理  
**我希望** 能够查询和分析股票资金流向数据  
**以便于** 做出投资决策和了解市场趋势

**验收标准:**
- WHEN 用户选择股票类别 THEN 系统应显示全球主要股市的净流入/流出总额，响应时间<2秒
- WHEN 用户查看股票细分 THEN 系统应提供按市值分类（大盘股、中小盘股）的资金流向数据
- WHEN 用户查看风格分类 THEN 系统应显示成长股vs价值股的资金流向对比分析
- WHEN 用户查看行业分布 THEN 系统应显示主要行业板块的资金流入流出情况和排名
- WHEN 用户请求实时更新 THEN 前端应通过WebSocket接收最新数据并自动刷新界面

**功能要求:**
- 提供REST API接口支持各种查询条件
- 实现数据缓存提高查询性能
- 支持WebSocket实时数据推送
- 提供友好的前端界面展示分析结果
- 支持数据导出功能（CSV、Excel格式）

#### US-002B-2: 债券类别资金流向分析
**作为** 投资经理  
**我希望** 查看债券类别的资金流向数据  
**以便于** 了解固定收益市场的资金配置变化

**子故事:**
- US-002B-2A: 债券数据采集
- US-002B-2B: 债券数据加工
- US-002B-2C: 债券资金流向数据查询

#### US-002B-3: 外汇类别资金流向分析
**作为** 投资经理  
**我希望** 查看外汇市场的资金流向数据  
**以便于** 了解不同货币的资金配置趋势

**子故事:**
- US-002B-3A: 外汇数据采集
- US-002B-3B: 外汇数据加工
- US-002B-3C: 外汇资金流向数据查询

#### US-002B-4: 大宗商品类别资金流向分析
**作为** 投资经理  
**我希望** 查看大宗商品的资金流向数据  
**以便于** 了解商品市场的投资趋势

**子故事:**
- US-002B-4A: 大宗商品数据采集
- US-002B-4B: 大宗商品数据加工
- US-002B-4C: 大宗商品资金流向数据查询

#### US-002B-5: 房地产类别资金流向分析
**作为** 投资经理  
**我希望** 查看房地产投资的资金流向数据  
**以便于** 了解不动产市场的资金配置

**子故事:**
- US-002B-5A: 房地产数据采集 (REITs等)
- US-002B-5B: 房地产数据加工
- US-002B-5C: 房地产资金流向数据查询

#### US-002B-6: 加密货币类别资金流向分析
**作为** 投资经理  
**我希望** 查看加密货币的资金流向数据  
**以便于** 了解数字资产市场的资金流动

**子故事:**
- US-002B-6A: 加密货币数据采集
- US-002B-6B: 加密货币数据加工
- US-002B-6C: 加密货币资金流向数据查询

#### US-002B-7: 现金类资产流向分析
**作为** 投资经理  
**我希望** 查看现金类资产的资金流向数据  
**以便于** 了解流动性偏好变化

**子故事:**
- US-002B-7A: 现金类资产数据采集 (货币市场基金等)
- US-002B-7B: 现金类资产数据加工
- US-002B-7C: 现金类资产资金流向数据查询

#### US-002B-8: 另类投资类别资金流向分析
**作为** 投资经理  
**我希望** 查看另类投资的资金流向数据  
**以便于** 了解非传统投资的资金配置

**子故事:**
- US-002B-8A: 另类投资数据采集 (私募股权、对冲基金等)
- US-002B-8B: 另类投资数据加工
- US-002B-8C: 另类投资资金流向数据查询

#### US-002B-9: 现货贸易类别资金流向分析
**作为** 投资经理  
**我希望** 查看现货贸易的资金流向数据  
**以便于** 了解实体贸易的资金流动

**子故事:**
- US-002B-9A: 现货贸易数据采集
- US-002B-9B: 现货贸易数据加工
- US-002B-9C: 现货贸易资金流向数据查询

## 验收标准

### 功能验收标准

#### US-002B-1: 股票类别分析
**WHEN** 用户选择股票类别 **THEN** 系统应显示全球主要股市的净流入/流出总额，响应时间<2秒
**WHEN** 用户查看股票细分 **THEN** 系统应提供按市值分类（大盘股、中小盘股）的资金流向数据
**WHEN** 用户查看风格分类 **THEN** 系统应显示成长股vs价值股的资金流向对比分析
**WHEN** 用户查看行业分布 **THEN** 系统应显示主要行业板块的资金流入流出情况和排名
**IF** 某个细分类别无数据 **THEN** 系统应显示"暂无数据"提示并建议查看总体股市数据

#### US-002B-2: 债券类别分析
**WHEN** 用户选择债券类别 **THEN** 系统应显示债券市场的总体净流入/流出数据
**WHEN** 用户查看政府债券 **THEN** 系统应显示国债、地方债的资金流向情况
**WHEN** 用户查看企业债券 **THEN** 系统应显示投资级和高收益债券的资金流向对比
**WHEN** 用户查看期限结构 **THEN** 系统应按短期（<2年）、中期（2-10年）、长期（>10年）显示资金分布
**IF** 某类债券数据不足 **THEN** 系统应显示提示信息并建议查看债券总体数据

#### US-002B-3: 外汇类别分析
**WHEN** 用户选择外汇类别 **THEN** 系统应显示主要货币的净流入/流出统计
**WHEN** 用户查看G10货币 **THEN** 系统应显示美元、欧元、日元、英镑等主要货币的资金流向
**WHEN** 用户查看新兴市场货币 **THEN** 系统应显示人民币、印度卢比等新兴市场货币的资金流向
**WHEN** 用户查看货币对 **THEN** 系统应显示主要货币对（如EUR/USD、USD/JPY）的交易资金流向
**IF** 某货币数据缺失 **THEN** 系统应显示可用货币列表并提示数据更新时间

#### US-002B-4: 大宗商品类别分析
**WHEN** 用户选择大宗商品类别 **THEN** 系统应显示商品市场的总体资金流向
**WHEN** 用户查看贵金属 **THEN** 系统应显示黄金、白银、铂金的资金流入流出情况
**WHEN** 用户查看能源商品 **THEN** 系统应显示原油、天然气的资金流向数据
**WHEN** 用户查看农产品 **THEN** 系统应显示小麦、大豆、玉米等农产品的资金流向
**WHEN** 用户查看工业金属 **THEN** 系统应显示铜、铝、锌等工业金属的资金配置情况
**IF** 某商品类别数据不足 **THEN** 系统应显示替代指标建议（如相关ETF数据）

#### US-002B-5至US-002B-9: 其他资产类别
**WHEN** 用户选择任何资产类别 **THEN** 系统应显示该类别的总体资金流向统计
**WHEN** 用户查看细分类别 **THEN** 系统应提供更详细的子类别分析
**IF** 某类资产数据不足 **THEN** 系统应显示"数据收集中"提示并提供更新时间预期

### 性能验收标准
- API响应时间 < 2秒
- 支持并发查询 > 100 QPS
- 数据更新延迟 < 5分钟
- 系统可用性 > 99.5%

### 质量验收标准
- 数据准确率 > 99%
- 异常数据检测率 > 95%
- 用户界面易用性评分 > 4.0/5.0

## 技术实现

### 数据源配置

#### 按资产类别分组的数据源
```yaml
asset-categories:
  stock:
    primary-sources: ["eastmoney", "tonghuashun", "polygon"]
    fallback-sources: ["alpha-vantage", "yahoo-finance"]
    markets: ["NYSE", "NASDAQ", "SSE", "SZSE", "HKEX", "TSE"]
    
  bond:
    primary-sources: ["bloomberg-bond", "tradeweb"]
    fallback-sources: ["fred-api", "treasury-direct"]
    markets: ["US_TREASURY", "CORPORATE_BOND", "MUNICIPAL_BOND"]
    
  forex:
    primary-sources: ["oanda", "fxcm", "reuters-fx"]
    fallback-sources: ["yahoo-fx", "exchangerates-api"]
    markets: ["SPOT_FX", "FX_FUTURES", "FX_OPTIONS"]
    
  commodity:
    primary-sources: ["cme-group", "ice-futures", "lme"]
    fallback-sources: ["yahoo-commodity", "investing-com"]
    markets: ["PRECIOUS_METALS", "ENERGY", "AGRICULTURE", "INDUSTRIAL_METALS"]
    
  real-estate:
    primary-sources: ["reit-data", "real-capital"]
    fallback-sources: ["yahoo-reits", "nareit"]
    markets: ["US_REITS", "GLOBAL_REITS", "DIRECT_RE"]
    
  crypto:
    primary-sources: ["coinbase-pro", "binance", "kraken"]
    fallback-sources: ["coingecko", "coinmarketcap"]
    markets: ["BTC", "ETH", "MAJOR_ALTS", "DEFI"]
    
  cash:
    primary-sources: ["money-market-funds", "treasury-bills"]
    fallback-sources: ["fed-data", "bank-deposits"]
    markets: ["MMF", "TB", "CD", "SAVINGS"]
    
  alternative:
    primary-sources: ["preqin", "pitchbook"]
    fallback-sources: ["hedge-fund-research"]
    markets: ["PRIVATE_EQUITY", "HEDGE_FUNDS", "INFRASTRUCTURE"]
    
  spot-trading:
    primary-sources: ["trade-finance", "commodity-spot"]
    fallback-sources: ["shipping-data", "warehouse-receipts"]
    markets: ["PHYSICAL_DELIVERY", "SPOT_MARKETS"]
```

### API设计

#### 资产类别统一查询接口
```http
GET /api/v1/cash-flows/asset-categories/{category}
```

**参数:**
- `category`: 资产类别 (stock, bond, forex, commodity, real-estate, crypto, cash, alternative, spot-trading)
- `timeRange`: 时间范围 (1h, 4h, 1d, 1w, 1m, 3m, 1y)
- `granularity`: 数据粒度 (5m, 15m, 1h, 4h, 1d)
- `subCategory`: 子类别筛选 (可选)
- `region`: 地区筛选 (可选)

**响应格式:**
```json
{
  "success": true,
  "data": {
    "assetCategory": "stock",
    "timeRange": "1d",
    "summary": {
      "totalNetInflow": 2500000000,
      "totalVolume": 120000000000,
      "flowIntensity": 2.08,
      "activeInstruments": 1250,
      "lastUpdated": "2025-01-17T15:30:00Z"
    },
    "breakdown": {
      "bySubCategory": [
        {
          "name": "Large Cap",
          "netInflow": 1800000000,
          "percentage": 72.0,
          "change24h": 15.2
        },
        {
          "name": "Mid Cap", 
          "netInflow": 500000000,
          "percentage": 20.0,
          "change24h": -8.5
        },
        {
          "name": "Small Cap",
          "netInflow": 200000000,
          "percentage": 8.0,
          "change24h": 22.1
        }
      ],
      "byRegion": [
        {
          "region": "North America",
          "netInflow": 1500000000,
          "percentage": 60.0
        },
        {
          "region": "Asia Pacific",
          "netInflow": 700000000,
          "percentage": 28.0
        },
        {
          "region": "Europe",
          "netInflow": 300000000,
          "percentage": 12.0
        }
      ]
    },
    "trends": {
      "hourlyData": [
        {
          "timestamp": "2025-01-17T14:00:00Z",
          "netInflow": 150000000,
          "volume": 8500000000
        }
        // ... 更多小时数据
      ],
      "movingAverages": {
        "ma24h": 145000000,
        "ma7d": 135000000,
        "ma30d": 125000000
      }
    },
    "alerts": [
      {
        "type": "HIGH_INFLOW",
        "message": "Large Cap股票资金流入超过24小时均值50%",
        "severity": "INFO",
        "timestamp": "2025-01-17T15:25:00Z"
      }
    ]
  },
  "metadata": {
    "dataSources": ["eastmoney", "polygon", "alpha-vantage"],
    "dataQuality": "HIGH",
    "coverage": "95.2%",
    "latency": "4.2s"
  }
}
```

#### 资产类别对比分析接口
```http
POST /api/v1/cash-flows/asset-categories/compare
```

**请求体:**
```json
{
  "categories": ["stock", "bond", "commodity"],
  "timeRange": "7d",
  "metrics": ["netInflow", "volume", "volatility"],
  "normalization": "percentage"
}
```

## 实施计划

### Phase 1: 核心资产类别 (4周)
- [x] US-002B-1: 股票类别分析 (已完成)
- [ ] US-002B-2: 债券类别分析
- [ ] US-002B-3: 外汇类别分析

### Phase 2: 扩展资产类别 (3周)
- [ ] US-002B-4: 大宗商品类别分析
- [ ] US-002B-6: 加密货币类别分析
- [ ] US-002B-7: 现金类资产分析

### Phase 3: 特殊资产类别 (2周)
- [ ] US-002B-5: 房地产类别分析
- [ ] US-002B-8: 另类投资类别分析
- [ ] US-002B-9: 现货贸易类别分析

### Phase 4: 集成和优化 (2周)
- [ ] 跨资产类别对比分析
- [ ] 资产轮动分析
- [ ] 智能预警和异常检测
- [ ] 性能优化和压力测试

## 监控和质量保证

### 关键指标监控
- 各资产类别数据采集成功率
- API响应时间分布
- 数据质量评分趋势
- 用户查询频次分析

### 质量保证措施
- 多源数据交叉验证
- 异常数据自动检测
- 实时数据质量监控
- 用户反馈收集机制

### 风险控制
- 数据源失效自动切换
- 服务降级策略
- 缓存预热机制
- 容量自动扩缩

## 文档和培训

### 技术文档
- API文档和使用示例
- 数据源配置指南
- 故障排除手册
- 性能调优指南

### 用户文档
- 功能使用指南
- 最佳实践建议
- 常见问题解答
- 案例分析报告

---

**版本**: 1.0  
**创建日期**: 2025-01-17  
**最后更新**: 2025-01-17  
**负责人**: 资金流动监控系统开发团队