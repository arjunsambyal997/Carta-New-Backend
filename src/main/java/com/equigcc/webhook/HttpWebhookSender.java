package com.equigcc.webhook;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Posts deliveries. Host names are resolved through {@link UrlGuard#resolvePublic}, and the connection is made to exactly
 * those addresses, so a DNS answer that changes between the check and the connect (DNS rebinding) cannot reach an
 * internal address.
 */
@Component
public class HttpWebhookSender implements WebhookSender, AutoCloseable {

    private final CloseableHttpClient client;

    public HttpWebhookSender(UrlGuard guard) {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new org.apache.hc.client5.http.SystemDefaultDnsResolver() {
                    @Override
                    public java.net.InetAddress[] resolve(String host) throws java.net.UnknownHostException {
                        return guard.resolvePublic(host);
                    }
                })
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setSocketTimeout(Timeout.ofSeconds(10))
                        .build())
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build())
                .disableRedirectHandling() // a redirect could point somewhere the URL check never saw
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    @Override
    public Result post(URI uri, Map<String, String> headers, String body) {
        var request = new HttpPost(uri);
        headers.forEach((name, value) -> {
            if (!name.equalsIgnoreCase("Content-Type")) {
                request.setHeader(name, value);
            }
        });
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        try {
            int status = client.execute(request, response -> response.getCode());
            return new Result(status, status >= 200 && status < 300 ? null : "HTTP " + status);
        } catch (IOException e) {
            return new Result(0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
