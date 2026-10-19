package com.equigcc.webhook;

import com.equigcc.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Keeps webhooks from being pointed at the platform's own network. A target must be https and must resolve only to
 * public addresses. The same check runs again just before each send, because DNS can change after a URL is saved.
 */
@Component
public class UrlGuard {

    private final boolean allowInsecure;

    public UrlGuard(@Value("${equigcc.webhooks.allow-insecure:false}") boolean allowInsecure) {
        this.allowInsecure = allowInsecure;
    }

    public URI validate(String url) {
        if (url == null || url.isBlank() || url.length() > 2000) {
            throw new BusinessRuleException("Enter a webhook URL of up to 2000 characters");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("That is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowInsecure && scheme.equals("http"))) {
            throw new BusinessRuleException("Webhook URLs must use https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new BusinessRuleException("The URL needs a host name and must not contain a user name or password");
        }
        if (!allowInsecure) {
            checkPublic(uri.getHost());
        }
        return uri;
    }

    void checkPublic(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessRuleException("Could not find that host name");
        }
        for (InetAddress a : addresses) {
            if (isInternal(a)) {
                throw new BusinessRuleException("Webhook URLs must point to a public address");
            }
        }
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 16) {
            // fc00::/7 unique local addresses
            return (b[0] & 0xFE) == 0xFC;
        }
        // 100.64.0.0/10 carrier-grade NAT, 169.254.0.0/16 is covered by isLinkLocalAddress
        return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
    }
}
