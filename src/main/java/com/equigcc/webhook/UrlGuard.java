package com.equigcc.webhook;

import com.equigcc.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Keeps webhooks from being pointed at the platform's own network. A target must be https and must resolve only to
 * public addresses. The same check runs again just before each send, because DNS can change after a URL is saved, and
 * the sender connects only to the addresses {@link #resolvePublic} approved, so a second lookup cannot swap them.
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
        try {
            resolvePublic(host);
        } catch (UnknownHostException e) {
            throw new BusinessRuleException(e.getMessage());
        }
    }

    /** Resolves a host for the sender, refusing it when any of its addresses is internal (unless insecure mode is on). */
    public InetAddress[] resolvePublic(String host) throws UnknownHostException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new UnknownHostException("Could not find that host name");
        }
        if (!allowInsecure) {
            for (InetAddress a : addresses) {
                if (isInternal(a)) {
                    throw new UnknownHostException("Webhook URLs must point to a public address");
                }
            }
        }
        return addresses;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 16) {
            return isInternalV6(b);
        }
        int o0 = b[0] & 0xFF, o1 = b[1] & 0xFF, o2 = b[2] & 0xFF;
        return o0 == 0                                   // 0.0.0.0/8 "this network"
                || (o0 == 100 && (o1 & 0xC0) == 64)      // 100.64.0.0/10 carrier-grade NAT
                || (o0 == 192 && o1 == 0 && o2 == 0)     // 192.0.0.0/24 protocol assignments
                || (o0 == 192 && o1 == 0 && o2 == 2)     // 192.0.2.0/24 documentation
                || (o0 == 198 && (o1 & 0xFE) == 18)      // 198.18.0.0/15 benchmarking
                || (o0 == 198 && o1 == 51 && o2 == 100)  // 198.51.100.0/24 documentation
                || (o0 == 203 && o1 == 0 && o2 == 113)   // 203.0.113.0/24 documentation
                || o0 >= 240;                            // 240.0.0.0/4 reserved, and broadcast
    }

    private static boolean isInternalV6(byte[] b) {
        if ((b[0] & 0xFE) == 0xFC) {
            return true; // fc00::/7 unique local
        }
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8) {
            return true; // 2001:db8::/32 documentation
        }
        // Addresses that carry an IPv4 address inside are judged by that address.
        if (allZero(b, 0, 12)) {
            return embedded(b, 12); // ::a.b.c.d (IPv4-compatible)
        }
        if ((b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && allZero(b, 4, 12)) {
            return embedded(b, 12); // 64:ff9b::/96 NAT64
        }
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
            return embedded(b, 2); // 2002::/16 6to4
        }
        return false;
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean embedded(byte[] b, int at) {
        try {
            return isInternal(InetAddress.getByAddress(java.util.Arrays.copyOfRange(b, at, at + 4)));
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
