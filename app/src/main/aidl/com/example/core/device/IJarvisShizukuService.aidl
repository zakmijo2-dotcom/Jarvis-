package com.example.core.device;

interface IJarvisShizukuService {
    String getRuntimeInfo() = 1;
    String executeAllowlistedAction(String actionId, String arg1, String arg2) = 2;
    void destroy() = 16777114;
}
