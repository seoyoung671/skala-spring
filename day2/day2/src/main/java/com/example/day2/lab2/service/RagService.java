package com.example.day2.lab2.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    public static final String NOT_FOUND = "제공된 문서에서 확인되지 않습니다";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final int defaultTopK;
    private final double similarityThreshold;

    public RagService(@Qualifier("policyChatClient") ChatClient chatClient,
            VectorStore vectorStore,
            @Value("${lab2.rag.top-k:5}") int defaultTopK,
            @Value("${lab2.rag.similarity-threshold:0.62}") double similarityThreshold) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.similarityThreshold = similarityThreshold;
    }

    public record AnswerDto(String answer, List<String> sources, boolean grounded) {
        public static AnswerDto unknown() {
            return new AnswerDto(NOT_FOUND + ".", List.of(), false);
        }
    }
    public record Chunk(String source, Double score, String snippet) {}

    public AnswerDto ask(String question, Integer requestedTopK) {
        int topK = requestedTopK == null ? defaultTopK : requestedTopK;

        // 5. 검색
        List<Document> evidence = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        if (evidence == null || evidence.isEmpty()) {
            // 근거가 없으면 비용이 드는 LLM을 호출하지 않는다.
            return AnswerDto.unknown();
        }

        String context = evidence.stream()
                .map(this::formatEvidence)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");

        // 6. 생성: 문자열을 직접 파싱하지 않고 AnswerDto로 받는다.
        AnswerDto generated = chatClient.prompt()
                .system("""
                        아래 [근거]만 사용해 답하세요.
                        근거에 답이 없으면 "제공된 문서에서 확인되지 않습니다"라고 답하세요.
                        추측하지 마세요.
                        답변에 사용한 출처만 sources에 넣고, 근거를 사용했으면 grounded를 true로 설정하세요.""")
                .user(user -> user.text("""
                        [근거]
                        {context}

                        [질문]
                        {question}""")
                        .param("context", context)
                        .param("question", question))
                .call()
                .entity(AnswerDto.class);

        if (generated == null || generated.answer() == null || generated.answer().contains(NOT_FOUND)) {
            return AnswerDto.unknown();
        }

        // 출처는 모델이 지어낸 값이 아니라 실제 검색 문서에서 확정한다.
        List<String> sources = evidence.stream()
                .map(document -> String.valueOf(document.getMetadata().get("source")))
                .distinct()
                .toList();
        return new AnswerDto(generated.answer(), sources, true);
    }

    private String formatEvidence(Document document) {
        return "[\ucd9c\ucc98: %s, \ubc84\uc804: %s]\n%s".formatted(
                document.getMetadata().get("source"),
                document.getMetadata().get("version"),
                document.getText());
    }

    /** 생성 전에 출처·유사도 점수·미리보기로 검색 품질을 확인한다. */
    public List<Chunk> retrieve(String question, int topK) {
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(0)
                .build());
        if (documents == null) {
            return List.of();
        }
        return documents.stream()
                .map(document -> new Chunk(
                        String.valueOf(document.getMetadata().get("source")),
                        document.getScore(),
                        snippet(document.getText(), 120)))
                .toList();
    }

    private String snippet(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
