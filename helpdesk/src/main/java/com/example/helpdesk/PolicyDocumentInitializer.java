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
// 자동 인제스트를 끄고 수동 업로드만 시험해야 할 때는 Bean 자체를 만들지 않는다.
// matchIfMissing=true이므로 별도 설정이 없는 일반 실행에서는 기본적으로 동작한다.
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
        // classpath*: 대신 classpath:를 사용해 현재 애플리케이션의 docs 디렉터리만 읽는다.
        // 와일드카드 해석은 Resource 추상화가 담당하므로 JAR로 패키징한 뒤에도 같은 코드로 찾는다.
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(POLICY_DOCUMENTS);

        int totalChunks = 0;
        for (Resource resource : resources) {
            String title = resource.getFilename() == null
                    ? "정책 문서"
                    : resource.getFilename();
            IngestService.IngestResult result = ingestService.ingest(
                    resource, title, "policy", "helpdesk");
            // IngestService가 source 기준으로 기존 청크를 지운 뒤 다시 저장하므로
            // 서버를 재시작해도 동일 문서의 청크가 계속 중복 적재되지는 않는다.
            totalChunks += result.chunks();
        }

        log.info("HelpDesk 정책 문서 자동 인제스트 완료: documents={}, chunks={}",
                resources.length, totalChunks);
    }
}
