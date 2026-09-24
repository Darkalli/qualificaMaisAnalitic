package com.sheets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SheetsProperties.class)
public class SheetsConfiguration {
}
