package com.sogo.ad.midd.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
    public void syncEmployeeToAD(ADEmployeeSyncDto employeeData) throws Exception {
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
                createEmployeeToDB(employeeData);
                // createEmployeeToLDAP(dn, employee);
                createEmployeeByPowerShell(dn, employee);
                break;
            case "U":
                log.debug("更新員工資訊: {}", employee.getEmployeeNo());
                ctx = getLdapContext();
                updateEmployee(ctx, employee, employeeData.getUpdatedFields());
                break;
            case "D":
                log.debug("停用員工: {}", employee.getEmployeeNo());
                disableADAccount(dn);
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

                    // 處理啟用狀態變更 - 如果 employedStatus 從非 1 變為 1，需要啟用帳號
                    if (employedStatusChanged) {
                        String newStatus = updatedFields.get("employedStatus");
                        if ("1".equals(newStatus)) {
                            log.info("員工狀態變更為啟用，啟用 AD 帳號: {}", employee.getEmployeeNo());
                            enableADAccount(ctx, new LdapName(dn));

                            // 重新啟用 Azure AD E1 帳號
                            try {
                                

                                log.info("重新啟用員工 Azure AD E1 帳號: {}", employee.getEmployeeNo());
                                azureADService.enableAADE1AccountProcessor(employee.getEmailAddress(),
                                        employee.getIdNoSuffix());
                            } catch (Exception e) {
                                log.error("重新啟用 Azure AD E1 帳號失敗: {}", e.getMessage(), e);
                            }
                        } else if ("2".equals(newStatus)) {
                            log.info("員工狀態變更為留停，停用 AD 帳號: {}", employee.getEmployeeNo());
                            disableADAccount(new LdapName(dn));
                        }
                    }

                    // 處理一般屬性更新 (原有邏輯)
                    for (Map.Entry<String, String> entry : updatedFields.entrySet()) {
                        String key = entry.getKey();
                        // 跳過 formulaOrgCode 和 employedStatus，因為已單獨處理
                        if ("formulaOrgCode".equals(key) || "employedStatus".equals(key)) {
                            continue;
                        }

                        String ldapAttribute = mapFieldToLdapAttribute(key);
                        String value = entry.getValue();

                        if (ldapAttribute != null && value != null && !value.trim().isEmpty()) {
                            ModificationItem[] mods = new ModificationItem[] {
                                    new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                            new BasicAttribute(ldapAttribute, value.trim()))
                            };

                            try {
                                ctx.modifyAttributes(dn, mods);
                                log.info("成功更新屬性: {} = {}", ldapAttribute, value.trim());
                            } catch (NamingException e) {
                                log.error("更新屬性 {} 時發生錯誤: {}", ldapAttribute, e.getMessage());
                            }
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
        switch (field.toLowerCase()) {
            case "fullname":
                return "displayName";
            case "emailaddress":
                return "mail";
            case "officephone":
                return "telephoneNumber";
            case "extno":
                return "extension";
            case "mobilephoneno":
                return "mobile";
            case "jobtitlename":
                return "title";
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
                                organization, organizationData.getUpdatedFields());
                    } else {
                        log.warn("找不到要更新的組織: {}", organization.getOrgCode());
                        // 如果找不到，可以選擇創建
                        Name orgDn = buildOrganizationDn(organization.getOrgCode(), orgHierarchy);
                        log.info("組織不存在，將建立新組織，DN: {}", orgDn.toString());
                        createOrganizationToAD(ctx, orgDn, organization, orgHierarchy);
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
     * @param orgCode     組織代碼
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

        // 從基礎 DN 開始，確保每一級 OU 都存在
        Name currentDn = new LdapName(ldapBaseDn);
        // 由於 buildOrganizationDn 現在是從最高層級到最低層級排序，
        // 所以這裡應該從第一個元素開始處理（從 0 到 ouList.size() - 1）
        for (int i = 0; i < ouList.size(); i++) {
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
                if (i == ouList.size() - 1) {
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
            Map<String, String> updatedFields) throws NamingException {
        log.debug("更新組織: {}, 更新欄位: {}", organization.getOrgCode(), updatedFields);

        if (updatedFields == null || updatedFields.isEmpty()) {
            log.info("沒有需要更新的欄位");
            return;
        }

        // 準備修改項
        List<ModificationItem> modItems = new ArrayList<>();

        // 處理組織名稱變更 (特殊處理，需要重命名 OU)
        if (updatedFields.containsKey("OrgName")) {
            String newName = updatedFields.get("OrgName");
            log.debug("需要更新組織名稱為: {}", newName);

            // 變更 OU 的 RDN
            Name newDn = getNewDnWithOUName(dn, newName);
            ctx.rename(dn, newDn);
            log.info("成功重命名組織: {} -> {}", dn, newDn);

            // 更新 dn 引用，指向新的 DN
            dn = newDn;
        }

        // 處理描述變更
        if (updatedFields.containsKey("Remark")) {
            String newRemark = updatedFields.get("Remark");
            modItems.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("description", "orgCode=" + organization.getOrgCode() +
                            ", " + newRemark)));
        } else {
            // 確保 orgCode 在描述中正確設置
            modItems.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("description", "orgCode=" + organization.getOrgCode())));
        }

        // 處理其他屬性...

        // 如果有屬性需要更新
        if (!modItems.isEmpty()) {
            ModificationItem[] mods = modItems.toArray(new ModificationItem[0]);
            ctx.modifyAttributes(dn, mods);
            log.info("成功更新組織屬性: {}", dn);
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