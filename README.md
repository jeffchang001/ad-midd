ad-midd

#AD 憑證問題
1. 與 AD 管理者取得完整 pfx 檔案(整個憑證鏈)
2. 將憑證鏈轉換成 pem 檔案: openssl pkcs12 -in ldaps.pfx -out certificate_chain.pem -nodes (通常 pfx 會伴隨一個密碼: Sogo1004!)
3. 從這個文件中提取憑證部分: openssl x509 -in certificate_chain.pem -out certificate.pem
4. 將 pem 匯入 keystore: keytool -import -alias uat_ad_cert -file certificate.pem -keystore "C:/Program Files/Zulu/zulu-8/jre/lib/security/cacerts" -storepass changeit
5. keystore 預設密碼是: changeit
6. 刪除憑證(重新匯入需要):  keytool -keystore "C:\Program Files\Zulu\zulu-8\jre\lib\security\cacerts" -delete -alias uat_ad_cert -storepass changeit
