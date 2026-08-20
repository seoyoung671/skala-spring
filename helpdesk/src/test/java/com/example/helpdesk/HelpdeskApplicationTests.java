package com.example.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HelpdeskApplicationTests {

	// 단위·컨텍스트 테스트는 외부 PostgreSQL이나 OpenAI에 의존하지 않는다.
	// 운영 환경에서는 PGvector 자동 설정이 실제 VectorStore Bean을 제공한다.
	@MockitoBean
	VectorStore vectorStore;

	@Test
	void contextLoads() {
	}

}
