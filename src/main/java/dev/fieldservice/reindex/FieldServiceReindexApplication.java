package dev.fieldservice.reindex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InfraiProperties.class)
public class FieldServiceReindexApplication {
    public static void main(String[] args) {
        SpringApplication.run(FieldServiceReindexApplication.class, args);
    }
}
