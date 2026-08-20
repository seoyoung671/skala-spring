package com.example.helpdesk.web;

import java.io.IOException;
import java.util.List;

import com.example.helpdesk.rag.IngestService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 문서 인제스트와 검색 청크 확인을 제공하는 관리자 API다. */
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IngestService ingestService;

    public AdminController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** 업로드한 사내 문서를 청크로 나누고 PGvector에 저장한다. */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public IngestService.IngestResult ingest(
            // MultipartFile을 Resource로 바꾸면 IngestService가 저장 위치와 무관하게 읽을 수 있다.
            @RequestPart("file") MultipartFile file,
            // title은 사용자에게 보여 줄 출처명, docType/dept는 검색 필터용 정보다.
            @RequestParam @NotBlank String title,
            @RequestParam @NotBlank String docType,
            @RequestParam @NotBlank String dept) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("인제스트할 문서 파일이 필요합니다.");
        }
        return ingestService.ingest(file.getResource(), title, docType, dept);
    }

    /**
     * 검색된 청크의 출처, 버전, 유사도와 미리보기를 직접 확인한다.
     * 모델 답변을 생성하기 전 검색 단계만 분리해서 관찰하므로 RAG 문제의 원인이
     * 검색인지 생성인지 구분하고 top-k와 threshold를 조정할 수 있다.
     */
    @GetMapping("/chunks")
    @PreAuthorize("hasRole('ADMIN')")
    public List<IngestService.ChunkView> inspect(
            @RequestParam("q") @NotBlank String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topK) {
        return ingestService.inspect(query, topK);
    }
}
