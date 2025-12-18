package com.onlinecourse.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "security.permit") // Bind với "security.permit"
public class PermitProperties {

    private List<String> paths = new ArrayList<>();
    public List<String> getPaths() {
        return paths;
    }
    public void setPaths(List<String> paths) {
        this.paths = paths;
    }
}