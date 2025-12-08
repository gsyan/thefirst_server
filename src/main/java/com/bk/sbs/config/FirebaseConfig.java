package com.bk.sbs.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    public void initialize() {
        try {
            log.info("Starting Firebase initialization...");
            if (FirebaseApp.getApps().isEmpty()) {
                log.info("No existing Firebase apps found, initializing new app...");
                ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
                if (resource.exists() == false) {
                    log.error("firebase-service-account.json not found in classpath!");
                    throw new RuntimeException("firebase-service-account.json not found");
                }
                log.info("Loading Firebase credentials from: {}", resource.getFilename());
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(resource.getInputStream()))
                    .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully!");
            } else {
                log.info("Firebase app already initialized, skipping...");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase", e);
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }
}
