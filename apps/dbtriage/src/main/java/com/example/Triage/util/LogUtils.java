package com.example.Triage.util;

import lombok.experimental.UtilityClass;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class LogUtils {
    public static String safeMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? "Operation failed." : msg;

    }
}
