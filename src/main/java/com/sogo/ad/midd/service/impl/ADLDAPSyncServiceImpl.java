package com.sogo.ad.midd.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.sogo.ad.midd.model.dto.ADSyncDto;
import com.sogo.ad.midd.model.dto.OrganizationHierarchyDto;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.service.ADLDAPSyncService;

@Service
public class ADLDAPSyncServiceImpl implements ADLDAPSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ADLDAPSyncServiceImpl.class);

    @Autowired
    private LdapTemplate ldapTemplate;

    @Value("${ldap.is.production.site}")
    private Boolean isProductionSite;

    @Override
    public void syncEmployeeToLDAP(ADSyncDto employeeData) {
        logger.info("我用打字的可以嗎？: {}", employeeData.getEmployeeNo());
        APIEmployeeInfo employee = employeeData.getEmployeeInfo();
        String action = employeeData.getAction();
        List<OrganizationHierarchyDto> orgHierarchy = employeeData.getOrgHierarchyDto();

        Name dn = buildEmployeeDn(employee.getEmployeeNo(), orgHierarchy);
        logger.info("DN: "+dn.toString());

        switch (action) {
            case "C":
                logger.debug("創建新員工: {}", employee.getEmployeeNo());
                createEmployee(dn, employee);
                break;
            // case "U":
            // logger.debug("更新員工資訊: {}", employee.getEmployeeNo());
            // updateEmployee(dn, employee, employeeData.getUpdatedFields());
            // break;
            // case "D":
            // logger.debug("停用員工: {}", employee.getEmployeeNo());
            // disableEmployee(dn);
            // break;
            default:
                logger.error("未知的操作類型: {}", action);
                // throw new IllegalArgumentException("Unknown action: " + action);
        }
        logger.info("完成同步員工資訊到 LDAP, 員工編號: {}", employeeData.getEmployeeNo());
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
     * 取得 LDAP 的 Base DN
     * 
     * @return LDAP 的 Base DN
     */
    private String getBaseDn() {
        LdapContextSource contextSource = (LdapContextSource) ldapTemplate.getContextSource();
        return contextSource.getBaseLdapName().toString();
    }

    /**
     * 構建員工的 LDAP DN
     * 
     * @param employeeNo   員工編號
     * @param orgHierarchy 組織層級列表
     * @return 員工的 LDAP DN
     */
    private Name buildEmployeeDn(String employeeNo, List<OrganizationHierarchyDto> orgHierarchy) {
        LdapName baseDn = LdapNameBuilder.newInstance(getBaseDn()).build();
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

    // private Name buildOrganizationDn(String orgCode) {
    // return LdapNameBuilder.newInstance()
    // .add("ou", "groups")
    // .add("cn", orgCode)
    // .build();
    // }

    /**
     * 在 LDAP 中創建新員工
     * 
     * @param dn       員工的 LDAP DN
     * @param employee 員工資訊
     */
    private void createEmployee(Name dn, APIEmployeeInfo employee) {
        logger.debug("開始創建員工 LDAP 條目: {}", dn);
        Attributes attrs = new BasicAttributes();
        Attribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("person");
        objectClass.add("organizationalPerson");
        objectClass.add("user");
        attrs.put(objectClass);

        attrs.put("sAMAccountName", employee.getEmployeeNo());
        attrs.put("cn", employee.getFullName());
        attrs.put("givenName", employee.getFullName());
        attrs.put("sn", employee.getFullName());
        if (employee.getEmailAddress() != null && !employee.getEmailAddress().isEmpty()) {
            attrs.put("mail", employee.getEmailAddress());
        }
        // 添加其他必要的屬性...

        try {
            ldapTemplate.bind(dn, null, attrs);
            logger.debug("完成創建員工 LDAP 條目: {}", dn);
        } catch (Exception e) {
            logger.error("創建員工 LDAP 條目時發生錯誤: {}", dn, e);
            throw new RuntimeException("Failed to create employee in LDAP", e);
        }
    }

    /**
     * 更新 LDAP 中的員工資訊
     * 
     * @param dn            員工的 LDAP DN
     * @param employee      員工資訊
     * @param updatedFields 需要更新的欄位
     */
    private void updateEmployee(Name dn, APIEmployeeInfo employee, java.util.Map<String, String> updatedFields) {
        List<ModificationItem> mods = new ArrayList<>();

        if (updatedFields != null) {
            for (java.util.Map.Entry<String, String> entry : updatedFields.entrySet()) {
                mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                        new BasicAttribute(entry.getKey(), entry.getValue())));
            }
        }

        ldapTemplate.modifyAttributes(dn, mods.toArray(new ModificationItem[0]));
        logger.debug("完成更新員工 LDAP 條目: {}", dn);
    }

    /**
     * 從 LDAP 中停用員工
     * 
     * @param dn 員工的 LDAP DN
     */
    private void disableEmployee(Name dn) {
        logger.debug("開始停用員工 LDAP 帳號: {}", dn);

        try {
            // 使用 AttributesMapper 來獲取 userAccountControl 屬性
            Integer currentControl = ldapTemplate.lookup(dn, new AttributesMapper<Integer>() {
                @Override
                public Integer mapFromAttributes(Attributes attrs) throws NamingException {
                    Attribute userAccountControl = attrs.get("userAccountControl");
                    if (userAccountControl != null) {
                        return Integer.parseInt(userAccountControl.get().toString());
                    }
                    return null;
                }
            });

            int newControl;
            if (currentControl != null) {
                // 設置停用位元（第二個位元）
                newControl = currentControl | 0x2; // 0x2 是 ACCOUNTDISABLE 標誌
            } else {
                // 如果之前沒有設置，我們設置一個默認值並加上停用標誌
                newControl = 512 | 0x2; // 512 是正常帳戶的默認值
            }

            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    new BasicAttribute("userAccountControl", Integer.toString(newControl)));

            ldapTemplate.modifyAttributes(dn, mods);

            logger.info("成功停用員工 LDAP 帳號: {}", dn);
        } catch (Exception e) {
            logger.error("停用員工 LDAP 帳號時發生錯誤: {}", dn, e);
            throw new RuntimeException("Failed to disable employee account", e);
        }
    }

    // private void updateOrganization(Name dn, OrganizationHierarchyDto org) {
    // List<ModificationItem> mods = new ArrayList<>();
    // mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
    // new BasicAttribute("ou", org.getOrgName())));
    // mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
    // new BasicAttribute("parentOrgCode", org.getParentOrgCode())));

    // ldapTemplate.modifyAttributes(dn, mods.toArray(new ModificationItem[0]));
    // }

    /**
     * 構建員工屬性
     * 
     * @param employee 員工資訊
     * @return 員工的 LDAP 屬性
     */
    private javax.naming.directory.Attributes buildEmployeeAttributes(APIEmployeeInfo employee) {
        javax.naming.directory.Attributes attrs = new javax.naming.directory.BasicAttributes();
        javax.naming.directory.Attribute objectClass = new javax.naming.directory.BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("person");
        objectClass.add("organizationalPerson");
        objectClass.add("inetOrgPerson");
        attrs.put(objectClass);

        attrs.put("cn", employee.getFullName());
        attrs.put("sn", employee.getFullName());
        attrs.put("uid", employee.getEmployeeNo());
        attrs.put("mail", employee.getEmailAddress());
        // 添加其他屬性...

        return attrs;
    }
}