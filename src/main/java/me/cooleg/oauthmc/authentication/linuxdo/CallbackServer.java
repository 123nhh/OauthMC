package me.cooleg.oauthmc.authentication.linuxdo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CallbackServer {
    
    private final int port;
    private final LinuxDoOauth oauth;
    private HttpServer server;
    
    public CallbackServer(int port, LinuxDoOauth oauth) {
        this.port = port;
        this.oauth = oauth;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/callback", this::handleCallback);
        server.setExecutor(null);
        server.start();
        Bukkit.getLogger().info("OauthMC: LinuxDo callback server started on port " + port);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            Bukkit.getLogger().info("OauthMC: LinuxDo callback server stopped");
        }
    }
    
    private void handleCallback(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) {
                sendResponse(exchange, 400, "<html><body><h1>Error</h1><p>Missing parameters</p></body></html>");
                return;
            }
            
            Map<String, String> params = parseQuery(query);
            String code = params.get("code");
            String state = params.get("state");
            String error = params.get("error");
            
            if (error != null) {
                Bukkit.getLogger().warning("OauthMC: Authorization error: " + error);
                sendResponse(exchange, 200, 
                    "<html><body><h1>授权失败</h1><p>错误: " + error + "</p><p>请关闭此页面并重新登录游戏</p></body></html>");
                return;
            }
            
            if (code == null || state == null) {
                sendResponse(exchange, 400, 
                    "<html><body><h1>Error</h1><p>Missing code or state parameter</p></body></html>");
                return;
            }
            
            oauth.handleCallback(code, state);
            
            sendResponse(exchange, 200, 
                "<html><body style='font-family: sans-serif; text-align: center; padding-top: 50px;'>" +
                "<h1 style='color: green;'>✓ 授权成功</h1>" +
                "<p style='font-size: 18px;'>您已成功完成 linux.do 账号绑定</p>" +
                "<p style='color: gray;'>请返回游戏，您将自动登录</p>" +
                "<p style='margin-top: 30px; color: gray; font-size: 14px;'>您可以安全地关闭此页面</p>" +
                "</body></html>");
            
        } catch (Exception e) {
            Bukkit.getLogger().severe("OauthMC: Error handling callback");
            e.printStackTrace();
            sendResponse(exchange, 500, 
                "<html><body><h1>Internal Server Error</h1><p>Please check server logs</p></body></html>");
        }
    }
    
    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                result.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                );
            }
        }
        return result;
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
