package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.IOauth;
import org.bukkit.Bukkit;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LinuxDoCallbackServer implements HttpHandler {

    private final HttpServer server;
    private final String clientId;
    private final String clientSecret;
    private final String callbackUrl;
    private final Gson gson;
    private final Map<String, LinuxDoUserInfo> authResults;

    public LinuxDoCallbackServer(int port, String clientId, String clientSecret, String callbackUrl, Gson gson) throws IOException {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUrl = callbackUrl;
        this.gson = gson;
        this.authResults = new ConcurrentHashMap<>();

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/callback", this);
        this.server.setExecutor(null);
        this.server.start();

        Bukkit.getLogger().info("LinuxDo OAuth callback server started on port " + port);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);

        String code = params.get("code");
        String state = params.get("state");

        if (code == null || state == null) {
            sendResponse(exchange, 400, "<html><body><h1>Error</h1><p>Missing code or state parameter</p></body></html>");
            return;
        }

        try {
            String accessToken = exchangeCodeForToken(code);
            LinuxDoUserInfo userInfo = fetchUserInfo(accessToken);

            authResults.put(state, userInfo);

            sendResponse(exchange, 200, "<html><body><h1>Success!</h1><p>Authorization successful. You can close this window and return to Minecraft.</p></body></html>");
        } catch (Exception e) {
            Bukkit.getLogger().warning("LinuxDo OAuth callback error: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "<html><body><h1>Error</h1><p>Failed to complete authorization. Please try again.</p></body></html>");
        }
    }

    private String exchangeCodeForToken(String code) throws IOException {
        URL tokenUrl = new URL("https://connect.linux.do/oauth2/token");
        HttpsURLConnection connection = (HttpsURLConnection) tokenUrl.openConnection();

        String params = "grant_type=authorization_code"
                + "&code=" + IOauth.urlEncode(code)
                + "&redirect_uri=" + IOauth.urlEncode(callbackUrl)
                + "&client_id=" + IOauth.urlEncode(clientId)
                + "&client_secret=" + IOauth.urlEncode(clientSecret);

        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length", String.valueOf(params.getBytes(StandardCharsets.UTF_8).length));
        connection.setRequestMethod("POST");
        connection.setUseCaches(false);
        connection.setDoOutput(true);

        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(params.getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }

        String response;
        try (InputStream inputStream = connection.getInputStream()) {
            response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        LinuxDoTokenResponse tokenResponse = gson.fromJson(response, LinuxDoTokenResponse.class);
        return tokenResponse.accessToken;
    }

    private LinuxDoUserInfo fetchUserInfo(String accessToken) throws IOException {
        URL userInfoUrl = new URL("https://connect.linux.do/api/user");
        HttpsURLConnection connection = (HttpsURLConnection) userInfoUrl.openConnection();

        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestMethod("GET");

        String response;
        try (InputStream inputStream = connection.getInputStream()) {
            response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        return gson.fromJson(response, LinuxDoUserInfo.class);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new ConcurrentHashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                result.put(pair[0], pair[1]);
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

    public LinuxDoUserInfo getAuthResult(String state) {
        return authResults.get(state);
    }

    public void removeAuthResult(String state) {
        authResults.remove(state);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            Bukkit.getLogger().info("LinuxDo OAuth callback server stopped");
        }
    }

}
