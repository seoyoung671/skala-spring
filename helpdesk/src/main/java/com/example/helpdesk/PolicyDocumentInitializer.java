package com.example.helpdesk;

import java.io.IOException;

import com.example.helpdesk.rag.IngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** 애플리케이션 시작 시 기본 정책 문서를 기존 인제스트 파이프라인에 넣는다. */
@Component
@ConditionalOnProperty(
        name = "helpdesk.ingest.auto-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PolicyDocumentInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyDocumentInitializer.class);
    private static final String POLICY_DOCUMENTS = "classpath:/docs/*.md";

    private final IngestService ingestService;

    public PolicyDocumentInitializer(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(POLICY_DOCUMENTS);

        int totalChunks = 0;
        for (Resource resource : resources) {
            String title = resource.getFilename() == null
                    ? "정책 문서"
                    : resource.getFilename();
            IngestService.IngestResult result = ingestService.ingest(
                    resource, title, "policy", "helpdesk");
            totalChunks += result.chunks();
        }

        log.info("HelpDesk 정책 문서 자동 인제스트 완료: documents={}, chunks={}",
                resources.length, totalChunks);
    }
}
