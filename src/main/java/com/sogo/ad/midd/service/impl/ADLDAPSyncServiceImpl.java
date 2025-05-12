package com.sogo.ad.midd.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.sogo.ad.midd.model.dto.ADEmployeeSyncDto;
import com.sogo.ad.midd.model.dto.ADOrganizationSyncDto;
import com.sogo.ad.midd.model.dto.OrganizationHierarchyDto;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.model.entity.APIEmployeeInfoActionLog;
import com.sogo.ad.midd.model.entity.APIOrganization;
import com.sogo.ad.midd.repository.APIEmployeeInfoActionLogRepository;
import com.sogo.ad.midd.repository.APIEmployeeInfoRepository;
import com.sogo.ad.midd.service.ADLDAPSyncService;
import com.sogo.ad.midd.service.AzureADService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ADLDAPSyncServiceImpl implements ADLDAPSyncService {

    @Value("${ldap.url}")
    private String ldapUrl;

    @Value("${ldap.base-dn}")
    private String ldapBaseDn;

    @Value("${ldap.username}")
    private String ldapUsername;

    @Value("${ldap.password}")
    private String ldapPassword;

    @Autowired
    private AzureADService azureADService;

    @Autowired
    private APIEmployeeInfoRepository employeeInfoRepository;

    @Autowired
    private APIEmployeeInfoActionLogRepository employeeInfoActionLogRepository;

    @Override
    public void syncEmployeeToAD(ADEmployeeSyncDto employeeData, LocalDate baseDate) throws Exception {
        log.info("employeeNo: {}", employeeData.getEmployeeNo());
        APIEmployeeInfo employee = employeeData.getEmployeeInfo();
        String action = employeeData.getAction();
        List<OrganizationHierarchyDto> orgHierarchy = employeeData.getOrgHierarchyDto();

        Name dn = buildEmployeeDn(employee.getEmployeeNo(), orgHierarchy);
        log.info("HR 組織樹的 DN: " + dn.toString());

        LdapContext ctx = null;
        switch (action) {
            case "C":
                log.debug("創建新員工: {}", employee.getEmployeeNo());
                createEmployeeByPowerShell(dn, employee);
                createEmployeeToDB(employeeData);
                break;
            case "U":
                log.debug("更新員工資訊: {}", employee.getEmployeeNo());
                ctx = getLdapContext();
                updateEmployee(ctx, employee, employeeData.getUpdatedFields());
                updateEmployeeToDB(employeeData);
                break;
            case "D":
                log.debug("停用員工: {}", employee.getEmployeeNo());
                disableADAccount(dn);
                deleteEmployeeToDB(employeeData, baseDate);
                break;
            default:
                log.error("未知的操作類型: {}", action);
                throw new IllegalArgumentException("Unknown action: " + action);
        }
        log.info("完成同步員工資訊到 LDAP, 員工編號: {}", employeeData.getEmployeeNo());
    }

    private LdapContext getLdapContext() throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put("java.naming.ldap.attributes.binary", "objectGUID");
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, ldapUsername);
        env.put(Context.SECURITY_CREDENTIALS, ldapPassword);
        env.put(Context.REFERRAL, "ignore");
        // env.put(Context.REFERRAL, "follow");
        // env.put("java.naming.ldap.version", "3");
        // env.put(Context.SECURITY_PROTOCOL, "ssl");

        // 添加超時設置，防止連接懸掛
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");

        LdapContext ctx = new InitialLdapContext(env, null);

        try {
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
            ctx.search(ldapBaseDn, "(objectClass=*)", searchControls);
            log.info("LDAP 連接成功");
        } catch (NamingException e) {
            log.error("LDAP 連接測試失敗", e);
            throw e;
        }

        return ctx;
    }

    /**
     * 構建員工的 LDAP DN
     * 
     * @param employeeNo   員工編號
     * @param orgHierarchy 組織層級列表
     * @return 員工的 LDAP DN
     */
    private Name buildEmployeeDn(String employeeNo, List<OrganizationHierarchyDto> orgHierarchy) {
        LdapName baseDn = LdapNameBuilder.newInstance(ldapBaseDn).build();
        LdapNameBuilder builder = LdapNameBuilder.newInstance();

        // 排序組織層級，從最低層級（數字最大）到最高層級（數字最小）
        List<OrganizationHierarchyDto> sortedOrgHierarchy = orgHierarchy.stream()
                .sorted(Comparator.comparingInt(OrganizationHierarchyDto::getOrgLevel).reversed())
                .collect(Collectors.toList());

        // 建構 OU 部分
        for (OrganizationHierarchyDto org : sortedOrgHierarchy) {
            builder.add("OU", org.getOrgName());
        }

        // 添加 CN
        builder.add("CN", employeeNo);

        // 合併 baseDn 和建構的 DN
        return LdapNameBuilder.newInstance(baseDn)
                .add(builder.build())
                .build();
    }

    private void updateEmployee(LdapContext ctx, APIEmployeeInfo employee, Map<String, String> updatedFields) {
        try {
            int pageSize = 1;
            byte[] cookie = null;
            ctx.setRequestControls(new Control[] { new PagedResultsControl(pageSize, Control.CRITICAL) });

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String[] returnedAtts = { "cn", "displayName", "mail", "telephoneNumber", "mobile", "title",
                    "userAccountControl" };
            searchControls.setReturningAttributes(returnedAtts);

            String searchFilter = String.format("(&(objectClass=person)(cn=%s))", employee.getEmployeeNo());
            log.debug("LDAP 搜索過濾器: {}", searchFilter);

            boolean employedStatusChanged = isEmployedStatusChanged(updatedFields);
            boolean organizationChanged = isOrganizationChanged(updatedFields);

            do {
                NamingEnumeration<SearchResult> results = ctx.search(ldapBaseDn, searchFilter, searchControls);

                while (results != null && results.hasMoreElements()) {
                    SearchResult searchResult = results.next();
                    String dn = searchResult.getNameInNamespace();
                    log.debug("找到員工 DN: {}", dn);

                    // 處理組織變更 - 如果 formulaOrgCode 有變更，需要移動員工帳號到新組織
                    if (organizationChanged) {
                        handleOrganizationChange(ctx, employee, dn, updatedFields.get("formulaOrgCode"));
                    }

                    // 處理啟用狀態變更 - 如果 employedStatus 從 3 變為 1，需要啟用帳號, 從 2 變為 1，則不需要改變
                    if (employedStatusChanged) {
                        String newStatus = updatedFields.get("employedStatus");
                        String oldStatus = updatedFields.get("oldValue");
                        if ("1".equals(newStatus) && "3".equals(oldStatus)) {
                            log.info("員工狀態變更(3->1)為啟用，啟用 AD 帳號: {}", employee.getEmployeeNo());
                            enableADAccount(ctx, new LdapName(dn));

                            // 重新啟用 Azure AD E1 帳號
                            try {
                                log.info("重新啟用員工 Azure AD E1 帳號: {}", employee.getEmployeeNo());
                                azureADService.enableAADE1AccountProcessor(employee.getEmailAddress(),
                                        employee.getIdNoSuffix());
                            } catch (Exception e) {
                                log.error("重新啟用 Azure AD E1 帳號失敗: {}", e.getMessage(), e);
                            }
                        } else if ("1".equals(newStatus) && "2".equals(oldStatus)) {
                            log.info("員工狀態變更為(2->1)啟用，啟用 AD 帳號: {}", employee.getEmployeeNo());
                            enableADAccount(ctx, new LdapName(dn));
                        } else if ("2".equals(newStatus)) {
                            log.info("員工狀態變更為留停，停用 AD 帳號: {}", employee.getEmployeeNo());
                            disableADAccount(new LdapName(dn));
                        }
                    }

                    // 處理一般屬性更新 - 這裡是需要主要修改的部分
                    for (Map.Entry<String, String> entry : updatedFields.entrySet()) {
                        String key = entry.getKey();
                        // 跳過 formulaOrgCode 和 employedStatus，因為已單獨處理
                        if ("formulaOrgCode".equals(key) || "employedStatus".equals(key)) {
                            continue;
                        }

                        String ldapAttribute = mapFieldToLdapAttribute(key);
                        String value = entry.getValue();

                        if (ldapAttribute != null && value != null && !value.trim().isEmpty()) {
                            updateSingleAttribute(ctx, dn, ldapAttribute, value.trim(), employee.getEmployeeNo());
                        }
                    }
                }

                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (Control control : controls) {
                        if (control instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                            cookie = prrc.getCookie();
                        }
                    }
                }

                ctx.setRequestControls(new Control[] { new PagedResultsControl(pageSize, cookie, Control.CRITICAL) });
            } while (cookie != null);

        } catch (NamingException | IOException e) {
            log.error("LDAP 操作失敗: {}", e.getMessage());
        }
    }

    // 新增一個方法專門處理單個屬性的更新
    private void updateSingleAttribute(LdapContext ctx, String dn, String attributeName, String attributeValue,
            String employeeNo) {
        log.debug("準備更新屬性 {} 為 {}, 員工編號: {}", attributeName, attributeValue, employeeNo);

        // 擴展特殊用戶列表，進一步識別面臨屬性更新問題的用戶
        boolean isSpecialUser = "654323".equals(employeeNo) ||
                (dn != null && dn.contains("總務課/忠孝"));

        // 特殊屬性標誌 - 對 extension 屬性總是使用 PowerShell
        boolean isSpecialAttribute = "extension".equals(attributeName);

        // 如果是特殊用戶或特殊屬性，優先使用 PowerShell
        if (isSpecialUser || isSpecialAttribute) {
            try {
                updateAttributeWithPowerShell(employeeNo, attributeName, attributeValue);
                return; // 如果成功使用 PowerShell 更新，直接返回
            } catch (Exception e) {
                log.warn("使用 PowerShell 更新特殊用戶屬性失敗，嘗試使用標準 LDAP 方法: {}", e.getMessage());
                // 失敗後繼續使用標準 LDAP 方法
            }
        }

        try {
            // 對所有用戶使用「先刪除後添加」的方式更新屬性
            try {
                // 步驟1: 嘗試刪除現有屬性（不指定值，刪除所有值）
                ModificationItem[] deleteMods = new ModificationItem[1];
                deleteMods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                        new BasicAttribute(attributeName));
                try {
                    ctx.modifyAttributes(dn, deleteMods);
                } catch (NamingException ne) {
                    // 忽略刪除不存在屬性的錯誤，僅記錄日誌
                    if (ne.getMessage().contains("LDAP: error code 16") ||
                            ne.getMessage().contains("NO_ATTRIBUTE_OR_VALUE")) {
                        log.debug("屬性 {} 不存在或為空，將直接添加，員工編號: {}",
                                attributeName, employeeNo);
                    } else {
                        // 其它錯誤則拋出以便後續處理
                        throw ne;
                    }
                }

                // 步驟2: 添加新的屬性值
                ModificationItem[] addMods = new ModificationItem[1];
                addMods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                        new BasicAttribute(attributeName, attributeValue));
                ctx.modifyAttributes(dn, addMods);

                log.info("成功更新屬性: {} = {}, 員工編號: {}",
                        attributeName, attributeValue, employeeNo);

            } catch (NamingException e) {
                // 處理更新失敗的情況
                if (e.getMessage().contains("LDAP: error code 1")) {
                    // 服務錯誤（error code 1），嘗試重試
                    handleAttributeUpdateRetry(ctx, dn, attributeName, attributeValue, employeeNo);
                } else {
                    log.error("更新屬性 {} 時發生錯誤: {}, 員工編號: {}",
                            attributeName, e.getMessage(), employeeNo);
                }
            }
        } catch (Exception e) {
            log.error("更新屬性過程中發生未捕獲的異常: {}, 員工編號: {}",
                    e.getMessage(), employeeNo, e);
        }
    }

    // 處理屬性更新重試
    private void handleAttributeUpdateRetry(LdapContext ctx, String dn, String attributeName,
            String attributeValue, String employeeNo) {
        log.warn("LDAP 伺服器錯誤 (error code 1)，嘗試延遲後重試: {}, 員工編號: {}",
                attributeName, employeeNo);

        boolean success = false;
        // 使用指數退避策略，基本等待時間為 1 秒
        for (int retryCount = 1; retryCount <= 3 && !success; retryCount++) {
            try {
                // 指數退避: 1秒, 2秒, 4秒
                int waitTime = (int) Math.pow(2, retryCount - 1) * 1000;
                Thread.sleep(waitTime);

                // 嘗試重新連接 LDAP 以避免使用過期連接
                if (retryCount > 1) {
                    try {
                        ctx.close();
                        ctx = getLdapContext();
                    } catch (NamingException e) {
                        log.error("重新連接 LDAP 失敗: {}", e.getMessage());
                        continue; // 連接失敗，繼續下一次重試
                    }
                }

                // 再次嘗試「先刪除後添加」操作
                try {
                    // 刪除現有屬性（忽略可能的錯誤）
                    try {
                        ModificationItem[] deleteMods = new ModificationItem[1];
                        deleteMods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                                new BasicAttribute(attributeName));
                        ctx.modifyAttributes(dn, deleteMods);
                    } catch (NamingException ne) {
                        // 忽略刪除時的錯誤
                        log.debug("重試過程中刪除屬性 {} 時發生錯誤（可能屬性不存在），忽略: {}",
                                attributeName, ne.getMessage());
                    }

                    // 添加新屬性
                    ModificationItem[] addMods = new ModificationItem[1];
                    addMods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                            new BasicAttribute(attributeName, attributeValue));
                    ctx.modifyAttributes(dn, addMods);

                    log.info("第{}次重試成功更新屬性: {} = {}, 員工編號: {}",
                            retryCount, attributeName, attributeValue, employeeNo);
                    success = true;

                } catch (NamingException ne) {
                    if (retryCount == 3) {
                        log.error("已重試3次，仍無法更新屬性 {}，放棄此次更新: {}, 員工編號: {}",
                                attributeName, ne.getMessage(), employeeNo);

                        // 最後一次重試失敗，嘗試使用 PowerShell 作為備選方案
                        if (!"654323".equals(employeeNo)) { // 確保不是已經嘗試過 PowerShell 的特殊用戶
                            try {
                                updateAttributeWithPowerShell(employeeNo, attributeName, attributeValue);
                            } catch (Exception pe) {
                                log.error("使用 PowerShell 更新屬性 {} 也失敗: {}, 員工編號: {}",
                                        attributeName, pe.getMessage(), employeeNo);
                            }
                        }
                    } else {
                        log.warn("第{}次重試更新屬性 {} 失敗，將再次嘗試: {}, 員工編號: {}",
                                retryCount, attributeName, ne.getMessage(), employeeNo);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("重試等待被中斷, 員工編號: {}", employeeNo, ie);
                break;
            } catch (Exception ge) {
                log.error("重試過程中發生未預期錯誤: {}, 員工編號: {}",
                        ge.getMessage(), employeeNo, ge);
                break;
            }
        }
    }

    // 使用 PowerShell 更新屬性
    private void updateAttributeWithPowerShell(String employeeNo, String attributeName, String attributeValue)
            throws Exception {
        log.info("嘗試使用 PowerShell 更新屬性 {}, 員工編號: {}", attributeName, employeeNo);

        StringBuilder powershellCmd = new StringBuilder();
        powershellCmd.append("Import-Module ActiveDirectory; ");

        // 根據不同的屬性類型選擇不同的 PowerShell 命令
        switch (attributeName) {
            case "mail":
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -EmailAddress '").append(attributeValue).append("'");
                break;
            case "telephoneNumber":
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -OfficePhone '").append(attributeValue).append("'");
                break;
            case "otherTelephone":
                // 分機號碼處理 - 先清除再添加
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -Clear otherTelephone; ");
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -Add @{otherTelephone='").append(attributeValue).append("'}");
                break;
            case "extension":
                // 使用 ipPhone 屬性來存儲分機號
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -Replace @{ipPhone='").append(attributeValue).append("'}");
                break;
            case "mobile":
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -MobilePhone '").append(attributeValue).append("'");
                break;
            case "displayName":
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -DisplayName '").append(attributeValue).append("'");
                break;
            case "title":
                powershellCmd.append("Set-ADUser ").append(employeeNo)
                        .append(" -Title '").append(attributeValue).append("'");
                break;
            default:
                throw new IllegalArgumentException("不支持通過 PowerShell 更新的屬性類型: " + attributeName);
        }

        String result = executePowerShellCommand(powershellCmd.toString());
        if (result != null && !result.trim().isEmpty()) {
            log.debug("PowerShell 執行結果: {}", result);
        }

        log.info("成功使用 PowerShell 更新屬性 {}, 員工編號: {}", attributeName, employeeNo);
    }

    /**
     * 檢查員工啟用狀態是否變更
     */
    private boolean isEmployedStatusChanged(Map<String, String> updatedFields) {
        return updatedFields != null && updatedFields.containsKey("employedStatus");
    }

    /**
     * 檢查組織是否變更
     */
    private boolean isOrganizationChanged(Map<String, String> updatedFields) {
        return updatedFields != null && updatedFields.containsKey("formulaOrgCode");
    }

    /**
     * 處理組織變更，將員工移動到新組織
     */
    private void handleOrganizationChange(LdapContext ctx, APIEmployeeInfo employee, String currentDn,
            String newOrgCode) {
        log.info("員工組織變更, 從原組織移動到新組織, 員工編號: {}, 新組織代碼: {}",
                employee.getEmployeeNo(), newOrgCode);

        try {
            // 查找新組織的 DN
            SearchResult newOrgResult = findOrganizationByOrgCode(ctx, newOrgCode);
            if (newOrgResult == null) {
                log.error("找不到新組織, 無法移動員工, 組織代碼: {}", newOrgCode);
                return;
            }

            // 獲取新組織的 DN
            String newOrgDn = newOrgResult.getNameInNamespace();
            log.info("找到新組織 DN: {}", newOrgDn);

            // 構建員工在新組織中的 DN
            String employeeCN = "CN=" + employee.getEmployeeNo();
            String newEmployeeDn = employeeCN + "," + newOrgDn;

            // 如果舊 DN 和新 DN 相同，不需要移動
            if (currentDn.equals(newEmployeeDn)) {
                log.info("員工已在目標組織中，無需移動");
                return;
            }

            // 移動員工到新組織
            log.info("開始移動員工, 從 {} 到 {}", currentDn, newEmployeeDn);
            ctx.rename(new LdapName(currentDn), new LdapName(newEmployeeDn));
            log.info("成功移動員工到新組織");

        } catch (NamingException e) {
            log.error("移動員工到新組織時發生錯誤: {}", e.getMessage(), e);
        }
    }

    private String mapFieldToLdapAttribute(String field) {
        if (field == null) {
            return null;
        }

        switch (field.toLowerCase()) {
            case "fullname":
                return "displayName";
            case "emailaddress":
                return "mail";
            case "officephone":
                return "telephoneNumber";
            case "extno":
                // 將分機號映射為 otherTelephone 而不是 extension 屬性
                return "otherTelephone"; // 使用 AD 中支持的屬性
            case "mobilephoneno":
                return "mobile";
            case "jobtitlename":
                return "title";
            // 添加對特定字段的處理，以避免顯示未知字段映射的警告
            case "datamodifieddate":
            case "datamodifieduser":
            case "employedstatus": // 已在單獨的邏輯中處理
            case "formulaorgcode": // 已在單獨的邏輯中處理
                return null; // 直接返回 null，不進行映射
            default:
                log.info("未知的字段映射: {}", field);
                return null;
        }
    }

    private void createEmployeeToDB(ADEmployeeSyncDto employeeData) {
        APIEmployeeInfo existingEmployee = employeeInfoRepository
                .findByEmployeeNo(employeeData.getEmployeeInfo().getEmployeeNo());
        if (existingEmployee != null) {
            log.info("員工已存在於數據庫: {}", employeeData.getEmployeeInfo().getEmployeeNo());
            return;
        }

        employeeInfoRepository.save(employeeData.getEmployeeInfo());
        employeeInfoActionLogRepository.save(
                new APIEmployeeInfoActionLog(employeeData.getEmployeeInfo().getEmployeeNo(), "C", "employeeNo", null,
                        null));

        log.info("成功創建員工: {}", employeeData.getEmployeeInfo().getEmployeeNo());
    }

    private void deleteEmployeeToDB(ADEmployeeSyncDto employeeData, LocalDate baseDate) {
        List<APIEmployeeInfoActionLog> apiEmployeeInfoActionLogs = employeeInfoActionLogRepository
                .findByEmployeeNoAndActionAndCreatedDate(employeeData.getEmployeeInfo().getEmployeeNo(), "D",
                        baseDate.toString());
        if (!apiEmployeeInfoActionLogs.isEmpty()) {
            log.info("刪除指令已存在於數據庫: {}", employeeData.getEmployeeInfo().getEmployeeNo());
            return;
        }

        APIEmployeeInfo existingEmployee = employeeInfoRepository
                .findByEmployeeNo(employeeData.getEmployeeInfo().getEmployeeNo());

        APIEmployeeInfo apiEmployeeInfo = employeeData.getEmployeeInfo();

        // 複製 apiEmployeeInfo 的資料到 existingEmployee
        existingEmployee.setFullName(apiEmployeeInfo.getFullName());
        existingEmployee.setEmailAddress(apiEmployeeInfo.getEmailAddress());
        existingEmployee.setOfficePhone(apiEmployeeInfo.getOfficePhone());
        existingEmployee.setExtNo(apiEmployeeInfo.getExtNo());
        existingEmployee.setMobilePhoneNo(apiEmployeeInfo.getMobilePhoneNo());
        existingEmployee.setJobTitleName(apiEmployeeInfo.getJobTitleName());
        existingEmployee.setFormulaOrgCode(apiEmployeeInfo.getFormulaOrgCode());
        existingEmployee.setEmployedStatus(apiEmployeeInfo.getEmployedStatus());
        existingEmployee.setDataModifiedDate(apiEmployeeInfo.getDataModifiedDate());
        existingEmployee.setDataModifiedUser(apiEmployeeInfo.getDataModifiedUser());

        employeeInfoRepository.save(existingEmployee);
        employeeInfoActionLogRepository.save(
                new APIEmployeeInfoActionLog(employeeData.getEmployeeInfo().getEmployeeNo(), "D", "employeeNo", null,
                        null));

        log.info("成功刪除(停用)員工: {}", employeeData.getEmployeeInfo().getEmployeeNo());
    }

    private void updateEmployeeToDB(ADEmployeeSyncDto employeeData) {
        APIEmployeeInfo existingEmployee = employeeInfoRepository
                .findByEmployeeNo(employeeData.getEmployeeInfo().getEmployeeNo());

        APIEmployeeInfo apiEmployeeInfo = employeeData.getEmployeeInfo();

        // 複製 apiEmployeeInfo 的資料到 existingEmployee
        existingEmployee.setFullName(apiEmployeeInfo.getFullName());
        existingEmployee.setEmailAddress(apiEmployeeInfo.getEmailAddress());
        existingEmployee.setOfficePhone(apiEmployeeInfo.getOfficePhone());
        existingEmployee.setExtNo(apiEmployeeInfo.getExtNo());
        existingEmployee.setMobilePhoneNo(apiEmployeeInfo.getMobilePhoneNo());
        existingEmployee.setJobTitleName(apiEmployeeInfo.getJobTitleName());
        existingEmployee.setFormulaOrgCode(apiEmployeeInfo.getFormulaOrgCode());
        existingEmployee.setEmployedStatus(apiEmployeeInfo.getEmployedStatus());
        existingEmployee.setDataModifiedDate(apiEmployeeInfo.getDataModifiedDate());
        existingEmployee.setDataModifiedUser(apiEmployeeInfo.getDataModifiedUser());

        employeeInfoRepository.save(existingEmployee);

        log.info("成功更新員工: {}", employeeData.getEmployeeInfo().getEmployeeNo());
    }

    /**
     * 啟用 AD 帳號
     */
    private void enableADAccount(LdapContext ctx, Name dn) throws NamingException {
        try {
            // 檢查當前帳號狀態
            Attributes attrs = ctx.getAttributes(dn, new String[] { "userAccountControl" });
            String currentUAC = attrs.get("userAccountControl").get().toString();
            log.info("當前帳號狀態 (UAC): {}", currentUAC);

            int uacValue = Integer.parseInt(currentUAC);
            int newUacValue = (uacValue & ~2) | 512; // 移除 "停用" 標記，添加 "正常帳戶" 標記
            log.info("新的 UAC 值: {}", newUacValue);

            // 更新 UAC 值
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("userAccountControl", Integer.toString(newUacValue)));
            ctx.modifyAttributes(dn, mods);
            log.info("成功更新 UAC 值");

            // 驗證最終帳號狀態
            attrs = ctx.getAttributes(dn, new String[] { "userAccountControl" });
            String finalUAC = attrs.get("userAccountControl").get().toString();
            log.info("最終帳號狀態 (UAC): {}", finalUAC);

            if (Integer.parseInt(finalUAC) == newUacValue) {
                log.info("成功啟用 LDAP 帳號: {}", dn);
            } else {
                log.warn("帳號可能未完全啟用，最終 userAccountControl 值: {}", finalUAC);
            }
        } catch (NamingException e) {
            log.error("啟用帳號時發生錯誤: {}, 錯誤: {}", dn, e.getMessage());
            throw e;
        }
    }

    private void disableADAccount(Name dn) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = getLdapContext();

            // 停用帳號
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("userAccountControl", "514"));
            ctx.modifyAttributes(dn, mods);

            log.info("成功停用 LDAP 帳號: {}", dn);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private void createEmployeeByPowerShell(Name dn, APIEmployeeInfo employee) throws Exception {
        log.info("開始建立 AD 帳號，使用 PowerShell 方式: {}", employee.getEmployeeNo());

        try {
            // 準備 OU 路徑 (需要反轉 DN 並移除 CN 部分)
            StringBuilder ouPathBuilder = new StringBuilder();
            List<String> dnParts = new ArrayList<>();

            // 將 LdapName 轉換為列表並反轉順序
            for (int i = 0; i < dn.size(); i++) {
                String part = dn.get(i);
                if (part.startsWith("OU=") || part.startsWith("ou=")) {
                    dnParts.add(part);
                }
            }

            // 反轉 OU 順序（從頂層 OU 到底層 OU）
            for (int i = dnParts.size() - 1; i >= 0; i--) {
                ouPathBuilder.append(dnParts.get(i));
                if (i > 0) {
                    ouPathBuilder.append(",");
                }
            }

            // 添加 DC 部分
            String[] baseDnParts = ldapBaseDn.split(",");
            for (String part : baseDnParts) {
                if (part.startsWith("DC=") || part.startsWith("dc=")) {
                    if (ouPathBuilder.length() > 0) {
                        ouPathBuilder.append(",");
                    }
                    ouPathBuilder.append(part);
                }
            }

            String ouPath = ouPathBuilder.toString();
            log.debug("建立的 OU 路徑: {}", ouPath);

            // 確保 OU 結構存在
            ensureOUStructurePowerShell(ouPath, employee);

            // 拼接 PowerShell 指令
            StringBuilder powershellCmd = new StringBuilder();

            // 匯入 AD 模組
            powershellCmd.append("Import-Module ActiveDirectory; ");

            // 建立使用者
            powershellCmd.append("New-ADUser ");
            powershellCmd.append("-Name '").append(employee.getEmployeeNo()).append("' ");
            powershellCmd.append("-SamAccountName '").append(employee.getEmployeeNo()).append("' ");

            // 添加基本屬性
            if (employee.getFullName() != null && !employee.getFullName().trim().isEmpty()) {
                powershellCmd.append("-DisplayName '").append(employee.getFullName()).append("' ");
            }

            powershellCmd.append("-UserPrincipalName '").append(employee.getEmployeeNo()).append("@sogo.com.tw' ");

            // 設定 OU 路徑
            powershellCmd.append("-Path '").append(ouPath).append("' ");

            // 設定其他屬性
            powershellCmd.append("-EmailAddress '").append(employee.getEmployeeNo()).append("@sogo.com.tw' ");

            if (employee.getOfficePhone() != null && !employee.getOfficePhone().trim().isEmpty()) {
                powershellCmd.append("-OfficePhone '").append(employee.getOfficePhone()).append("' ");
            }

            if (employee.getMobilePhoneNo() != null && !employee.getMobilePhoneNo().trim().isEmpty()) {
                powershellCmd.append("-MobilePhone '").append(employee.getMobilePhoneNo()).append("' ");
            }

            if (employee.getJobTitleName() != null && !employee.getJobTitleName().trim().isEmpty()) {
                powershellCmd.append("-Title '").append(employee.getJobTitleName()).append("' ");
            }

            // 添加分機號碼設定 (otherTelephone)
            if (employee.getExtNo() != null && !employee.getExtNo().trim().isEmpty()) {
                powershellCmd.append("-OtherAttributes @{otherTelephone='").append(employee.getExtNo()).append("'} ");
            }

            // 設定密碼和啟用帳號
            String password = "Sogo$" + employee.getIdNoSuffix();
            powershellCmd.append("-AccountPassword (ConvertTo-SecureString -AsPlainText '").append(password)
                    .append("' -Force) ");
            powershellCmd.append("-Enabled $true ");
            powershellCmd.append("-PasswordNeverExpires $false ");
            powershellCmd.append("-CannotChangePassword $false; ");

            // 添加 proxyAddresses
            powershellCmd.append("Set-ADUser ").append(employee.getEmployeeNo()).append(" ");
            powershellCmd.append("-Add @{ProxyAddresses=@(");
            powershellCmd.append("'SMTP:").append(employee.getEmployeeNo()).append("@sogo.com.tw', ");
            powershellCmd.append("'smtp:").append(employee.getEmployeeNo()).append("@mail.sogo.com.tw', ");
            powershellCmd.append("'smtp:").append(employee.getEmployeeNo()).append("@gardencity.com.tw'");
            powershellCmd.append(")}");

            // 執行 PowerShell 指令
            String result = executePowerShellCommand(powershellCmd.toString());
            log.info("PowerShell 執行結果: {}", result);

            log.info("成功建立 AD 帳號: {}", employee.getEmployeeNo());
        } catch (Exception e) {
            log.error("建立 AD 帳號失敗: {}, 錯誤: {}", employee.getEmployeeNo(), e.getMessage());
            throw new Exception("建立 AD 帳號失敗: " + e.getMessage(), e);
        }

    }

    private void ensureOUStructurePowerShell(String ouPath, APIEmployeeInfo employee) throws Exception {
        log.debug("開始確保 OU 結構存在（PowerShell）: {}", ouPath);

        try {
            // 解析 OU 路徑
            String[] ouParts = ouPath.split(",");

            // 找出所有 DC 部分，構建域基本路徑
            StringBuilder domainPath = new StringBuilder();
            for (String part : ouParts) {
                if (part.startsWith("DC=") || part.startsWith("dc=")) {
                    if (domainPath.length() > 0) {
                        domainPath.append(",");
                    }
                    domainPath.append(part);
                }
            }

            // 將 OU 部分按照從上到下的層次排序
            List<String> ouPartsList = new ArrayList<>();
            for (String part : ouParts) {
                if (part.startsWith("OU=") || part.startsWith("ou=")) {
                    ouPartsList.add(part);
                }
            }

            // 逐層建立 OU 結構
            String currentPath = domainPath.toString(); // 從域根開始

            // 從最外層 OU 開始，依次建立內層 OU
            for (int i = ouPartsList.size() - 1; i >= 0; i--) {
                String ouPart = ouPartsList.get(i);
                String ouName = ouPart.substring(3); // 移除 "OU=" 前綴

                // 構建當前 OU 的完整路徑
                String fullOuPath = ouPart + "," + currentPath;

                // 檢查此 OU 是否存在
                String ouCheckCmd = String.format(
                        "Import-Module ActiveDirectory; " +
                                "if (Get-ADOrganizationalUnit -Filter {DistinguishedName -eq '%s'} -ErrorAction SilentlyContinue) { "
                                +
                                "    Write-Output 'EXISTS' " +
                                "} else { " +
                                "    Write-Output 'NOT_EXISTS' " +
                                "}",
                        fullOuPath);

                String result = executePowerShellCommand(ouCheckCmd);
                log.debug("檢查 OU 存在結果: {}, {}", fullOuPath, result);

                // 如果不存在，則建立
                if (result.contains("NOT_EXISTS")) {
                    // 檢查是否是最內層 OU (即索引為0的OU)，如果是則添加描述
                    boolean isInnerMostOu = (i == 0);

                    StringBuilder createOuCmd = new StringBuilder();
                    createOuCmd.append("Import-Module ActiveDirectory; ");
                    createOuCmd.append("New-ADOrganizationalUnit -Name '").append(ouName).append("' ");
                    createOuCmd.append("-Path '").append(currentPath).append("' ");

                    // 如果是最內層 OU，添加組織代碼描述
                    if (isInnerMostOu && employee != null && employee.getFormulaOrgCode() != null) {
                        createOuCmd.append("-Description 'orgCode=").append(employee.getFormulaOrgCode()).append("' ");
                    }

                    createOuCmd.append("-ProtectedFromAccidentalDeletion $false");

                    String createResult = executePowerShellCommand(createOuCmd.toString());
                    log.info("建立 OU 結果: {}, {}", ouName, createResult);
                }

                // 更新當前路徑，為下一層 OU 的建立做準備
                currentPath = fullOuPath;
            }
        } catch (Exception e) {
            log.error("確保 OU 結構時發生錯誤: {}", e.getMessage());
            throw new Exception("確保 OU 結構失敗: " + e.getMessage(), e);
        }

    }

    /**
     * 執行 PowerShell 命令並返回結果
     */
    private String executePowerShellCommand(String command) throws Exception {
        log.debug("執行 PowerShell 命令: {}", command);

        try {
            // 建立 PowerShell 執行程序
            String[] commandArgs = {
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", command
            };

            ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // 讀取輸出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            // 等待程序完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("PowerShell 命令執行結束，退出碼: {}", exitCode);
            }

            return output.toString().trim();
        } catch (IOException | InterruptedException e) {
            log.error("執行 PowerShell 命令失敗: {}", e.getMessage());
            throw new Exception("執行 PowerShell 命令失敗: " + e.getMessage(), e);
        }
    }

    @Override
    public void syncOrganizationToAD(ADOrganizationSyncDto organizationData) throws Exception {
        log.info("開始同步組織到 AD, 組織代碼: {}", organizationData.getOrgCode());
        APIOrganization organization = organizationData.getOrganization();
        String action = organizationData.getAction();
        List<OrganizationHierarchyDto> orgHierarchy = organizationData.getOrgHierarchyDto();

        LdapContext ctx = null;
        try {
            ctx = getLdapContext();

            // 檢查組織是否已存在
            SearchResult existingOrg = findOrganizationByOrgCode(ctx, organization.getOrgCode());

            switch (action) {
                case "C":
                    log.debug("創建新組織: {}", organization.getOrgCode());
                    if (existingOrg != null) {
                        log.info("組織已存在於 AD 中，OrgCode: {}, DN: {}",
                                organization.getOrgCode(), existingOrg.getNameInNamespace());
                        // 如果需要，可以更新現有組織屬性
                        updateOrganizationAttributes(ctx, new LdapName(existingOrg.getNameInNamespace()), organization);
                    } else {
                        // 創建組織結構
                        Name orgDn = buildOrganizationDn(organization.getOrgCode(), orgHierarchy);
                        log.info("將建立組織的 DN: {}", orgDn.toString());
                        createOrganizationToAD(ctx, orgDn, organization, orgHierarchy);
                    }
                    break;
                case "U":
                    log.debug("更新組織資訊: {}", organization.getOrgCode());
                    if (existingOrg != null) {
                        // 更新現有組織
                        updateOrganization(ctx, new LdapName(existingOrg.getNameInNamespace()),
                                organization, organizationData);
                    } else {
                        log.warn("找不到要更新的組織: {}", organization.getOrgCode());
                        // 如果找不到，可以選擇創建
                        // Name orgDn = buildOrganizationDn(organization.getOrgCode(), orgHierarchy);
                        // log.info("組織不存在，將建立新組織，DN: {}", orgDn.toString());
                        // createOrganizationToAD(ctx, orgDn, organization, orgHierarchy);
                    }
                    break;
                case "D":
                    log.debug("停用組織: {}", organization.getOrgCode());
                    if (existingOrg != null) {
                        // 停用現有組織
                        disableOrganization(ctx, new LdapName(existingOrg.getNameInNamespace()), organization);
                    } else {
                        log.warn("找不到要停用的組織: {}", organization.getOrgCode());
                    }
                    break;
                default:
                    log.error("未知的操作類型: {}", action);
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
            log.info("完成同步組織資訊到 LDAP, 組織代碼: {}", organizationData.getOrgCode());
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    log.error("關閉 LDAP 連接時發生錯誤", e);
                }
            }
        }
    }

    /**
     * 建構組織的 LDAP DN
     *
     * @param orgCode      組織代碼
     * @param orgHierarchy 組織層級資訊
     * @return 組織的 LDAP DN
     */
    private Name buildOrganizationDn(String orgCode, List<OrganizationHierarchyDto> orgHierarchy) {
        LdapName baseDn = LdapNameBuilder.newInstance(ldapBaseDn).build();
        LdapNameBuilder builder = LdapNameBuilder.newInstance();

        // 排序組織層級，從最高層級（數字最小）到最低層級（數字最大）
        List<OrganizationHierarchyDto> sortedOrgHierarchy = orgHierarchy.stream()
                .sorted(Comparator.comparingInt(OrganizationHierarchyDto::getOrgLevel))
                .collect(Collectors.toList());

        // 建構 OU 部分
        for (OrganizationHierarchyDto org : sortedOrgHierarchy) {
            builder.add("OU", org.getOrgName());
        }

        // 合併 baseDn 和建構的 DN
        return LdapNameBuilder.newInstance(baseDn)
                .add(builder.build())
                .build();
    }

    /**
     * 創建組織到 AD
     */
    private void createOrganizationToAD(LdapContext ctx, Name dn, APIOrganization organization,
            List<OrganizationHierarchyDto> orgHierarchy) throws NamingException {
        log.debug("開始創建組織到 AD: {}", organization.getOrgCode());

        // 確保所有父 OU 存在
        ensureOUStructure(ctx, dn, organization);

        // 檢查組織是否已經被 ensureOUStructure 創建
        try {
            ctx.lookup(dn);
            log.info("組織已存在，只更新屬性: {}", dn);

            // 更新屬性
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("description", "orgCode=" + organization.getOrgCode()));
            ctx.modifyAttributes(dn, mods);

            log.info("成功更新組織屬性: {}", dn);
        } catch (NameNotFoundException e) {
            // 組織不存在，創建它
            // 創建組織屬性
            Attributes attrs = new BasicAttributes();
            Attribute objectClass = new BasicAttribute("objectClass");
            objectClass.add("top");
            objectClass.add("organizationalUnit");
            attrs.put(objectClass);

            // 添加組織名稱
            attrs.put("ou", organization.getOrgName());

            // 添加描述屬性，存儲 orgCode 以便後續查詢
            attrs.put("description", "orgCode=" + organization.getOrgCode());

            // 創建組織單位
            ctx.createSubcontext(dn, attrs);
            log.info("成功創建組織: {}", dn);
        }
    }

    /**
     * 更新組織屬性
     */
    private void updateOrganizationAttributes(LdapContext ctx, Name dn, APIOrganization organization)
            throws NamingException {
        log.debug("更新組織屬性: {}", dn);

        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                new BasicAttribute("description", "orgCode=" + organization.getOrgCode()));

        ctx.modifyAttributes(dn, mods);
        log.info("成功更新組織屬性: {}", dn);
    }

    /**
     * 確保 OU 結構存在（父級 OU）
     */
    private void ensureOUStructure(LdapContext ctx, Name dn, APIOrganization organization)
            throws NamingException {
        log.debug("開始確保 OU 結構: {}", dn);

        // 獲取 OU 路徑的各部分
        List<String> ouList = new ArrayList<>();
        for (int i = 0; i < dn.size(); i++) {
            String rdn = dn.get(i);
            if (rdn.startsWith("OU=") || rdn.startsWith("ou=")) {
                ouList.add(rdn.substring(3));
            }
        }
        log.debug("OU 列表: {}", ouList);

        // 因為 buildOrganizationDn 是從最高層級到最低層級排序，
        // 但 DN 格式是從最低層級到最高層級，所以 ouList 實際上是從最低層級到最高層級
        // 我們需要從最高層級開始建立，所以需要反向遍歷 ouList
        Name currentDn = new LdapName(ldapBaseDn);
        for (int i = ouList.size() - 1; i >= 0; i--) {
            String ou = ouList.get(i);
            currentDn.add("OU=" + ou);

            try {
                log.debug("嘗試查找 OU: {}", currentDn);
                ctx.lookup(currentDn);
                log.debug("OU 已存在: {}", currentDn);
            } catch (NameNotFoundException e) {
                log.debug("OU 不存在，創建: {}", currentDn);

                // 創建 OU
                Attributes ouAttrs = new BasicAttributes();
                Attribute ouObjectClass = new BasicAttribute("objectClass");
                ouObjectClass.add("top");
                ouObjectClass.add("organizationalUnit");
                ouAttrs.put(ouObjectClass);
                ouAttrs.put("ou", ou);

                // 只有最後一級的 OU 才添加 orgCode 描述，父層級 OU 不添加
                if (i == 0) {
                    ouAttrs.put("description", "orgCode=" + organization.getOrgCode());
                }

                ctx.createSubcontext(currentDn, ouAttrs);
                log.info("成功創建組織單位: {}", currentDn);
            }
        }
    }

    /**
     * 更新組織
     */
    private void updateOrganization(LdapContext ctx, Name dn, APIOrganization organization,
            ADOrganizationSyncDto organizationData) throws NamingException {
        Map<String, String> updatedFields = organizationData.getUpdatedFields();
        log.debug("更新組織: {}, 更新欄位: {}", organization.getOrgCode(), updatedFields);

        if (updatedFields == null || updatedFields.isEmpty()) {
            log.info("沒有需要更新的欄位");
            return;
        }

        // 準備修改項
        List<ModificationItem> modItems = new ArrayList<>();

        // 處理組織架構與名稱變更
        Name currentDn = dn;
        boolean needRename = false;
        Name newDn = currentDn;

        // 檢查組織層級變更
        if (organizationData.getOrgHierarchyDto() != null && !organizationData.getOrgHierarchyDto().isEmpty()) {
            try {
                // 根據現有 DN 分析當前組織架構
                String currentDnStr = currentDn.toString();

                // 構建新的 DN，基於 organizationData.getOrgHierarchyDto()
                newDn = buildDnFromHierarchy(organizationData.getOrgHierarchyDto(), organization.getOrgName());
                String newDnStr = newDn.toString();

                // 比較當前 DN 和新 DN
                if (!currentDnStr.equals(newDnStr)) {
                    log.info("發現組織架構變更，需要搬移 OU。從: {} 到: {}", currentDnStr, newDnStr);
                    needRename = true;
                }
            } catch (Exception e) {
                log.error("分析組織架構變更時發生錯誤", e);
            }
        }

        // 檢查組織名稱變更
        String orgNameValue = null;
        for (Map.Entry<String, String> entry : updatedFields.entrySet()) {
            if ("orgname".equalsIgnoreCase(entry.getKey())) {
                orgNameValue = entry.getValue();
                break;
            }
        }

        // 如果只有名稱變更，但沒有層級變更
        if (orgNameValue != null && !needRename) {
            log.debug("需要更新組織名稱為: {}", orgNameValue);
            newDn = getNewDnWithOUName(dn, orgNameValue);
            needRename = true;
        }

        // 執行重命名/移動操作
        if (needRename) {
            try {
                ctx.rename(currentDn, newDn);
                log.info("成功重命名/搬移組織: {} -> {}", currentDn, newDn);
                currentDn = newDn; // 更新當前 DN 為新的 DN
            } catch (NamingException e) {
                log.error("重命名/搬移組織時發生錯誤: {}", e.getMessage(), e);
                throw e;
            }
        }

        // 確保 orgCode 在描述中正確設置
        modItems.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                new BasicAttribute("description", "orgCode=" + organization.getOrgCode())));

        // 如果有屬性需要更新
        if (!modItems.isEmpty()) {
            ModificationItem[] mods = modItems.toArray(new ModificationItem[0]);
            try {
                ctx.modifyAttributes(currentDn, mods); // 注意：使用更新後的 currentDn
                log.info("成功更新組織屬性: {}", currentDn);
            } catch (NamingException e) {
                log.error("更新組織屬性時發生錯誤: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * 根據新的 OU 名稱獲取新的 DN
     */
    private Name getNewDnWithOUName(Name oldDn, String newOUName) throws NamingException {
        // 複製原始 DN，除了最後一個 RDN (即當前 OU)
        Name parentDn = new LdapName(oldDn.toString()).getPrefix(oldDn.size() - 1);

        // 創建新的 DN，添加帶有新名稱的 OU
        return LdapNameBuilder.newInstance(parentDn)
                .add("OU", newOUName)
                .build();
    }

    /**
     * 根據組織層級資料構建 LDAP DN
     * 
     * @param hierarchyDtoList 組織層級DTO列表
     * @param orgName          組織名稱
     * @return 構建的 DN
     * @throws NamingException 命名異常
     */
    private Name buildDnFromHierarchy(List<OrganizationHierarchyDto> hierarchyDtoList, String orgName)
            throws NamingException {
        // 先排序層級，確保由上而下的順序
        Collections.sort(hierarchyDtoList, new Comparator<OrganizationHierarchyDto>() {
            @Override
            public int compare(OrganizationHierarchyDto o1, OrganizationHierarchyDto o2) {
                // 使用 orgLevel 欄位進行排序
                return Integer.compare(o1.getOrgLevel(), o2.getOrgLevel());
            }
        });

        // 建立基於 baseDn 的 DN 構建器
        LdapName baseDn = new LdapName(ldapBaseDn);
        LdapNameBuilder builder = LdapNameBuilder.newInstance(baseDn);

        // 從最高層級（數字最小）到最低層級（數字最大）添加 OU
        for (int i = hierarchyDtoList.size() - 1; i > 0; i--) {

            OrganizationHierarchyDto hierarchyDto = hierarchyDtoList.get(i);
            if (hierarchyDto.getOrgCode() != null && !hierarchyDto.getOrgCode().isEmpty()) {
                builder.add("OU", hierarchyDto.getOrgName());
            }
        }

        // 最後添加當前組織作為最底層 OU
        builder.add("OU", orgName);

        Name result = builder.build();
        log.debug("buildDnFromHierarchy 構建的 DN: {}", result.toString());
        return result;
    }

    /**
     * 禁用組織
     */
    private void disableOrganization(LdapContext ctx, Name dn, APIOrganization organization)
            throws NamingException {
        log.debug("禁用組織: {}", organization.getOrgCode());

        // 檢查是否有子物件
        boolean hasChildren = checkForChildren(ctx, dn);

        if (hasChildren) {
            // 如果有子物件，僅標記為禁用狀態（更新描述）
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("description", "[DISABLED] orgCode=" + organization.getOrgCode()));

            ctx.modifyAttributes(dn, mods);
            log.info("組織有子物件，已標記為禁用狀態: {}", dn);
        } else {
            // 如果沒有子物件，可以刪除組織
            ctx.destroySubcontext(dn);
            log.info("成功刪除組織: {}", dn);
        }
    }

    /**
     * 檢查 OU 下是否有子物件
     */
    private boolean checkForChildren(LdapContext ctx, Name dn) throws NamingException {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        searchControls.setCountLimit(1); // 只需要找到一個就確認有子物件了

        NamingEnumeration<SearchResult> results = ctx.search(dn, "(objectClass=*)", searchControls);
        boolean hasChildren = results.hasMoreElements();
        results.close();

        return hasChildren;
    }

    /**
     * 根據描述中的 orgCode 查找組織
     */
    private SearchResult findOrganizationByOrgCode(LdapContext ctx, String orgCode) throws NamingException {
        log.debug("根據 orgCode 尋找組織: {}", orgCode);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        // 精確匹配 orgCode
        String searchFilter = "(description=*orgCode=" + orgCode + "*)";
        log.debug("搜索過濾器: {}", searchFilter);

        NamingEnumeration<SearchResult> results = ctx.search(ldapBaseDn, searchFilter, searchControls);

        if (results.hasMoreElements()) {
            SearchResult result = results.next();
            log.info("找到組織 DN: {}", result.getNameInNamespace());

            // 檢查是否找到多個結果
            if (results.hasMoreElements()) {
                log.warn("發現多個具有相同 orgCode 的組織: {}", orgCode);
                while (results.hasMoreElements()) {
                    log.warn("額外的組織 DN: {}", results.next().getNameInNamespace());
                }
            }

            results.close();
            return result;
        }

        log.debug("未找到 orgCode 為 {} 的組織", orgCode);
        results.close();
        return null;
    }

}