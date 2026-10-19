package com.equigcc.webhook;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpWebhookSender implements WebhookSender {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER) // a redirect could point somewhere the URL check never saw
            .build();

    @Override
    public Result post(URI uri, Map<String, String> headers, String body) {
        try {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return new Result(response.statusCode(), response.statusCode() >= 200 && response.statusCode() < 300 ? null : "HTTP " + response.statusCode());
        } catch (IOException e) {
            return new Result(0, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, "Interrupted");
        }
    }
}
