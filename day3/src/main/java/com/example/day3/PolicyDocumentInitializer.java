package com.example.day3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.day3.service.PolicyDocumentIngestService;

@Component
@ConditionalOnProperty(name = "lab3.policy.ingest-enabled", havingValue = "true", matchIfMissing = true)
public class PolicyDocumentInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyDocumentInitializer.class);

    private final PolicyDocumentIngestService ingestService;

    public PolicyDocumentInitializer(PolicyDocumentIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int chunks = ingestService.ingest();
        log.info("Day 3 정책 문서 인제스트 완료: {} chunks", chunks);
    }
}
