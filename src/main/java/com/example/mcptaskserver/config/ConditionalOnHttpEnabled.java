package com.example.mcptaskserver.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(HttpEnabledCondition.class)
public @interface ConditionalOnHttpEnabled {
}
