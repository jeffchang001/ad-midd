# Azure AD 帳戶管理工具

本文檔提供如何使用 `AzureADAccountManagementConsole` 工具管理 Azure AD 帳戶的說明。

## 功能概述

管理工具提供以下功能：

1. **刪除所有 E1 帳戶** - 執行刪除所有 Azure AD E1 帳戶的操作
2. **停用指定員工帳戶** - 針對特定員工停用 E1 帳戶
3. **啟用指定員工帳戶** - 針對特定員工啟用 E1 帳戶
4. **根據日期停用帳戶** - 根據日期停用被標記為刪除的帳戶
5. **根據日期啟用帳戶** - 根據日期啟用被標記為創建的帳戶
6. **列出所有帳戶** - 顯示系統中所有員工帳戶信息

## 使用方式

### 方法一：使用 Maven 命令行運行

在專案根目錄下執行以下命令啟動管理工具：

```bash
mvn spring-boot:run -Dspring-boot.run.main-class=com.sogo.ad.midd.util.AzureADAccountManagementConsole
```

### 方法二：使用 IDE 運行

1. 在 IDE 中打開專案
2. 找到 `AzureADAccountManagementConsole` 類
3. 右鍵單擊該類，選擇 "Run" 或 "Debug" 運行

### 方法三：打包後運行

1. 使用 Maven 打包專案：

```bash
mvn clean package
```

2. 使用 Java 直接運行：

```bash
java -cp target/ad-midd-0.0.1-SNAPSHOT.jar -Dloader.main=com.sogo.ad.midd.util.AzureADAccountManagementConsole org.springframework.boot.loader.PropertiesLauncher
```

## 操作說明

1. 啟動應用後，會顯示一個選單，選擇要執行的操作編號
2. 根據提示輸入必要的資訊，如員工編號或日期
3. 操作前都會要求確認，輸入 `YES` 確認執行
4. 操作完成後，按 Enter 鍵返回選單

## ˊˊ注意事項

1. **危險操作**：這些操作會直接影響實際的 Azure AD 環境，請謹慎使用
2. **確認提示**：每個危險操作執行前都需要輸入 `YES` 確認，其他輸入則取消操作
3. **日誌記錄**：所有操作都有詳細的日誌記錄，保存在 `logs/ad-midd.log` 中

## 常見問題

1. **連接失敗**：檢查 Azure AD 憑證是否正確，網絡連接是否正常
2. **找不到用戶**：確認員工編號是否正確，且在數據庫中存在
3. **操作失敗**：檢查日誌檔案了解詳細錯誤訊息

## 開發與擴展

如需添加新功能或修改現有功能，請修改 `AzureADAccountManagementConsole` 類。主要步驟：

1. 在 `displayMenu()` 方法中添加新選單項
2. 在 `commandLineRunner()` 方法的 switch 區塊中添加新的 case
3. 添加新的方法實現功能邏輯
