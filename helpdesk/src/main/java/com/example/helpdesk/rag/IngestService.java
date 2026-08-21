package com.example.helpdesk.rag;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.helpdesk.config.HelpDeskProperties;
import com.example.helpdesk.security.PromptInjectionDetector;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 사내 규정 문서를 읽고, 청크로 나누고, 출처 메타데이터를 붙여 VectorStore에 저장한다.
 * 같은 source를 다시 인제스트할 때 기존 청크를 먼저 삭제해 중복 검색 결과를 방지한다.
 */
@Service
public class IngestService {

    private final VectorStore vectorStore;
    private final HelpDeskProperties properties;
    private final PromptInjectionDetector injectionDetector;

    public IngestService(
            VectorStore vectorStore,
            HelpDeskProperties properties,
            PromptInjectionDetector injectionDetector) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.injectionDetector = injectionDetector;
    }

    public record IngestResult(String source, String title, String version, int chunks) {
    }

    /**
     * 검색 품질 확인 API가 반환하는 청크 요약이다.
     * 전체 본문 대신 preview만 노출해 응답 크기와 문서 노출 범위를 제한한다.
     */
    public record ChunkView(
            String source,
            String title,
            String version,
            Double score,
            String preview) {
    }

    /**
     * 문서 하나를 인제스트한다.
     *
     * source, title, version은 최종 답변의 출처 표시에 필요한 필수 메타데이터다.
     * docType과 dept는 이후 문서 종류나 부서별 검색 필터에 사용할 수 있다.
     */
    public IngestResult ingest(Resource file, String title, String docType, String dept) {
        String source = requiredSource(file);
        String normalizedTitle = required(title, "문서 제목");
        String normalizedDocType = required(docType, "문서 종류");
        String normalizedDept = required(dept, "담당 부서");
        String version = LocalDate.now(ZoneOffset.UTC).toString();

        // 1. 읽기: Tika가 PDF, DOCX, TXT 등 파일 형식별 본문 추출을 담당한다.
        // 삭제 전에 문서를 먼저 읽어 파싱이 실패했는데 기존 문서까지 사라지는
        // 상황을 줄인다.
        List<Document> raw = new TikaDocumentReader(file).get();
        if (raw.stream().anyMatch(document -> injectionDetector.containsInjection(document.getText()))) {
            throw new IllegalArgumentException(
                    "정책 문서에서 프롬프트 인젝션 패턴을 발견했습니다: " + source);
        }

        // 2. 분할: 문서 전체를 모델에 보내지 않고 검색 가능한 작은 청크로 나눈다.
        // 구분자를 유지하면 제목이나 문단 경계가 청크에 남아 검색 품질에 도움이 된다.
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(properties.ingest().chunkSize())
                .withMinChunkSizeChars(properties.ingest().minChunkSizeChars())
                .withKeepSeparator(true)
                .build()
                .apply(raw);

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("인제스트할 문서 내용이 없습니다.");
        }

        // 3. 메타데이터 보강: 모든 청크에 같은 출처 정보를 복사한다.
        // 나중에 어떤 청크가 검색되더라도 답변 출처를 복원할 수 있다.
        List<Document> enriched = chunks.stream()
                .map(chunk -> enrich(chunk, source, normalizedTitle,
                        normalizedDocType, normalizedDept, version))
                .toList();

        // 4. 재인제스트 준비: 같은 source의 이전 청크를 문서 단위로 모두 삭제한다.
        // 문자열로 필터식을 연결하지 않고 빌더로 값을 바인딩해 특수문자가 포함된
        // 파일명도 안전하게 처리한다. 이 단계가 없으면 같은 청크가 계속 누적된다.
        var sourceFilter = new FilterExpressionBuilder().eq("source", source).build();
        vectorStore.delete(sourceFilter);

        // 5. 임베딩·저장: add가 각 청크의 임베딩 생성과 PGvector 저장을 수행한다.
        vectorStore.add(enriched);
        return new IngestResult(source, normalizedTitle, version, enriched.size());
    }

    /** 검색 결과의 출처, 점수와 일부 본문을 반환해 인제스트 품질을 확인한다. */
    public List<ChunkView> inspect(String query, int topK) {
        String normalizedQuery = required(query, "검색어");
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("topK는 1 이상 20 이하여야 합니다.");
        }

        // 질문을 임베딩한 후 PGvector에서 의미적으로 가까운 청크를 조회한다.
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(normalizedQuery)
                .topK(topK)
                // 품질 확인 화면에서는 낮은 점수도 관찰할 수 있도록 임계값을 적용하지 않는다.
                .similarityThreshold(0)
                .build());
        if (hits == null) {
            return List.of();
        }

        return hits.stream()
                .map(document -> new ChunkView(
                        metadata(document, "source"),
                        metadata(document, "title"),
                        metadata(document, "version"),
                        document.getScore(),
                        preview(document.getText())))
                .toList();
    }

    private Document enrich(
            Document chunk,
            String source,
            String title,
            String docType,
            String dept,
            String version) {
        // Tika와 Splitter가 이미 추가한 메타데이터를 잃지 않도록 복사한 뒤
        // HelpDesk가 요구하는 출처 정보를 덧붙인다.
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("source", source);
        metadata.put("title", title);
        metadata.put("version", version);
        metadata.put("docType", docType);
        metadata.put("dept", dept);
        return new Document(chunk.getText(), metadata);
    }

    private String requiredSource(Resource file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("인제스트할 문서 파일이 필요합니다.");
        }
        return required(file.getFilename(), "문서 파일명");
    }

    private String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "이(가) 필요합니다.");
        }
        return value.strip();
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        int maxLength = properties.ingest().previewLength();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
