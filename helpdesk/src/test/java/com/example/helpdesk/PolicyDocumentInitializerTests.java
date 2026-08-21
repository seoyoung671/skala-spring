package com.example.helpdesk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.helpdesk.rag.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class PolicyDocumentInitializerTests {

    @Test
    void 시작할_때_기본_정책_문서_세_개를_인제스트한다() throws Exception {
        IngestService ingestService = org.mockito.Mockito.mock(IngestService.class);
        when(ingestService.ingest(any(Resource.class), any(String.class),
                eq("policy"), eq("helpdesk")))
                .thenReturn(new IngestService.IngestResult("policy.md", "policy.md", "v1", 1));

        new PolicyDocumentInitializer(ingestService).run(null);

        verify(ingestService, times(3)).ingest(
                any(Resource.class), any(String.class), eq("policy"), eq("helpdesk"));
    }
}
