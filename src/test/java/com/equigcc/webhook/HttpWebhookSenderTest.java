package com.equigcc.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpWebhookSenderTest {

    @Test
    void refusesToConnectWhenTheHostResolvesInternally() throws Exception {
        try (var sender = new HttpWebhookSender(new UrlGuard(false))) {
            for (String url : new String[]{"https://localhost:9/hook", "https://127.0.0.1:9/hook", "https://[::1]:9/hook"}) {
                var result = sender.post(URI.create(url), Map.of(), "{}");
                assertThat(result.success()).as(url).isFalse();
                assertThat(result.error()).as(url).contains("public address");
            }
        }
    }

    @Test
    void connectsOnlyToTheAddressesTheGuardApproved() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var hits = new AtomicInteger();
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        var lookups = new AtomicInteger();
        // Stands in for DNS: the sender must use this answer, not one of its own.
        UrlGuard guard = new UrlGuard(true) {
            @Override
            public InetAddress[] resolvePublic(String host) throws UnknownHostException {
                lookups.incrementAndGet();
                assertThat(host).isEqualTo("hooks.example.test");
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }
        };
        try (var sender = new HttpWebhookSender(guard)) {
            int port = server.getAddress().getPort();
            var result = sender.post(URI.create("http://hooks.example.test:" + port + "/hook"), Map.of("X-Test", "1"), "{}");
            assertThat(result.statusCode()).isEqualTo(204);
            assertThat(hits.get()).isEqualTo(1);
            assertThat(lookups.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }
}
