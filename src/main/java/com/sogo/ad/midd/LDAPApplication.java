package com.sogo.ad.midd;

import java.io.IOException;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;

public class LDAPApplication {
    public static void main(String[] args) {
        // String ldapUrl = "ldaps://48.218.20.1:636";
        String ldapUrl = "ldap://48.218.20.1:389";
        String ldapBaseDn = "DC=uat-sogo,DC=net";
        String ldapUsername = "jeffchang@uat-sogo.net";
        String ldapPassword = "123qweaS";

        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, ldapUsername);
        env.put(Context.SECURITY_CREDENTIALS, ldapPassword);
        env.put(Context.REFERRAL, "ignore");
        // env.put(Context.SECURITY_PROTOCOL, "SSL");

		// env.put("java.naming.ldap.factory.socket", "com.sogo.ad.midd.MySSLSocketFactory");
        
        // Enable SSL debugging
        // System.setProperty("javax.net.debug", "all");

        LdapContext ctx = null;

        try {
            // 建立 LDAP 連接
            ctx = new InitialLdapContext(env, null);
            System.out.println("成功連接到 LDAP 服務器");

            int pageSize = 1;
            byte[] cookie = null;
            ctx.setRequestControls(new Control[] { new PagedResultsControl(pageSize, Control.CRITICAL) });

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String[] returnedAtts = { "cn", "sn", "mail", "displayName" }; // 加入 displayName
            searchControls.setReturningAttributes(returnedAtts);

            String searchFilter = "(&(objectClass=person)(cn=林小齊))";
            System.out.println("搜索過濾器: " + searchFilter);
            System.out.println("基礎 DN: " + ldapBaseDn);

            // do {
            // NamingEnumeration<SearchResult> results = ctx.search(ldapBaseDn,
            // searchFilter, searchControls);

            // while (results != null && results.hasMoreElements()) {
            // SearchResult searchResult = results.next();
            // Attributes attrs = searchResult.getAttributes();
            // System.out.println("找到用戶: " + searchResult.getName());
            // System.out.println("CN: " + attrs.get("cn"));
            // System.out.println("SN: " + attrs.get("sn"));
            // System.out.println("Mail: " + attrs.get("mail"));
            // System.out.println("Current DisplayName: " + attrs.get("displayName")); //
            // 列印原本的 displayName
            // System.out.println("------------------------");

            // // 修改 displayName
            // String newDisplayName = "葉為立";
            // Attribute displayNameAttr = new BasicAttribute("displayName",
            // newDisplayName);
            // ModificationItem[] mods = new ModificationItem[1];
            // mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
            // displayNameAttr);

            // // 執行更新操作
            // System.out.println("searchResult.getNameInNamespace()==="+searchResult.getNameInNamespace());
            // ctx.modifyAttributes(searchResult.getNameInNamespace(), mods);
            // System.out.println("成功更新 displayName 為: " + newDisplayName);
            // }

            // Control[] controls = ctx.getResponseControls();
            // if (controls != null) {
            // for (Control control : controls) {
            // if (control instanceof PagedResultsResponseControl) {
            // PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
            // cookie = prrc.getCookie();
            // }
            // }
            // }

            // ctx.setRequestControls(new Control[] { new PagedResultsControl(pageSize,
            // cookie, Control.CRITICAL) });
            // } while (cookie != null);

        } catch (NamingException | IOException e) {
            System.err.println("LDAP 操作失敗: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 確保 LDAP 連接被關閉
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    System.err.println("關閉 LDAP 連接失敗: " + e.getMessage());
                }
            }
        }
    }
}
