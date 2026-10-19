package com.equigcc.web;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void aDuplicateFoundAtFlushIsAConflictNotAServerError() throws Exception {
        var handles = Arrays.stream(ApiExceptionHandler.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(ExceptionHandler.class))
                .filter(m -> Arrays.asList(m.getAnnotation(ExceptionHandler.class).value()).contains(ConstraintViolationException.class))
                .findFirst().orElseThrow();
        var pd = (org.springframework.http.ProblemDetail) handles.invoke(new ApiExceptionHandler(),
                new ConstraintViolationException("duplicate", new SQLException("duplicate key value"), "uk_share_class_org_name"));
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).doesNotContain("uk_share_class_org_name");
    }
}
