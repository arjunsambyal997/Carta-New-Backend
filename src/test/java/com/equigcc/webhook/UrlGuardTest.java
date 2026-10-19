package com.equigcc.webhook;

import com.equigcc.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlGuardTest {

    private final UrlGuard strict = new UrlGuard(false);

    @Test
    void internalAddressesAreRecognised() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "192.168.0.9", "172.16.5.5", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1"}) {
            assertThat(UrlGuard.isInternal(InetAddress.getByName(ip))).as(ip).isTrue();
        }
        for (String ip : new String[]{"0.1.2.3", "192.0.0.8", "198.18.0.1", "198.19.255.255", "240.0.0.1", "255.255.255.255",
                "::127.0.0.1", "64:ff9b::a9fe:a9fe", "2002:7f00:1::", "2002:a00:1::", "2001:db8::1", "fe80::1", "fec0::1"}) {
            assertThat(UrlGuard.isInternal(InetAddress.getByName(ip))).as(ip).isTrue();
        }
        for (String ip : new String[]{"1.1.1.1", "198.20.0.1", "2606:4700:4700::1111", "64:ff9b::808:808", "2002:808:808::"}) {
            assertThat(UrlGuard.isInternal(InetAddress.getByName(ip))).as(ip).isFalse();
        }
        assertThat(UrlGuard.isInternal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(UrlGuard.isInternal(InetAddress.getByName("100.128.0.1"))).isFalse();
    }

    @Test
    void rejectsPlainHttpCredentialsAndLiteralInternalHosts() {
        assertThatThrownBy(() -> strict.validate("http://example.com/hook")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> strict.validate("https://user:pw@example.com/hook")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> strict.validate("https://127.0.0.1/hook")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> strict.validate("https://169.254.169.254/latest")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> strict.validate("ftp://example.com")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> strict.validate(" ")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void insecureModeAllowsLocalTargetsForDevelopment() {
        assertThat(new UrlGuard(true).validate("http://localhost:9999/hook").getPort()).isEqualTo(9999);
    }
}
