package com.equigcc.system;

import com.equigcc.system.SystemDtos.FlagRequest;
import com.equigcc.system.SystemDtos.StatusView;
import com.equigcc.system.SystemDtos.WindowRequest;
import com.equigcc.system.SystemDtos.WindowView;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * Public read of build version, maintenance notices and feature flags, so the UI can warn clients before an
 * update. Writes are for the operator only and need the shared admin token; with no token configured they do not exist.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    static final String TOKEN_HEADER = "X-System-Admin-Token";

    private final SystemService system;
    private final String adminToken;

    public SystemController(SystemService system, @Value("${equigcc.system.admin-token:}") String adminToken) {
        this.system = system;
        this.adminToken = adminToken;
    }

    @GetMapping("/status")
    public StatusView status() {
        return system.status();
    }

    @GetMapping("/flags")
    public Map<String, Boolean> flags() {
        return system.flags();
    }

    @PostMapping("/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    public WindowView schedule(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                               @Valid @RequestBody WindowRequest req) {
        requireAdmin(token);
        return system.schedule(req);
    }

    @DeleteMapping("/maintenance/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestHeader(value = TOKEN_HEADER, required = false) String token, @PathVariable UUID id) {
        requireAdmin(token);
        system.cancel(id);
    }

    @PutMapping("/flags/{key}")
    public Map<String, Boolean> setFlag(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                        @PathVariable String key, @RequestBody FlagRequest req) {
        requireAdmin(token);
        return system.setFlag(key, req.enabled());
    }

    private void requireAdmin(String token) {
        if (adminToken.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        boolean ok = token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), adminToken.getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
    }
}
