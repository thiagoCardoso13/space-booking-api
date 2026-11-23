package com.thiago.spacebooking.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documentation marker for endpoints restricted to admins (it adds the 403 response and the Bearer requirement to
 * the OpenAPI document). The actual enforcement lives in {@link SecurityConfig}; a test checks that both agree.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AdminOnly {
}
