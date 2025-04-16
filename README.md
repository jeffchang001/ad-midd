# SOGO AD 中台系統

## 專案簡介

SOGO AD 中台系統是一個專門用於管理和同步 Active Directory (AD) 帳戶的中介服務平台。本系統負責處理員工資料與 AD 系統之間的同步，提供統一的 AD 帳戶管理介面，並確保資料的一致性與安全性。

### 主要功能

- AD 帳戶生命週期管理
- 組織結構（OU）維護
- 與員工中台（ee-midd）整合
- Azure AD 同步
- LDAP 操作支援
- 系統監控與稽核

## 系統架構

### 技術堆疊

- **後端框架**：Spring Boot 2.7.18
- **資料庫**：PostgreSQL 42.7.4
- **安全框架**：Spring Security
- **AD 整合**：
  - Spring LDAP
  - Azure AD SDK
- **API 文件**：Springdoc OpenAPI UI
- **監控工具**：Spring Boot Actuator

### 系統依賴

- Java 8
- Maven 3.8+
- PostgreSQL 14+
- Docker & Docker Compose

## 安裝說明

### 前置條件

1. 安裝必要軟體：

   ```bash
   # 檢查 Java 版本
   java -version

   # 檢查 Maven 版本
   mvn -version

   # 檢查 Docker 版本
   docker -v
   docker-compose -v
   ```
2. 設定環境變數：

   ```bash
   # 複製環境變數範本
   cp .env.example .env

   # 編輯環境變數
   vim .env
   ```

### 資料庫設定

1. 建立資料庫：

   ```sql
   CREATE DATABASE ad_midd;
   ```
2. 執行資料庫腳本：

   ```bash
   # 進入 sql 目錄
   cd sql

   # 執行初始化腳本
   psql -U postgres -d ad_midd -f schema.sql
   ```

### 專案建置

1. 編譯專案：

   ```bash
   mvn clean package -DskipTests
   ```
2. 使用 Docker Compose 啟動：

   ```bash
   docker-compose up -d
   ```

## 環境變數配置

### 必要環境變數

```properties
# 應用程式設定
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=prod
APP_NAME=ad-midd

# 資料庫設定
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/ad_midd
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password

# AD 設定
AD_LDAP_URL=ldaps://your.ad.server:636
AD_LDAP_BASE=dc=sogo,dc=com,dc=tw
AD_LDAP_USERNAME=cn=admin,dc=sogo,dc=com,dc=tw
AD_LDAP_PASSWORD=your_ldap_password

# Azure AD 設定
AZURE_AD_TENANT_ID=your_tenant_id
AZURE_AD_CLIENT_ID=your_client_id
AZURE_AD_CLIENT_SECRET=your_client_secret

# JWT 設定
JWT_SECRET=your_jwt_secret
JWT_EXPIRATION=86400
```

### 選用環境變數

```properties
# 日誌設定
LOGGING_LEVEL_ROOT=INFO
LOGGING_FILE_PATH=/var/log/ad-midd

# 快取設定
SPRING_CACHE_TYPE=redis
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

# 監控設定
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics

# Swagger 文件設定
SPRINGDOC_SWAGGER_UI_PATH=/swagger-ui.html
SPRINGDOC_API_DOCS_PATH=/v3/api-docs

# 效能調校
SPRING_JPA_PROPERTIES_HIBERNATE_JDBC_BATCH_SIZE=100
SPRING_JPA_PROPERTIES_HIBERNATE_ORDER_INSERTS=true

# 同步任務設定
SYNC_SCHEDULE_CRON=0 0 1 * * ?
SYNC_BATCH_SIZE=1000
```

## 系統關聯

### 與 ee-midd 的關係

本系統（ad-midd）作為 AD 中台，主要負責：

1. **資料同步**

   - 從 ee-midd 獲取員工資料
   - 處理資料轉換與驗證
   - 同步至 AD 系統
2. **資料流向**

   ```
   ee-midd → ad-midd → AD/Azure AD
   ```
3. **同步機制**

   - 定期全量同步
   - 即時增量同步
   - 手動觸發同步

## API 文件

### Swagger UI

- 開發環境：http://localhost:8081/swagger-ui.html
- 測試環境：http://test-ad-midd.sogo.com.tw/swagger-ui.html
- 正式環境：http://ad-midd.sogo.com.tw/swagger-ui.html

### OpenAPI 規格

- JSON 格式：/v3/api-docs
- YAML 格式：/v3/api-docs.yaml

## 開發指南

### 分支管理

- `main`: 主分支，用於生產環境
- `develop`: 開發分支，用於整合功能
- `feature/*`: 功能分支
- `hotfix/*`: 緊急修復分支

### 程式碼規範

1. **命名規則**

   - 類別名稱：PascalCase
   - 方法名稱：camelCase
   - 常數：UPPER_SNAKE_CASE
2. **API 設計**

   - 遵循 Google RESTful API 設計規範
   - 使用繁體中文進行註解
   - API 版本控制：/api/v1/*
3. **提交規範**

   ```
   feat: 新增功能
   fix: 修復問題
   docs: 文件更新
   style: 程式碼格式調整
   refactor: 重構程式碼
   test: 測試相關
   chore: 建置/工具相關
   ```

## 監控與維護

### 健康檢查

- 端點：/actuator/health
- 檢查項目：
  - 資料庫連線
  - AD 連線狀態
  - 記憶體使用量
  - 磁碟空間

### 日誌管理

1. **日誌位置**

   - 容器內：/var/log/ad-midd/
   - 主機掛載：./logs/ad-midd/
2. **日誌等級**

   - ERROR：系統錯誤
   - WARN：警告訊息
   - INFO：一般資訊
   - DEBUG：除錯資訊

### 效能監控

- 端點：/actuator/metrics
- 監控項目：
  - API 響應時間
  - JVM 記憶體使用
  - 資料庫連線池
  - 同步作業狀態

## 常見問題

### 同步失敗處理

1. 檢查錯誤日誌
2. 驗證連線狀態
3. 確認資料格式
4. 重試同步作業

### 效能優化建議

1. 調整批次處理大小
2. 優化資料庫查詢
3. 設定合適的快取策略
4. 監控系統資源使用

## 授權資訊

© 2024 SOGO. 版權所有。
