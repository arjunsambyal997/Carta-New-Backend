package com.equigcc.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * The path Spring MVC routes on: percent-decoded, without ;parameters or doubled slashes. Filters that decide by path
 * must use this, not {@code getRequestURI()}, or {@code /api/v1/auth/%6Cogin} reaches the login endpoint unnoticed.
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    public static String of(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
