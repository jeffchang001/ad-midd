package com.sogo.ad.midd.service.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.sogo.ad.midd.model.dto.ADSyncDto;
import com.sogo.ad.midd.model.dto.OrganizationHierarchyDto;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.service.ADLDAPSyncService;

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

    @Override
    public void syncEmployeeToLDAP(ADSyncDto employeeData) throws Exception {
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
                createEmployee(dn, employee);
                break;
            case "U":
                log.debug("更新員工資訊: {}", employee.getEmployeeNo());
                ctx = getLdapContext();
                // updateEmployee(ctx, employee, employeeData.getUpdatedFields());
                break;
            case "D":
                log.debug("停用員工: {}", employee.getEmployeeNo());
                // disableEmployee(dn);
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

    @Override
    public void syncOrganizationToLDAP(ADSyncDto organizationData) {
        // List<OrganizationHierarchyDto> orgHierarchy =
        // organizationData.getOrgHierarchyDto();
        // for (OrganizationHierarchyDto org : orgHierarchy) {
        // Name dn = buildOrganizationDn(org.getOrgCode());
        // updateOrganization(dn, org);
        // }
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
            String[] returnedAtts = { "cn", "displayName", "mail", "telephoneNumber", "mobile", "title" };
            searchControls.setReturningAttributes(returnedAtts);

            String searchFilter = String.format("(&(objectClass=person)(cn=%s))", employee.getEmployeeNo());
            log.debug("LDAP 搜索過濾器: {}", searchFilter);

            do {
                NamingEnumeration<SearchResult> results = ctx.search(ldapBaseDn, searchFilter, searchControls);

                while (results != null && results.hasMoreElements()) {
                    SearchResult searchResult = results.next();
                    String dn = searchResult.getNameInNamespace();
                    log.debug("找到員工 DN: {}", dn);

                    for (Map.Entry<String, String> entry : updatedFields.entrySet()) {
                        String ldapAttribute = mapFieldToLdapAttribute(entry.getKey());
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
            // case "datamodifieddate": 2023版本AD才有
            // return "whenChanged";
            default:
                log.warn("未知的字段映射: {}", field);
                return null;
        }
    }

    private void createEmployee(Name dn, APIEmployeeInfo employee) throws Exception {
        LdapContext ctx = null;
        try {
            ctx = getLdapContext();

            // 確保所有父 OU 存在
            ensureOUStructure(ctx, dn, employee);

            // 創建用戶屬性
            Attributes attrs = new BasicAttributes();
            Attribute objectClass = new BasicAttribute("objectClass");
            objectClass.add("top");
            objectClass.add("person");
            objectClass.add("organizationalPerson");
            objectClass.add("user");
            attrs.put(objectClass);

            // 添加其他屬性
            addAttributeIfNotNull(attrs, "cn", employee.getEmployeeNo());
            addAttributeIfNotNull(attrs, "sAMAccountName", employee.getEmployeeNo());
            addAttributeIfNotNull(attrs, "userPrincipalName", employee.getEmployeeNo() + "@sogo.com.tw");
            addAttributeIfNotNull(attrs, "displayName", employee.getFullName());
            addAttributeIfNotNull(attrs, "mail", employee.getEmployeeNo() + "@sogo.com.tw");
            addAttributeIfNotNull(attrs, "telephoneNumber", employee.getOfficePhone());
            addAttributeIfNotNull(attrs, "mobile", employee.getMobilePhoneNo());
            addAttributeIfNotNull(attrs, "title", employee.getJobTitleName());

            // 創建用戶
            ctx.createSubcontext(dn, attrs);
            log.info("成功創建 LDAP 帳號: {}", dn);

            // 添加 proxyAddresses
            addProxyAddresses(ctx, dn, employee);

            // 啟用帳號 TODO: 需要連線到 AAD 執行, 機制可能會不同
            // enableAccount(ctx, dn);

            // 設置密碼 TODO: 需要連線到 AAD 執行, 機制可能會不同
            // setUserPassword(ctx, dn, employee);

        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private void addAttributeIfNotNull(Attributes attrs, String attrName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            attrs.put(attrName, value.trim());
        }
    }

    private void ensureOUStructure(LdapContext ctx, Name dn, APIEmployeeInfo employee) throws NamingException {
        log.debug("開始確保 OU 結構: {}", dn);
        List<String> ouList = new ArrayList<>();
        for (int i = 0; i < dn.size(); i++) {
            String rdn = dn.get(i);
            if (rdn.startsWith("OU=") || rdn.startsWith("ou=")) {
                ouList.add(rdn.substring(3));
            }
        }
        log.debug("OU 列表: {}", ouList);

        Name currentDn = new LdapName(ldapBaseDn);
        for (int i = 0; i < ouList.size(); i++) {
            String ou = ouList.get(i);
            currentDn.add("OU=" + ou);
            try {
                log.debug("嘗試查找 OU: {}", currentDn);
                ctx.lookup(currentDn);
                log.debug("OU 已存在: {}", currentDn);
            } catch (NameNotFoundException e) {
                log.debug("OU 不存在，嘗試創建: {}", currentDn);
                Attributes ouAttrs = new BasicAttributes();
                Attribute ouObjectClass = new BasicAttribute("objectClass");
                ouObjectClass.add("top");
                ouObjectClass.add("organizationalUnit");
                ouAttrs.put(ouObjectClass);
                ouAttrs.put("ou", ou);

                // 添加 description 屬性, 只針對最後一個組織儲存組織代碼,
                if (i == ouList.size() - 1) {
                    ouAttrs.put("description", "orgCode=" + employee.getFormulaOrgCode());
                }

                try {
                    ctx.createSubcontext(currentDn, ouAttrs);
                    log.info("成功創建組織單位: {}", currentDn);
                } catch (NamingException ne) {
                    log.error("創建組織單位時發生錯誤: {}, 錯誤: {}", currentDn, ne.getMessage(), ne);
                    throw ne;
                }
            } catch (NamingException ne) {
                log.error("檢查 OU 結構時發生未知錯誤: {}, 錯誤: {}", currentDn, ne.getMessage(), ne);
                throw ne;
            }
        }
    }

    private void addProxyAddresses(LdapContext ctx, Name dn, APIEmployeeInfo employee) throws NamingException {
        ModificationItem[] proxyMods = new ModificationItem[1];
        Attribute proxyAddresses = new BasicAttribute("proxyAddresses");
        proxyAddresses.add("SMTP:" + employee.getEmployeeNo() + "@sogo.com.tw");
        proxyAddresses.add("smtp:" + employee.getEmployeeNo() + "@mail.sogo.com.tw");
        proxyAddresses.add("smtp:" + employee.getEmployeeNo() + "@gardencity.com.tw");
        proxyMods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, proxyAddresses);

        ctx.modifyAttributes(dn, proxyMods);
        log.info("成功新增 proxyAddresses 屬性: {}", dn);
    }

    private void enableAccount(LdapContext ctx, Name dn) throws NamingException {
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

    private void setUserPassword(LdapContext ctx, Name dn, APIEmployeeInfo employee) {
        // TODO: 需要請客戶確認 AD server 是否啟用 SSL/TLS 加密, LDAP需要進行驗證才能正常執行; 若改用powershell方式,
        // 可以不用 ssl, 但是部屬的時候要和該 AD 同網域, 不須登入驗證
        try {

            // 然後設置密碼 "Sogo$" + 身份證字號後四碼
            String newPassword = "Sogo$" + employee.getIdNoSuffix();

            // 密碼必須以 UTF-16LE 格式，並且包圍在雙引號內
            String quotedPassword = "\"" + newPassword + "\"";
            byte[] passwordBytes = quotedPassword.getBytes("UTF-16LE");

            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("unicodePwd", passwordBytes));

            ctx.modifyAttributes(dn, mods);
            log.info("成功設置新密碼");

            // 最後,如果需要,移除 PASSWD_NOTREQD 標誌
            ModificationItem[] uacMods = new ModificationItem[1];
            uacMods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("userAccountControl", "512")); // 512 (NORMAL_ACCOUNT)
            ctx.modifyAttributes(dn, uacMods);

        } catch (NamingException | UnsupportedEncodingException e) {
            log.error("設置密碼時發生錯誤: {}", e.getMessage());
        }
    }

    private void disableEmployee(Name dn) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = getLdapContext();

            // 停用帳號
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("userAccountControl", "514"));
            ctx.modifyAttributes(dn, mods);

            log.info("成功停用 LDAP 帳號: {}", dn);

            // 可選：移動到停用的 OU
            // Name newDn = LdapNameBuilder.newInstance(ldapBaseDn)
            // .add("OU", "Disabled Accounts")
            // .add(dn.get(dn.size() - 1))
            // .build();
            // ctx.rename(dn, newDn);
            // log.info("已將停用的帳號移動到 Disabled Accounts OU: {}", newDn);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private boolean isPasswordValid(String password) {
        // 檢查密碼長度
        if (password.length() < 8) {
            return false;
        }

        // 檢查是否包含大寫字母、小寫字母、數字和特殊字符
        boolean hasUppercase = !password.equals(password.toLowerCase());
        boolean hasLowercase = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecialChar = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar;
    }
}