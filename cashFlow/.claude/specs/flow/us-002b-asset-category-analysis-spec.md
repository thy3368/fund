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

#### 数据采集需求详细定义

##### 需要采集的数据类型

**核心指数数据:**
- **指数OHLCV数据**: 开盘价、最高价、最低价、收盘价、成交量
- **ETF资金流数据**: 
  - 直接数据: ETF日净流入/流出、申购赎回单位数
  - 计算数据: 基于ETF推算的指数资金流向
- **指数成份变化**: 成份股调整、权重变化  
- **跨市场数据**: 期货持仓变化、期权流向数据

**指数元数据:**
- **基本信息**: 指数代码、名称、交易所、基准日期
- **分类信息**: 市场分类(发达/新兴)、行业分类、风格分类  
- **地理信息**: 所属国家/地区、时区、货币
- **指数属性**: 基点、权重方法、成份股数量、市值

##### 13维度数据映射

| 维度 | 所需数据字段 | 数据来源 |
|------|-------------|----------|
| **地理维度** | 指数所属国家/地区、交易所位置 | 指数基本信息 |
| **货币维度** | 指数计价货币、汇率转换 | 交易所数据、汇率API |
| **市值维度** | 大盘股指数(SPY)、中盘股(MDY)、小盘股(IWM) | 不同市值指数对比 |
| **风格维度** | 成长型指数、价值型指数、股息指数 | 风格分类指数 |
| **行业维度** | 11个行业ETF(XLK/XLF/XLV等) | 行业分类ETF |
| **跨境维度** | 本土指数 vs 全球指数、跨境ETF | MSCI指数、跨境ETF |
| **时区维度** | 指数交易时区、主要交易时段 | 交易所时区信息 |
| **数据源维度** | 数据提供商、指数发布机构 | 指数公司、数据供应商 |
| **时间维度** | 交易时间、收盘时间、节假日 | 交易日历 |
| **风险情绪维度** | VIX指数、波动率指数 | 恐慌指数、波动率指标 |
| **流动性维度** | 指数成交量、ETF流动性 | 交易量数据 |
| **地缘政治维度** | 发达市场 vs 新兴市场 | 市场分类标准 |
| **质量维度** | 指数数据完整性、更新及时性 | 数据质量监控 |

##### 指数净流入数据获取策略

**直接获取ETF净流入数据 (30个):**
```yaml
us-etfs-direct-flows:
  市场ETF: [SPY, QQQ, IWM, VTI, DIA, MDY]
  行业ETF: [XLK, XLF, XLV, XLE, XLY, XLP, XLI, XLB, XLU, XLRE, XLC]
  国际ETF: [EWJ, EWG, EWU, FXI, ASHR, EWY, EWZ, etc.]
  数据字段:
    - daily_net_inflow: 日净流入金额
    - shares_outstanding: 流通份额
    - creation_units: 申购单位数
    - redemption_units: 赎回单位数
  数据源: ETF.com, Morningstar, Bloomberg ETF
```

**通过相关ETF推算指数流向 (12个):**
```yaml
index-flow-calculation:
  中国指数:
    - 上证指数 ← 通过FXI, ASHR ETF推算
    - 深证成指 ← 通过ASHR, 159901推算
    - 创业板指 ← 通过159915, 159952推算
    
  其他指数:
    - 日经225 ← 通过EWJ, 1329.T推算
    - 恒生指数 ← 通过2800.HK, FXI推算
    - 欧洲指数 ← 通过EWG, EWU, EWQ推算
  
  计算方法:
    指数净流入 = Σ(相关ETF净流入 × 指数权重)
```

##### 数据源配置

**ETF净流入数据源（免费）:**
```yaml
free-etf-sources:
  etf-com:
    api-key: not-required
    rate-limit: 1000-calls/day
    coverage: 美国主要ETF
    data-types: [日净流入, 申购赎回, AUM变化]
    
  yahoo-finance:
    rate-limit: 2000-calls/hour  
    coverage: 全球指数和ETF
    data-types: [指数OHLCV, ETF基本信息]
    
  morningstar-basic:
    coverage: 全球ETF
    data-types: [ETF流向, 持仓数据]
    limitations: 延迟1天
```

**专业ETF数据源（付费）:**
```yaml
paid-etf-sources:
  bloomberg-etf:
    coverage: 全球ETF
    data-types: [实时ETF流向, 机构持仓, 衍生品数据]
    latency: <30分钟
    cost: 高
    
  morningstar-direct:
    coverage: 全球ETF详细数据
    data-types: [实时净流入, 成份股流向, 风险指标]
    latency: <1小时
    cost: 中
    
  refinitiv-lipper:
    coverage: ETF和基金数据
    data-types: [流向分析, 同类对比, 业绩归因]
    quality: 机构级
    cost: 高
```

