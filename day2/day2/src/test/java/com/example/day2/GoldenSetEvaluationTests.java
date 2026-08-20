package com.example.day2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.example.day2.lab2.service.DocumentIngestService;
import com.example.day2.lab2.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Tag("eval")
@SpringBootTest
class GoldenSetEvaluationTests {

    private static final Logger log = LoggerFactory.getLogger(GoldenSetEvaluationTests.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentIngestService ingestService;

    @Autowired
    private RagService ragService;

    @Value("${lab2.eval.top-k:4}")
    private int topK;

    @Value("${lab2.eval.name:single}")
    private String experimentName;

    @Value("${lab2.eval.enforce-baseline:true}")
    private boolean enforceBaseline;

    @Value("${lab2.eval.result-file:}")
    private String resultFile;

    record Golden(String q, List<String> must, String src) {}

    @Test
    void 골든_세트_평가() throws IOException {
        ingestDocuments();

        List<Golden> golden = objectMapper.readValue(
                new ClassPathResource("golden.json").getInputStream(),
                new TypeReference<>() {});

        int pass = 0;
        for (Golden item : golden) {
            RagService.AnswerDto answer = ragService.ask(item.q(), topK);

            boolean hit = item.must().stream()
                    .allMatch(keyword -> answer.answer().contains(keyword));
            boolean cite = item.src() == null
                    || answer.sources().stream().anyMatch(source -> source.contains(item.src()));

            if (hit && cite) {
                pass++;
                log.info("통과: {}", item.q());
            } else {
                logFailure(item, answer, hit, cite);
            }
        }

        log.info("[{}] 골든 세트 통과 {}/{}", experimentName, pass, golden.size());
        writeExperimentResult(pass, golden.size());
        if (enforceBaseline) {
            assertThat(pass)
                    .as("골든 세트는 10문항 중 최소 8문항을 통과해야 한다")
                    .isGreaterThanOrEqualTo(8);
        }
    }

    private void writeExperimentResult(int pass, int total) throws IOException {
        if (resultFile.isBlank()) {
            return;
        }
        Path path = Path.of(resultFile);
        Files.createDirectories(path.getParent());
        Files.writeString(path, pass + "," + total);
    }

    private void ingestDocuments() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/lab2-docs/*.md");
        Arrays.stream(resources).forEach(resource -> ingestService.ingest(
                resource,
                sourceName(resource),
                "2026-07"));
    }

    private void logFailure(Golden golden, RagService.AnswerDto answer,
            boolean hit, boolean cite) {
        String failureType;
        if (!answer.grounded() || answer.sources().isEmpty()) {
            failureType = "근거를 못 찾음: 청킹·임베딩·top-k·질문 표현을 확인";
        } else if (!hit) {
            failureType = "근거는 찾았지만 답변이 틀림: 프롬프트·모델·근거 포맷을 확인";
        } else {
            failureType = "출처 불일치: 메타데이터와 출처 포맷을 확인";
        }

        log.warn("""
                실패 유형: {}
                질문: {}
                필수 키워드: {}
                기대 출처: {}
                답변: {}
                실제 출처: {}
                키워드 통과: {}, 출처 통과: {}""",
                failureType, golden.q(), golden.must(), golden.src(),
                answer.answer(), answer.sources(), hit, cite);
    }

    private String sourceName(Resource resource) {
        String filename = resource.getFilename();
        return filename == null ? "unknown" : filename.replaceFirst("\\.md$", "");
    }
}
