修改plugins\OauthMC\config.yml那里的login-mode

eg:

    login-mode: "LinuxDo"

    linuxdo-settings:
      client-id: "***"
      client-secret: "***"
      min-trust-level: 2
      require-active: true

    authme:
      enabled: true
      force-linuxdo-binding: true
      disable-password-login: true
  
connect的回调地址填这个 http://ip:8080/callback
（上述为你mc服务器的ip，保证安全组放行8080端口）
