package com.sogo.ad.midd.service.impl;

import java.io.IOException;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.naming.Context;
import javax.naming.Name;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.sogo.ad.midd.model.dto.ADSyncDto;
import com.sogo.ad.midd.model.dto.OrganizationHierarchyDto;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.service.ADLDAPSyncService;

@Service
public class ADLDAPSyncServiceImpl implements ADLDAPSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ADLDAPSyncServiceImpl.class);

    @Value("${ldap.url}")
    private String ldapUrl;

    @Value("${ldap.base-dn}")
    private String ldapBaseDn;

    @Value("${ldap.username}")
    private String ldapUsername;

    @Value("${ldap.password}")
    private String ldapPassword;

    @Value("${ldap.is.production.site}")
    private Boolean isProductionSite;

    @Override
    public void syncEmployeeToLDAP(ADSyncDto employeeData) throws Exception {
        logger.info("employeeNo: {}", employeeData.getEmployeeNo());
        APIEmployeeInfo employee = employeeData.getEmployeeInfo();
        String action = employeeData.getAction();
        List<OrganizationHierarchyDto> orgHierarchy = employeeData.getOrgHierarchyDto();

        Name dn = buildEmployeeDn(employee.getEmployeeNo(), orgHierarchy);
        logger.info("HR 組織樹的 DN: " + dn.toString());

        LdapContext ctx = null;
        switch (action) {
            case "C":
                // logger.debug("創建新員工: {}", employee.getEmployeeNo());
                // createEmployee(dn, employee);
                // break;
            case "U":
                logger.debug("更新員工資訊: {}", employee.getEmployeeNo());
                ctx = getLdapContext();
                updateEmployee(ctx, employee, employeeData.getUpdatedFields());
                break;
            case "D":
                logger.debug("停用員工: {}", employee.getEmployeeNo());
                disableEmployee(dn);
                break;
            default:
                logger.error("未知的操作類型: {}", action);
                throw new IllegalArgumentException("Unknown action: " + action);
        }
        logger.info("完成同步員工資訊到 LDAP, 員工編號: {}", employeeData.getEmployeeNo());
    }

    private LdapContext getLdapContext() throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, ldapUsername);
        env.put(Context.SECURITY_CREDENTIALS, ldapPassword);
        env.put(Context.REFERRAL, "ignore");

        return new InitialLdapContext(env, null);
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

        // 在這裡加入自定義的 OU
        if (isProductionSite == Boolean.FALSE) {
            builder.add("OU", "開發測試區");
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
            logger.debug("LDAP 搜索過濾器: {}", searchFilter);

            do {
                NamingEnumeration<SearchResult> results = ctx.search(ldapBaseDn, searchFilter, searchControls);

                while (results != null && results.hasMoreElements()) {
                    SearchResult searchResult = results.next();
                    String dn = searchResult.getNameInNamespace();
                    logger.debug("找到員工 DN: {}", dn);

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
                                logger.info("成功更新屬性: {} = {}", ldapAttribute, value.trim());
                            } catch (NamingException e) {
                                logger.error("更新屬性 {} 時發生錯誤: {}", ldapAttribute, e.getMessage());
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
            logger.error("LDAP 操作失敗: {}", e.getMessage());
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
                logger.warn("未知的字段映射: {}", field);
                return null;
        }
    }

    private void createEmployee(Name dn, APIEmployeeInfo employee) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = getLdapContext();

            Attributes attrs = new BasicAttributes();
            Attribute objectClass = new BasicAttribute("objectClass");
            objectClass.add("top");
            objectClass.add("person");
            objectClass.add("organizationalPerson");
            objectClass.add("user");
            attrs.put(objectClass);

            attrs.put("cn", employee.getEmployeeNo());
            attrs.put("sAMAccountName", employee.getEmployeeNo());
            attrs.put("displayName", employee.getFullName());
            attrs.put("givenName", employee.getFullName());
            attrs.put("mail", employee.getEmailAddress());
            attrs.put("telephoneNumber", employee.getOfficePhone());
            attrs.put("mobile", employee.getMobilePhoneNo());
            attrs.put("title", employee.getJobTitleName());

            // 設置預設密碼，建議使用隨機生成的強密碼
            String defaultPassword = generateRandomPassword();
            attrs.put("userPassword", defaultPassword);

            ctx.createSubcontext(dn, attrs);

            logger.info("成功創建 LDAP 帳號: {}", dn);

            // 啟用帳號
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("userAccountControl", "512"));
            ctx.modifyAttributes(dn, mods);

            logger.info("成功啟用 LDAP 帳號: {}", dn);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private String generateRandomPassword() {
        // TODO: 預設密碼policy為「Sogo$身份證後四碼」, 身分證的部分要等 Radar 改版後取得APIEmployeeInfo.IDNo
        return "TempPass" + System.currentTimeMillis();
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
            
            logger.info("成功停用 LDAP 帳號: {}", dn);
            
            // 可選：移動到停用的 OU
            // Name newDn = LdapNameBuilder.newInstance(ldapBaseDn)
            //     .add("OU", "Disabled Accounts")
            //     .add(dn.get(dn.size() - 1))
            //     .build();
            // ctx.rename(dn, newDn);
            // logger.info("已將停用的帳號移動到 Disabled Accounts OU: {}", newDn);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}