**指数数据源:**
```yaml
index-sources:
  alpha-vantage:
    coverage: 全球主要指数OHLCV
    data-types: [指数价格, 基本信息]
    
  investing-com:
    coverage: 全球指数实时数据
    data-types: [指数OHLCV, 技术指标]
    
  eastmoney:
    coverage: 中国指数
    data-types: [指数OHLCV, 成份股数据]
```

**验收标准:**
- WHEN 系统启动时 THEN 应成功连接至少2个外部数据源（东方财富、Yahoo Finance等）
- WHEN 数据采集运行时 THEN 系统应每5分钟从各数据源获取最新42个指数数据
- WHEN 接收到数据源响应时 THEN 系统应将指数原始数据暂存到数据缓冲区
- WHEN 数据源不可用时 THEN 系统应自动切换到备用数据源并记录告警
- WHEN 采集完成时 THEN 系统应记录采集统计信息（成功率、延迟、指数覆盖率）

#### 数据质量验收标准

**数据完整性验收:**
```yaml
completeness-tests:
  index-ohlcv-data:
    - test: "验证指数OHLCV数据无缺失"
      condition: "HIGH >= LOW AND OPEN,CLOSE BETWEEN LOW,HIGH"
      threshold: "99.5%"
      
  etf-volume-data:
    - test: "验证ETF成交量为正数"
      condition: "VOLUME >= 0"
      threshold: "100%"
      
  timestamp-data:
    - test: "验证时间戳格式和时区"
      condition: "TIMESTAMP IS VALID AND TIMEZONE CONSISTENT"
      threshold: "100%"
```

**数据质量验收:**
```yaml
quality-tests:
  index-validation:
    - test: "指数价格异常检测"
      condition: "|INDEX_CHANGE| < 20% OR HAS_MARKET_EVENT"
      threshold: "99.9%"
      
  etf-flow-validation:
    - test: "ETF资金流数据一致性"
      condition: "NET_INFLOW = INFLOW - OUTFLOW"
      threshold: "100%"
      
  cross-source-validation:
    - test: "多源指数数据一致性"
      condition: "ABS(SOURCE1_INDEX - SOURCE2_INDEX) / SOURCE1_INDEX < 0.05%"
      threshold: "98%"
```

**13维度分类验收:**
```yaml
dimension-tests:
  geographic:
    - test: "地理维度分类准确性"
      sample-size: 42
      manual-verification: true
      target-accuracy: "100%"
      
  market-cap:
    - test: "市值维度分类正确性"
      condition: "SPY=大盘股, MDY=中盘股, IWM=小盘股"
      threshold: "100%"
      
  sector:
    - test: "行业维度分类标准化"
      condition: "11个行业ETF分类正确"
      threshold: "100%"
```

#### 监控指标

**采集性能指标:**
```yaml
performance-kpis:
  collection-success-rate:
    target: ">99.5%"
    calculation: "成功采集指数次数 / 总采集次数"
    
  index-coverage:
    target: ">95%"
    calculation: "有数据的指数数 / 目标42个指数"
    
  average-latency:
    target: "<2秒"
    calculation: "平均指数数据采集延迟"
    
  api-quota-usage:
    target: "<50%"
    calculation: "已使用API调用 / API配额"
```

**数据质量指标:**
```yaml
quality-kpis:
  data-accuracy:
    target: ">99.5%"
    calculation: "通过验证的指数记录数 / 总记录数"
    
  duplicate-rate:
    target: "<0.01%"
    calculation: "重复指数记录数 / 总记录数"
    
  missing-data-rate:
    target: "<0.5%"
    calculation: "缺失指数字段数 / 总字段数"
```

#### 实施优先级 - 市场指数采集方案

**第一批采集（高优先级）:**
- **美股指数**: SPY(S&P500), QQQ(NASDAQ100), IWM(Russell2000)
- **中国指数**: 000001(上证指数), 399001(深证成指), 399006(创业板指)
- **港股指数**: HSI(恒生指数), HSCEI(恒生国企指数)
- **数据类型**: 指数OHLCV + ETF资金流 + 市场元数据

**第二批采集（中优先级）:**
- **欧洲指数**: SX5E(欧洲STOXX50), UKX(英国FTSE100), DAX(德国DAX30)
- **日本指数**: NKY(日经225), TPX(东证指数)
- **数据类型**: 增加期货数据 + 跨境资金流

