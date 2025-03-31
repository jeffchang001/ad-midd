package com.sogo.ad.midd.util;

import java.util.List;
import java.util.Scanner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.sogo.ad.midd.AdMiddApplication;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.repository.APIEmployeeInfoRepository;
import com.sogo.ad.midd.service.AzureADService;

import lombok.extern.slf4j.Slf4j;

/**
 * Azure AD 帳戶管理控制台應用程式
 * 用於執行 Azure AD 帳戶相關操作
 * 
 * 使用方式:
 * 1. 直接執行該類的 main 方法
 * 2. 在控制台選擇要執行的操作
 */
@Slf4j
@SpringBootApplication
@Import(AdMiddApplication.class)
public class AzureADAccountManagementConsole {

    @Autowired
    private AzureADService azureADService;
    
    @Autowired
    private APIEmployeeInfoRepository employeeInfoRepository;
    
    private Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        SpringApplication.run(AzureADAccountManagementConsole.class, args);
    }
    
    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            boolean running = true;
            
            while (running) {
                displayMenu();
                int choice = getUserChoice();
                
                try {
                    switch (choice) {
                        case 1:
                            deleteAllAccounts();
                            break;
                        case 2:
                            disableSpecificAccount();
                            break;
                        case 3:
                            enableSpecificAccount();
                            break;
                        case 4:
                            disableAccountsByDate();
                            break;
                        case 5:
                            enableAccountsByDate();
                            break;
                        case 6:
                            listAllAccounts();
                            break;
                        case 0:
                            running = false;
                            log.info("退出程序");
                            break;
                        default:
                            System.out.println("無效的選擇，請重新輸入");
                    }
                } catch (Exception e) {
                    log.error("操作執行過程中發生錯誤: {}", e.getMessage(), e);
                    System.out.println("操作失敗: " + e.getMessage());
                }
                
                if (running && choice != 0) {
                    System.out.println("\n按 Enter 鍵繼續...");
                    scanner.nextLine();
                }
            }
            
            // 關閉掃描器並退出應用程式
            scanner.close();
            System.exit(0);
        };
    }
    
    private void displayMenu() {
        System.out.println("\n=== Azure AD 帳戶管理系統 ===");
        System.out.println("1. 刪除所有 E1 帳戶");
        System.out.println("2. 停用指定員工帳戶");
        System.out.println("3. 啟用指定員工帳戶");
        System.out.println("4. 根據日期停用帳戶");
        System.out.println("5. 根據日期啟用帳戶");
        System.out.println("6. 列出所有員工帳戶");
        System.out.println("0. 退出");
        System.out.println("============================");
        System.out.print("請選擇操作: ");
    }
    
    private int getUserChoice() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private void deleteAllAccounts() throws Exception {
        // 獲取所有帳戶總數，提示用戶確認
        List<APIEmployeeInfo> allAccounts = employeeInfoRepository.findAll();
        log.info("發現 {} 個帳戶將被處理", allAccounts.size());
        
        // 顯示警告並要求確認
        System.out.println("警告: 此操作將刪除所有 Azure AD E1 帳戶，共 " + allAccounts.size() + " 個帳戶");
        System.out.println("確定要繼續嗎？輸入 'YES' 確認執行，或任意其他鍵取消");
        String confirmation = scanner.nextLine();
        
        if ("YES".equals(confirmation)) {
            log.info("確認刪除，開始執行...");
            azureADService.deleteAADE1AllAccountProcessor();
            log.info("刪除所有 Azure AD E1 帳戶操作已完成");
            System.out.println("✅ 成功刪除所有 Azure AD E1 帳戶");
        } else {
            log.info("操作已取消");
            System.out.println("❌ 操作已取消");
        }
    }
    
    private void disableSpecificAccount() throws Exception {
        System.out.println("請輸入要停用的員工號碼 (例如: E001):");
        String employeeNo = scanner.nextLine();
        
        // 檢查員工是否存在
        APIEmployeeInfo employeeInfo = employeeInfoRepository.findByEmployeeNo(employeeNo);
        if (employeeInfo == null) {
            log.error("找不到員工號碼 {} 的資料", employeeNo);
            System.out.println("❌ 找不到員工號碼 " + employeeNo + " 的資料");
            return;
        }
        
        log.info("開始處理員工 {}, 電子郵件: {}", 
                employeeInfo.getEmployeeNo(), employeeInfo.getEmailAddress());
        
        // 顯示警告並要求確認
        System.out.println("警告: 將停用員工 " + employeeNo + " 的 Azure AD 帳戶");
        System.out.println("郵箱: " + employeeInfo.getEmailAddress());
        System.out.println("確定要繼續嗎？輸入 'YES' 確認執行，或任意其他鍵取消");
        
        String confirmation = scanner.nextLine();
        if ("YES".equals(confirmation)) {
            log.info("確認停用，開始執行...");
            
            // 使用公開方法停用帳戶
            azureADService.disableAADE1AccountProcessor(employeeInfo.getEmailAddress());
            log.info("成功停用員工 {} 的 Azure AD 帳戶", employeeNo);
            System.out.println("✅ 成功停用員工 " + employeeNo + " 的 Azure AD 帳戶");
        } else {
            log.info("操作已取消");
            System.out.println("❌ 操作已取消");
        }
    }
    
    private void enableSpecificAccount() throws Exception {
        System.out.println("請輸入要啟用的員工號碼 (例如: E001):");
        String employeeNo = scanner.nextLine();
        
        // 檢查員工是否存在
        APIEmployeeInfo employeeInfo = employeeInfoRepository.findByEmployeeNo(employeeNo);
        if (employeeInfo == null) {
            log.error("找不到員工號碼 {} 的資料", employeeNo);
            System.out.println("❌ 找不到員工號碼 " + employeeNo + " 的資料");
            return;
        }
        
        log.info("開始處理員工 {}, 電子郵件: {}", 
                employeeInfo.getEmployeeNo(), employeeInfo.getEmailAddress());
        
        // 顯示警告並要求確認
        System.out.println("警告: 將啟用員工 " + employeeNo + " 的 Azure AD 帳戶");
        System.out.println("郵箱: " + employeeInfo.getEmailAddress());
        System.out.println("確定要繼續嗎？輸入 'YES' 確認執行，或任意其他鍵取消");
        
        String confirmation = scanner.nextLine();
        if ("YES".equals(confirmation)) {
            log.info("確認啟用，開始執行...");
            
            // 使用公開方法啟用帳戶
            azureADService.enableAADE1AccountProcessor(
                    employeeInfo.getEmailAddress(), 
                    employeeInfo.getIdNoSuffix());
            log.info("成功啟用員工 {} 的 Azure AD 帳戶", employeeNo);
            System.out.println("✅ 成功啟用員工 " + employeeNo + " 的 Azure AD 帳戶");
        } else {
            log.info("操作已取消");
            System.out.println("❌ 操作已取消");
        }
    }
    
    private void disableAccountsByDate() throws Exception {
        System.out.println("請輸入要處理的日期 (格式: yyyy-MM-dd):");
        String date = scanner.nextLine();
        
        System.out.println("請輸入要處理的員工號碼 (留空處理所有):");
        String employeeNo = scanner.nextLine();
        
        // 顯示警告並要求確認
        System.out.println("警告: 此操作將停用 " + date + " 日期標記為刪除的員工帳戶");
        if (!employeeNo.isEmpty()) {
            System.out.println("僅處理員工: " + employeeNo);
        } else {
            System.out.println("處理所有相關員工");
        }
        System.out.println("確定要繼續嗎？輸入 'YES' 確認執行，或任意其他鍵取消");
        
        String confirmation = scanner.nextLine();
        if ("YES".equals(confirmation)) {
            log.info("確認停用，開始執行...");
            azureADService.disableAADE1Account(employeeNo.isEmpty() ? null : employeeNo, date);
            log.info("停用操作已完成");
            System.out.println("✅ 停用操作已完成");
        } else {
            log.info("操作已取消");
            System.out.println("❌ 操作已取消");
        }
    }
    
    private void enableAccountsByDate() throws Exception {
        System.out.println("請輸入要處理的日期 (格式: yyyy-MM-dd):");
        String date = scanner.nextLine();
        
        System.out.println("請輸入要處理的員工號碼 (留空處理所有):");
        String employeeNo = scanner.nextLine();
        
        // 顯示警告並要求確認
        System.out.println("警告: 此操作將啟用 " + date + " 日期標記為創建的員工帳戶");
        if (!employeeNo.isEmpty()) {
            System.out.println("僅處理員工: " + employeeNo);
        } else {
            System.out.println("處理所有相關員工");
        }
        System.out.println("確定要繼續嗎？輸入 'YES' 確認執行，或任意其他鍵取消");
        
        String confirmation = scanner.nextLine();
        if ("YES".equals(confirmation)) {
            log.info("確認啟用，開始執行...");
            azureADService.enableAADE1Account(employeeNo.isEmpty() ? null : employeeNo, date);
            log.info("啟用操作已完成");
            System.out.println("✅ 啟用操作已完成");
        } else {
            log.info("操作已取消");
            System.out.println("❌ 操作已取消");
        }
    }
    
    private void listAllAccounts() {
        try {
            List<APIEmployeeInfo> allAccounts = employeeInfoRepository.findAll();
            log.info("發現 {} 個員工帳戶", allAccounts.size());
            
            System.out.println("所有員工帳戶信息:");
            System.out.println("-------------------------------------");
            System.out.printf("%-10s | %-30s | %-10s\n", "員工號", "電子郵件", "狀態");
            System.out.println("-------------------------------------");
            
            for (APIEmployeeInfo account : allAccounts) {
                System.out.printf("%-10s | %-30s | %-10s\n", 
                        account.getEmployeeNo(), 
                        account.getEmailAddress(),
                        account.getStatus());
            }
            
            System.out.println("-------------------------------------");
            System.out.println("總計: " + allAccounts.size() + " 個帳戶");
        } catch (Exception e) {
            log.error("列出員工帳戶時發生錯誤: {}", e.getMessage(), e);
            System.out.println("❌ 列出員工帳戶時發生錯誤: " + e.getMessage());
        }
    }
} 