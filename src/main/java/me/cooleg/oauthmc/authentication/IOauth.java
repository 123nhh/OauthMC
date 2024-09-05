package me.cooleg.oauthmc.authentication;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface IOauth {

    CodeAndLinkResponse beginLogin(UUID uuid);

    default String urlToResponse(URL url, String method, String encodedParams) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", "" + encodedParams.getBytes(StandardCharsets.UTF_8).length);
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            connection.setDoOutput(true);

            OutputStream stream = connection.getOutputStream();
            stream.write(encodedParams.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            stream.close();

            InputStream inputStream = null;
            if (100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            byte[] bytes = inputStream.readAllBytes();
            inputStream.close();
            return new String(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    default String stringUrlToResponse(String url, String method, String encodedParams) {
        try {
            return urlToResponse(new URL(url), method, encodedParams);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    default String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

}
