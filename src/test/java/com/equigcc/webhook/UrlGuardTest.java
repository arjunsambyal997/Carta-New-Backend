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