**第三批采集（扩展覆盖）:**
- **新兴市场指数**: SENSEX(印度), IBOV(巴西), KOSPI(韩国), RTS(俄罗斯)
- **行业指数**: 科技、金融、能源、医疗等主要行业ETF
- **数据类型**: 完整13维度分类数据

#### 指数采集详细清单

**核心市场指数 (23个):**
```yaml
tier1-indices:
  北美 (6个):
    - SPY: SPDR S&P 500 ETF
    - QQQ: Invesco QQQ ETF (NASDAQ-100)
    - IWM: iShares Russell 2000 ETF
    - VTI: Vanguard Total Stock Market ETF
    - DIA: SPDR Dow Jones Industrial Average ETF
    - MDY: SPDR S&P MidCap 400 ETF
    
  中国 (5个):
    - 000001.SS: 上证综合指数
    - 399001.SZ: 深证成份指数
    - 399006.SZ: 创业板指数
    - 000016.SS: 上证50指数
    - HSI: 恒生指数
    
  欧洲 (7个):
    - SX5E: Euro STOXX 50
    - UKX: FTSE 100 (英国)
    - DAX: DAX 30 (德国)
    - CAC: CAC 40 (法国)
    - IBEX: IBEX 35 (西班牙)
    - AEX: AEX 25 (荷兰)
    - SMI: Swiss Market Index
    
  亚太其他 (5个):
    - NKY: Nikkei 225 (日本)
    - TPX: TOPIX (日本)
    - AS51: ASX 200 (澳洲)
    - STI: Straits Times Index (新加坡)
    - KOSPI: KOSPI 200 (韩国)
```

**新兴市场指数 (8个):**
```yaml
tier2-indices:
  新兴亚洲:
    - SENSEX: BSE Sensex (印度)
    - NIFTY: NSE Nifty 50 (印度)
    - SET: SET Index (泰国)
    - PCOMP: PSEi (菲律宾)
    
  新兴美洲:
    - IBOV: Bovespa Index (巴西)
    - IPSA: IPSA (智利)
    
  新兴欧非:
    - RTS: RTS Index (俄罗斯)
    - TOP40: FTSE/JSE Top 40 (南非)
```

**行业ETF指数 (11个):**
```yaml
sector-etfs:
  科技: XLK (Technology Select Sector SPDR)
  金融: XLF (Financial Select Sector SPDR) 
  医疗: XLV (Health Care Select Sector SPDR)
  能源: XLE (Energy Select Sector SPDR)
  消费: XLY (Consumer Discretionary SPDR)
  必需消费: XLP (Consumer Staples SPDR)
  工业: XLI (Industrial Select Sector SPDR)
  材料: XLB (Materials Select Sector SPDR)
  公用事业: XLU (Utilities Select Sector SPDR)
  房地产: XLRE (Real Estate Select Sector SPDR)
  通讯: XLC (Communication Services SPDR)
```

**总计: 42个指数/ETF**

#### 指数采集优势

**相比个股采集的优势:**
- ✅ **数据量减少99%**: 从4000+个股降至42个指数
- ✅ **成本降低95%**: API调用次数大幅减少
- ✅ **覆盖度更全**: 指数代表整个市场资金流向
- ✅ **数据质量更高**: 指数数据更加标准化和可靠
- ✅ **实时性更好**: 更容易获得实时数据
- ✅ **维护简单**: 指数构成相对稳定

**支持的分析维度:**
- 📊 **地理维度**: 不同国家/地区市场对比
- 💰 **市值维度**: 大盘股(SPY) vs 小盘股(IWM)
- 🏭 **行业维度**: 11个主要行业ETF对比
- 📈 **风格维度**: 成长型 vs 价值型指数
- 🌍 **发达vs新兴**: 发达市场 vs 新兴市场资金流向

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

## 验收标准

### 功能验收标准

#### US-002B-1: 股票类别分析
**WHEN** 用户选择股票类别 **THEN** 系统应显示全球主要股市的净流入/流出总额，响应时间<2秒
**WHEN** 用户查看股票细分 **THEN** 系统应提供按市值分类（大盘股、中小盘股）的资金流向数据
**WHEN** 用户查看风格分类 **THEN** 系统应显示成长股vs价值股的资金流向对比分析
**WHEN** 用户查看行业分布 **THEN** 系统应显示主要行业板块的资金流入流出情况和排名
**IF** 某个细分类别无数据 **THEN** 系统应显示"暂无数据"提示并建议查看总体股市数据


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
