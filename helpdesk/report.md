# SKALA HelpDesk AI 종합 실습 보고서

## 1. 프로젝트 개요

SKALA HelpDesk AI는 사내 규정 문서와 실시간 업무 데이터를 함께 활용하는 상담 어시스턴트다. 사용자의 정책 질문에는 검색된 문서 근거를 사용하고, 주문 및 티켓과 관련된 질문에는 Tool을 통해 실제 데이터를 조회하거나 업무를 접수한다.

이 프로젝트는 다음 기능을 하나의 상담 흐름으로 통합하는 것을 목표로 한다.

- RAG 기반 사내 규정 검색과 출처 제공
- 주문 및 티켓 실시간 조회·생성
- 여러 턴의 대화 문맥 유지
- 민감정보 및 위험 요청 차단
- AI 요청, 토큰, 지연시간에 대한 감사와 관측
- 외부 AI 서비스 장애에 대응하는 폴백

## 2. 개발 환경

| 구분 | 적용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| AI Framework | Spring AI 1.1.8 |
| AI Model | OpenAI `gpt-4o-mini` |
| Embedding Model | OpenAI `text-embedding-3-small` |
| Database | PostgreSQL 16 (테스트: H2) |
| Vector Store | PGvector |
| Build Tool | Gradle |
| Observability | Spring Boot Actuator, Micrometer |

## 3. Phase 1 — 설정과 ChatClient 조립

### 3.1 목표

Phase 1의 목표는 AI 기능에 사용되는 설정을 코드 밖으로 분리하고, HelpDesk 전용 `ChatClient`에 공통 시스템 프롬프트와 Advisor 체인을 조립하는 것이다.

구체적인 목표는 다음과 같다.

1. 모델과 RAG 및 메모리 설정을 외부 설정으로 관리한다.
2. 설정값의 유효 범위를 애플리케이션 시작 시 검증한다.
3. 시스템 프롬프트를 별도 리소스로 관리한다.
4. VectorStore와 ChatMemory를 Spring Bean으로 구성한다.
5. 안전, 메모리, RAG, 감사, 계측 기능을 하나의 ChatClient에 연결한다.

### 3.2 설정 외부화

`application.properties`에 모델과 HelpDesk 동작 설정을 정의했다.

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.chat.options.temperature=0.2
spring.ai.openai.embedding.options.model=text-embedding-3-small

helpdesk.rag.top-k=5
helpdesk.rag.threshold=0.62
helpdesk.memory.max=20
```

각 설정의 의미는 다음과 같다.

| 설정 | 의미 |
|---|---|
| `spring.ai.openai.api-key` | 환경변수로 전달받는 OpenAI API 키 |
| `spring.ai.openai.chat.options.model` | 상담 답변을 생성할 모델 |
| `spring.ai.openai.chat.options.temperature` | 답변의 무작위성 조절 값 |
| `spring.ai.openai.embedding.options.model` | 문서와 질문을 벡터로 변환할 모델 |
| `helpdesk.rag.top-k` | 질문과 관련된 상위 검색 문서 수 |
| `helpdesk.rag.threshold` | 검색 결과로 인정할 최소 유사도 |
| `helpdesk.memory.max` | 대화 메모리에 유지할 최대 메시지 수 |

### 3.3 타입 안전한 설정 바인딩

`HelpDeskProperties`는 `helpdesk.*` 설정을 Java 객체로 바인딩한다. 중첩 record를 이용해 RAG 설정과 메모리 설정을 구분했다.

설정 오류가 실행 중에 발견되지 않도록 다음 검증 조건을 적용했다.

| 속성 | 허용 범위 |
|---|---:|
| `rag.top-k` | 1~20 |
| `rag.threshold` | 0.0~1.0 |
| `memory.max` | 1~100 |

설정값이 범위를 벗어나면 애플리케이션 컨텍스트 생성 단계에서 실패하므로 잘못된 운영 설정을 조기에 발견할 수 있다.

### 3.4 시스템 프롬프트 외부화

시스템 프롬프트는 Java 문자열로 작성하지 않고 다음 파일에 분리했다.

```text
src/main/resources/prompts/system.st
```

프롬프트에는 다음 원칙을 정의했다.

- 사내 규정은 검색된 문서 근거만 사용한다.
- 근거가 없으면 추측하지 않는다.
- 주문 및 티켓 정보는 Tool을 사용한다.
- Tool에서 확인되지 않은 실시간 정보를 생성하지 않는다.
- 답변은 한국어 존댓말로 간결하게 작성한다.

프롬프트를 외부 파일로 분리하면 Java 코드를 수정하지 않고 상담 정책을 검토하고 변경할 수 있다.

### 3.5 VectorStore 구성

Phase 1에서는 PostgreSQL의 vector 확장을 사용하는 PGvector를 VectorStore로 적용했다. Spring AI starter의 자동 설정이 `EmbeddingModel`, `JdbcTemplate`과 PGvector 설정을 이용해 `VectorStore` Bean을 생성한다.

```text
EmbeddingModel → PGvector → VectorStore 인터페이스
```

스키마 자동 초기화를 활성화하고 `text-embedding-3-small`의 기본 출력 크기와 같은 1536차원을 지정했다. 로컬 개발에서는 `compose.yaml`의 PostgreSQL 16 + pgvector 컨테이너를 사용한다. 테스트 프로필은 외부 DB 없이 실행할 수 있도록 H2와 Mock VectorStore를 사용한다.

### 3.6 대화 메모리 구성

`MessageWindowChatMemory`를 사용해 최근 대화 메시지를 메모리에 유지하도록 구성했다. 최대 메시지 수는 코드에 고정하지 않고 `helpdesk.memory.max` 설정에서 읽는다.

현재 메모리는 애플리케이션 프로세스 내부에 저장된다. 따라서 서버가 재시작되면 대화 기록이 사라지며, 다중 서버 환경에서는 인스턴스 간 대화 기록이 공유되지 않는다. 영속화 또는 분산 저장소 적용은 후속 확장 대상으로 남긴다.

### 3.7 Advisor 체인

HelpDesk 전용 `helpDeskClient`에 다음 Advisor를 기본값으로 등록했다.

```text
AuditAdvisor (0)
    ↓
SafeGuardAdvisor (100)
    ↓
MessageChatMemoryAdvisor (200)
    ↓
QuestionAnswerAdvisor (300)
    ↓
TokenMeterAdvisor (900)
    ↓
AI Model
```

Advisor별 역할은 다음과 같다.

| Advisor | 역할 |
|---|---|
| `AuditAdvisor` | AI 요청, 응답, 실패 여부와 처리 시간을 감사 로그에 기록 |
| `SafeGuardAdvisor` | 민감정보 관련 표현이 포함된 요청을 모델 호출 전에 차단 |
| `MessageChatMemoryAdvisor` | 이전 대화 메시지를 현재 프롬프트에 추가하고 새 대화를 저장 |
| `QuestionAnswerAdvisor` | VectorStore에서 관련 문서를 검색해 질문의 근거로 제공 |
| `TokenMeterAdvisor` | 입력·출력 토큰과 모델 처리 지연시간을 Micrometer에 기록 |

실행 순서를 숫자로 명시해 안전 검사, 메모리 추가, 문서 검색, 모델 계측의 순서가 변경되지 않도록 했다.

### 3.8 안전 처리

Spring AI의 `SafeGuardAdvisor`를 사용해 다음 민감정보 관련 표현을 우선 차단하도록 구성했다.

- 주민등록번호
- 카드번호

차단된 요청은 모델에 전달되지 않고 다음과 같은 안전한 응답을 반환한다.

```text
민감정보가 포함된 요청은 처리할 수 없습니다. 해당 정보를 제거해 주세요.
```

현재 구현은 지정 단어의 포함 여부를 검사하는 초기 단계다. 실제 번호 패턴 탐지, 마스킹, 프롬프트 인젝션 방어는 안전 기능을 다루는 후속 Phase에서 확장한다.

### 3.9 감사와 계측

`AuditAdvisor`는 다음 이벤트를 별도 감사 로거에 기록한다.

- `CHAT_REQUEST`
- `CHAT_RESPONSE`
- `CHAT_FAILED`

현재는 민감한 질문 및 답변 원문을 로그에 남기지 않고 이벤트와 처리시간 중심으로 기록한다.

`TokenMeterAdvisor`는 Micrometer를 이용해 다음 지표를 생성한다.

| 지표 | 태그 및 의미 |
|---|---|
| `ai.tokens` | `type=prompt`: 입력 토큰 수 |
| `ai.tokens` | `type=completion`: 출력 토큰 수 |
| `ai.latency` | `phase=model`: 모델 호출 지연시간 |

모든 지표에는 `feature=helpdesk` 태그를 부여해 다른 AI 기능의 지표와 구분할 수 있도록 했다.

### 3.10 주요 산출물

| 파일 | 역할 |
|---|---|
| `config/AiConfig.java` | VectorStore, ChatMemory, ChatClient와 Advisor 체인 조립 |
| `config/HelpDeskProperties.java` | HelpDesk 설정 바인딩과 유효성 검증 |
| `advisor/AuditAdvisor.java` | AI 요청·응답 감사 로그 |
| `advisor/TokenMeterAdvisor.java` | 토큰 및 모델 지연시간 계측 |
| `resources/prompts/system.st` | HelpDesk 시스템 프롬프트 |
| `resources/application.properties` | 모델, RAG, 메모리 설정 |

### 3.11 검증 결과

Gradle 전체 테스트를 실행해 Java 컴파일, 설정 바인딩, Spring Bean 조립 및 애플리케이션 컨텍스트 로딩을 확인했다.

```text
./gradlew test
BUILD SUCCESSFUL
```

테스트 실행 과정에서는 실제 OpenAI API를 호출하지 않았다. 따라서 API 키가 없는 개발 환경에서도 기본 컨텍스트 검증이 가능하다.

### 3.12 Phase 1 완료 기준

| 완료 기준 | 결과 |
|---|---|
| 모델, RAG, 메모리 설정 외부화 | 완료 |
| 설정값 타입 바인딩 및 범위 검증 | 완료 |
| 시스템 프롬프트 외부화 | 완료 |
| VectorStore Bean 구성 | 완료 |
| ChatMemory Bean 구성 | 완료 |
| HelpDesk 전용 ChatClient 구성 | 완료 |
| 기본 Advisor 체인 연결 | 완료 |
| Spring 컨텍스트 및 테스트 통과 | 완료 |

### 3.13 제한사항 및 후속 과제

Phase 1은 AI 요청을 처리할 공통 기반을 완성한 단계다. 다음 기능은 후속 Phase에서 구현하거나 강화해야 한다.

- 사내 문서 읽기, 청크 분할 및 VectorStore 인제스트
- 문서 출처 메타데이터 저장과 응답 출처 반환
- 주문 및 티켓 Tool 구현
- 실제 채팅 Service와 REST/SSE API 구현
- 정규식 기반 개인정보 탐지와 프롬프트 인젝션 방어
- 사용자별 대화 메모리 격리
- 영속 대화 메모리 적용
- Advisor 단위 테스트와 골든 세트 평가
- 외부 AI 장애 시 폴백 구성

## 4. Phase 2 — 문서 인제스트 파이프라인

### 4.1 목표

Phase 2의 목표는 사내 규정 문서를 검색 가능한 청크로 변환해 PGvector에 저장하고, 저장 결과를 검색 API로 직접 확인할 수 있게 하는 것이다.

핵심 요구사항은 다음과 같다.

1. Tika로 다양한 형식의 문서를 읽는다.
2. 긴 문서를 토큰 기준 청크로 분할한다.
3. 모든 청크에 출처 메타데이터를 저장한다.
4. 같은 문서를 다시 넣을 때 기존 청크를 삭제해 중복을 방지한다.
5. 검색 결과의 출처, 버전, 점수와 미리보기를 확인한다.

### 4.2 인제스트 처리 흐름

```text
문서 업로드
  → TikaDocumentReader로 본문 추출
  → TokenTextSplitter로 청크 분할
  → 출처 메타데이터 추가
  → 동일 source의 기존 청크 삭제
  → 임베딩 생성 및 PGvector 저장
```

파싱 실패 시 기존 문서까지 사라지는 상황을 줄이기 위해 문서 읽기와 청크 분할을 먼저 완료한 후 삭제와 저장을 수행한다.

### 4.3 청크 설정 외부화

```properties
helpdesk.ingest.chunk-size=800
helpdesk.ingest.min-chunk-size-chars=350
helpdesk.ingest.preview-length=160
```

| 설정 | 의미 |
|---|---|
| `chunk-size` | TokenTextSplitter가 목표로 하는 청크 크기 |
| `min-chunk-size-chars` | 지나치게 작은 청크 생성을 방지하는 최소 문자 수 |
| `preview-length` | 품질 확인 API에서 반환할 본문 미리보기 길이 |

각 값은 `HelpDeskProperties.Ingest`에 바인딩하고 Bean Validation으로 허용 범위를 검증한다.

### 4.4 출처 메타데이터

모든 청크에 다음 메타데이터를 저장한다.

| 메타데이터 | 용도 |
|---|---|
| `source` | 원본 파일명 및 재인제스트 삭제 기준 |
| `title` | 사용자에게 표시할 문서 제목 |
| `version` | 인제스트 날짜를 이용한 문서 버전 |
| `docType` | 정책, 매뉴얼 등 문서 종류 |
| `dept` | 문서 담당 부서 |

`source`, `title`, `version`은 최종 답변의 출처 표시에 필요한 필수 항목이다. `docType`과 `dept`는 이후 검색 범위를 제한하는 필터로 활용할 수 있다.

### 4.5 중복 방지

재인제스트할 때 파일명인 `source`가 같은 기존 청크를 필터로 삭제한 후 새 청크를 추가한다.

```text
delete(source = 현재 파일명)
  → add(새 청크 목록)
```

필터 문자열을 직접 연결하지 않고 `FilterExpressionBuilder`를 사용해 특수문자가 포함된 파일명도 안전하게 처리한다. 이 과정이 없으면 같은 청크가 반복해서 누적되어 검색 결과가 특정 문장으로 편향될 수 있다.

### 4.6 관리자 API

#### 문서 인제스트

```http
POST /api/admin/documents
Content-Type: multipart/form-data
```

요청 값은 문서 파일과 `title`, `docType`, `dept`다. 응답에는 source, title, version과 생성한 청크 수가 포함된다.

#### 검색 품질 확인

```http
GET /api/admin/chunks?q={검색어}&topK=5
```

검색 결과는 다음 정보를 반환한다.

- source
- title
- version
- similarity score
- 본문 preview

품질 확인 API에는 유사도 임계값을 적용하지 않는다. 낮은 점수의 결과도 함께 관찰해야 Phase 1의 RAG threshold를 조정할 근거를 얻을 수 있기 때문이다.

### 4.7 검증

단위 테스트에서 다음 내용을 검증했다.

- 기존 source 삭제가 새 청크 추가보다 먼저 실행되는지 확인
- 저장되는 모든 청크에 source, title, version, docType, dept가 존재하는지 확인
- 인제스트 결과의 청크 수가 실제 저장 요청과 일치하는지 확인
- 검색 품질 확인 응답에 출처와 버전이 포함되는지 확인
- 긴 본문이 설정한 미리보기 길이로 잘리는지 확인

```text
./gradlew test
BUILD SUCCESSFUL
```

### 4.8 주요 산출물

| 파일 | 역할 |
|---|---|
| `rag/IngestService.java` | 문서 읽기, 분할, 메타데이터, 중복 제거와 저장 |
| `web/AdminController.java` | 문서 업로드 및 검색 품질 확인 API |
| `config/HelpDeskProperties.java` | 청크와 미리보기 설정 바인딩 |
| `rag/IngestServiceTests.java` | 인제스트 결과물과 호출 순서 검증 |

### 4.9 완료 기준

| 완료 기준 | 결과 |
|---|---|
| Tika 문서 읽기 | 완료 |
| TokenTextSplitter 청크 분할 | 완료 |
| 필수 출처 메타데이터 저장 | 완료 |
| source 단위 삭제 후 재인제스트 | 완료 |
| 임베딩 및 PGvector 저장 연결 | 완료 |
| 검색 품질 확인 API | 완료 |
| 결과물 기반 단위 테스트 | 완료 |

### 4.10 후속 과제

- 실제 사내 규정 문서 준비 및 인제스트
- PostgreSQL 컨테이너와 OpenAI API를 사용하는 통합 테스트
- 문서별 명시적 버전 입력 또는 콘텐츠 해시 적용
- 삭제 성공 후 저장 실패에 대한 복구 전략
- 관리자 인증·인가 활성화
- MIME 타입과 업로드 크기 제한

## 5. Phase 3 — RAG 답변과 출처 표기

### 5.1 목표

Phase 3의 목표는 사용자의 질문을 HelpDesk ChatClient에 전달하고, 생성된 답변과 실제 검색에 사용된 문서 출처를 하나의 구조화된 응답으로 반환하는 것이다.

QuestionAnswerAdvisor는 검색 문서를 모델 프롬프트에 추가하지만 API 응답용 출처 목록까지 자동 생성하지 않는다. 따라서 애플리케이션이 `ChatClientResponse.context()`에서 검색 문서를 꺼내 출처 DTO로 변환해야 한다.

### 5.2 처리 흐름

```text
사용자 질문 + conversationId
  → HelpDesk ChatClient 호출
  → Memory Advisor가 이전 대화 추가
  → QuestionAnswerAdvisor가 PGvector 검색
  → 검색 문서를 모델 근거로 추가
  → 모델 답변 생성
  → 응답 context에서 실제 검색 문서 추출
  → AnswerDto(답변, 출처 목록) 반환
```

### 5.3 대화 식별자 전달

질문과 함께 `conversationId`를 Advisor parameter로 전달한다.

```java
.advisors(advisor -> advisor.param(
    ChatMemory.CONVERSATION_ID, conversationId))
```

`MessageChatMemoryAdvisor`는 이 값을 키로 사용해 같은 대화의 이전 메시지를 불러오고 새로운 질문과 답변을 저장한다. Phase 3에서는 대화 ID 전달 경로를 마련했으며 사용자별 격리는 후속 보안 단계에서 강화한다.

### 5.4 전체 ChatClientResponse 사용

답변 문자열만 반환하는 `content()` 대신 `chatClientResponse()`를 사용한다.

```text
ChatClientResponse
├── chatResponse: 모델이 생성한 답변
└── context: Advisor가 남긴 처리 정보와 검색 문서
```

검색 문서는 다음 context key에 저장된다.

```java
QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS
```

context 값 중 실제 `Document` 객체만 선택하고 source, title, version을 `AnswerDto.Source`로 변환한다.

### 5.5 출처 중복 제거와 검증

한 문서에서 여러 청크가 검색될 수 있으므로 동일한 문서 출처가 응답에 반복될 수 있다. `Source` record의 값 동등성과 `distinct()`를 사용해 같은 source, title, version 조합을 하나로 합친다.

다음 필수 메타데이터가 모두 존재하는 청크만 정상 출처로 인정한다.

- document: 원본 source 파일명
- title: 사용자 표시용 문서 제목
- version: 문서 버전

필수 값이 없는 청크는 출처 목록에서 제외한다.

### 5.6 무근거 답변 차단

검색 결과가 없는데 모델 답변을 그대로 반환하면 모델의 사전학습 지식이나 추측이 사내 규정처럼 전달될 수 있다. 이를 막기 위해 유효한 출처가 없으면 모델의 생성 문장을 버리고 고정된 안전 응답을 반환한다.

```text
제공된 문서에서 확인되지 않습니다.
```

이때 sources는 빈 목록이다. 모델 응답이 비어 있는 경우에도 같은 안전 응답을 사용한다.

### 5.7 응답 구조

```json
{
  "answer": "단순 변심 반품은 수령 후 7일 이내에 신청할 수 있습니다.",
  "sources": [
    {
      "document": "return-policy.md",
      "title": "반품 규정",
      "version": "2026-08-20"
    }
  ]
}
```

답변과 출처를 구조화하면 웹 UI가 출처 링크나 문서 버전을 별도 영역에 표시할 수 있다.

### 5.8 검증

단위 테스트에서 다음 규칙을 검증했다.

- 모델 답변과 실제 검색 출처가 함께 반환되는지 확인
- 같은 문서의 여러 청크가 하나의 출처로 합쳐지는지 확인
- 검색 근거가 없으면 모델 문장을 안전 응답으로 교체하는지 확인
- 필수 메타데이터가 없는 문서를 출처에서 제외하는지 확인

```text
./gradlew test
BUILD SUCCESSFUL
```

### 5.9 주요 산출물

| 파일 | 역할 |
|---|---|
| `chat/HelpDeskService.java` | ChatClient 호출, 대화 ID 전달, 답변과 출처 조립 |
| `chat/AnswerDto.java` | 답변과 문서 출처를 표현하는 구조화 DTO |
| `chat/HelpDeskServiceTests.java` | 출처 추출, 중복 제거와 무근거 차단 검증 |

### 5.10 완료 기준

| 완료 기준 | 결과 |
|---|---|
| HelpDesk ChatClient 질문 호출 | 완료 |
| conversationId 전달 | 완료 |
| Advisor context 검색 문서 추출 | 완료 |
| source, title, version 출처 변환 | 완료 |
| 중복 출처 제거 | 완료 |
| 무근거 모델 답변 차단 | 완료 |
| 결과 변환 단위 테스트 | 완료 |

### 5.11 후속 과제

- 주문 및 티켓 Tool 결과와 RAG 답변의 공존 처리
- 사용자 ID를 포함한 안전한 conversationId 구성
- REST 및 SSE API로 AnswerDto 노출
- 출처 문서 링크와 섹션 정보 확장
- 실제 PGvector 검색을 포함한 통합 테스트

## 6. Phase 4 — Tool 연동: 주문과 티켓

### 6.1 목표

Phase 4의 목표는 문서 검색으로 알 수 없는 실시간 주문 데이터와 상태 변경 요청을 Spring AI Tool로 연결하는 것이다. 주문번호는 모델이 생성하거나 사용자의 대화에서 추출한 값이므로 그대로 신뢰하지 않고, Tool 내부에서 인증 사용자와 주문 소유자를 반드시 함께 검증한다.

### 6.2 Tool 구분

| Tool | 성격 | 동작 |
|---|---|---|
| `OrderTools.orderStatus` | 읽기 | 주문 상태와 예상 도착일 조회 |
| `TicketTools.createTicket` | 쓰기 | 교환·환불 요청을 PENDING 티켓으로 접수 |

두 Tool은 `AiConfig`의 `defaultTools`에 등록되어 HelpDesk ChatClient의 모든 요청에서 모델이 선택할 수 있다.

### 6.3 인증 사용자 전달

`HelpDeskService`는 ChatClient 호출 시 다음 정보를 ToolContext에 넣는다.

```text
userId       = 인증 사용자 ID
toolExecuted = 현재 요청에서 Tool이 실행됐는지 나타내는 표시
```

Tool은 모델 인자로 사용자 ID를 받지 않는다. 서버가 전달한 ToolContext의 userId만 인증 사용자로 사용한다.

### 6.4 주문 소유권 검증

주문 조회는 주문번호만 사용하는 `findById()`가 아니라 다음 Repository 메서드를 사용한다.

```java
findByIdAndOwnerId(orderId, userId)
```

DB 쿼리 조건에 주문번호와 소유자를 함께 포함해 다른 사용자의 주문이 애플리케이션 메모리까지 올라오지 않게 한다. 주문이 없거나 다른 사용자 소유인 경우 모두 같은 메시지를 반환해 주문 존재 여부를 노출하지 않는다.

```text
해당 주문을 찾을 수 없습니다.
```

### 6.5 티켓 승인 게이트

교환·환불 Tool은 실제 업무를 완료하지 않는다. 다음 검증 후 승인 대기 티켓만 생성한다.

1. ToolContext에 인증 사용자 ID가 있는지 확인한다.
2. orderId와 userId로 본인 주문인지 확인한다.
3. 티켓 종류가 EXCHANGE 또는 REFUND인지 확인한다.
4. 사유가 비어 있지 않고 500자 이내인지 확인한다.
5. 무작위 티켓 번호를 생성한다.
6. 상태를 PENDING으로 저장한다.

사용자 응답에는 티켓 번호와 승인 대기 상태를 명시하고 실제 처리가 끝났다고 표현하지 않는다.

```text
티켓 TK-XXXXXXXX를 접수했습니다. 현재 PENDING 상태이며 담당자 승인 후 처리됩니다.
```

### 6.6 RAG 근거와 Tool 근거 구분

Phase 3에서는 문서 출처가 없으면 모델 답변을 차단했다. 하지만 주문과 티켓 답변은 문서가 아니라 DB Tool 결과가 근거이므로 출처 목록이 비어 있어도 정상일 수 있다.

각 요청에 `AtomicBoolean` 실행 표시를 전달하고 Tool이 실행되면 값을 변경한다. 응답 조립 시 다음 기준을 사용한다.

```text
문서 출처 있음       → RAG 근거 답변 허용
성공적인 Tool 실행   → 실시간 DB 근거 답변 허용
둘 다 없음           → 무근거 안전 응답
```

### 6.7 데이터 모델

`Order`는 주문번호, 소유자, 상품명, 상태와 예상 도착일을 저장한다. `Ticket`은 티켓 번호, 주문번호, 요청자, 교환·환불 종류, 사유, 상태와 접수 시간을 저장한다.

로컬 실습에서는 `OrderDataInitializer`가 user1과 user2의 예제 주문을 준비한다.

### 6.8 검증

Tool 단위 테스트에서 다음 내용을 확인했다.

- 주문 조회 쿼리에 인증 사용자 ID가 포함되는지 확인
- 다른 사용자의 주문 정보를 노출하지 않는지 확인
- 사용자 컨텍스트가 없는 Tool 호출을 차단하는지 확인
- 본인 주문의 티켓만 저장하는지 확인
- 생성된 티켓이 PENDING 상태인지 확인
- 응답에 티켓 번호, PENDING, 승인 후 처리가 포함되는지 확인
- 타인 주문에는 티켓을 저장하지 않는지 확인
- EXCHANGE와 REFUND 이외의 타입을 거부하는지 확인

```text
./gradlew test
BUILD SUCCESSFUL
```

### 6.9 주요 산출물

| 파일 | 역할 |
|---|---|
| `domain/Order.java` | 실시간 주문 데이터 모델 |
| `domain/Ticket.java` | 승인 대기 교환·환불 티켓 모델 |
| `repository/OrderRepository.java` | 주문번호와 소유자 동시 조회 |
| `repository/TicketRepository.java` | 티켓 저장소 |
| `tools/OrderTools.java` | 주문 상태 조회 Tool |
| `tools/TicketTools.java` | 교환·환불 티켓 접수 Tool |
| `tools/ToolRequestContext.java` | 인증 사용자와 Tool 실행 표시 추출 |
| `OrderDataInitializer.java` | 로컬 실습용 주문 데이터 |
| `tools/OrderToolsTests.java` | 주문 Tool 소유권 테스트 |
| `tools/TicketToolsTests.java` | 티켓 승인 게이트 테스트 |

### 6.10 완료 기준

| 완료 기준 | 결과 |
|---|---|
| 주문 조회 Tool 구현 | 완료 |
| 티켓 접수 Tool 구현 | 완료 |
| ChatClient 기본 Tool 등록 | 완료 |
| ToolContext 사용자 ID 전달 | 완료 |
| 주문 소유권 검증 | 완료 |
| PENDING 승인 게이트 | 완료 |
| RAG·Tool 근거 구분 | 완료 |
| Tool 보안 단위 테스트 | 완료 |

### 6.11 Phase 4 심화 — Tool 설계 리뷰

이 절은 심화 설계를 코드에 반영하기 전에 현재 구현과 개선 후 목표를 비교한 기록이다. 현재 권한 검증은 코드로 적용되어 있지만, 모델의 Tool 선택 정확도를 높이는 설명과 인자 안내에는 보완할 부분이 있다. 아래의 “반영 후” 내용은 다음 코드 변경에서 적용할 목표이며 아직 구현 완료로 판정하지 않는다.

#### 6.11.1 코드 반영 전

현재 `OrderTools`의 설명은 주문 상태와 배송 예정일을 물을 때 사용한다고 안내한다. `TicketTools`도 사용자가 명시적으로 교환·환불을 요청하고 주문번호와 사유가 있을 때만 사용한다고 안내한다. 따라서 Tool 사용 시점에 대한 기본 설명은 이미 존재한다.

다만 다음 항목은 충분히 명시적이지 않다.

| 검토 항목 | 현재 상태 | 예상 위험 |
|---|---|---|
| Tool 사용 시점 | 기본 조건은 있으나 사용하지 말아야 할 상황은 없음 | 정책 질문에 실시간 Tool을 호출할 수 있음 |
| Tool 간 차이 | 클래스 역할로는 구분되나 설명에서 상호 배제 조건이 약함 | 주문 조회 대신 티켓 생성 Tool을 선택할 수 있음 |
| 인자 형식 | `주문번호`, `EXCHANGE 또는 REFUND` 정도만 안내 | 주문번호 형식을 바꾸거나 사유를 임의 생성할 수 있음 |
| 누락 인자 처리 | 주문번호나 사유가 없을 때의 행동이 없음 | 사용자에게 묻지 않고 추측한 값으로 Tool을 호출할 수 있음 |
| 반복 호출 방지 | 성공 및 실패 결과 이후 재호출 금지 문구가 없음 | 동일 조회 또는 티켓 접수를 반복할 수 있음 |
| 읽기·쓰기 위험도 | 티켓이 승인 대기라는 설명은 존재 | 모델이 결과를 실제 처리 완료로 과장할 가능성이 남음 |
| 권한 검증 | Tool 내부의 `findByIdAndOwnerId`로 구현됨 | 모델 설명과 무관하게 서버에서 차단되므로 현재도 안전함 |

현재 `@ToolParam`은 다음 수준으로 작성되어 있다.

```text
orderId: 조회 또는 접수할 주문번호
type: EXCHANGE 또는 REFUND
reason: 사용자가 말한 요청 사유
```

형식과 예시가 부족하므로 모델이 인자를 정규화하거나 누락된 값을 추측할 여지가 있다.

#### 6.11.2 코드 반영 후 목표

Tool 설명을 다음 원칙으로 보강한다.

1. 언제 사용하는지 명시한다.
2. 언제 사용하지 않는지 명시한다.
3. 비슷한 Tool과의 차이를 설명한다.
4. 필수 인자가 없으면 추측하지 말고 사용자에게 질문하게 한다.
5. 성공 또는 명확한 실패 결과를 받은 뒤 같은 Tool을 반복 호출하지 않게 한다.
6. 쓰기 Tool은 접수와 실제 처리의 차이를 반복해서 명시한다.

개선 후 `OrderTools` 설명의 목표는 다음과 같다.

```text
사용자가 본인 주문의 현재 배송 상태 또는 예상 도착일을 물을 때만 사용한다.
반품·교환 규정 설명이나 티켓 접수에는 사용하지 않는다.
주문번호가 없으면 추측하지 말고 사용자에게 주문번호를 요청한다.
조회 결과를 받은 뒤 같은 주문을 반복 조회하지 않는다.
```

개선 후 `TicketTools` 설명의 목표는 다음과 같다.

```text
사용자가 명시적으로 교환 또는 환불 접수를 요청할 때만 사용한다.
규정이나 가능 여부만 묻는 질문에는 사용하지 않는다.
orderId, type, reason 중 하나라도 없으면 추측하지 말고 사용자에게 요청한다.
티켓이 생성되면 같은 요청을 다시 접수하지 않는다.
이 Tool은 PENDING 티켓만 만들며 실제 교환·환불은 담당자 승인 후 처리된다.
```

`@ToolParam`에는 형식과 예시를 추가한다.

```text
orderId: 숫자로 된 주문번호. 예: 12345
type: EXCHANGE 또는 REFUND 중 하나. 예: REFUND
reason: 사용자가 직접 말한 사유를 요약 없이 그대로 전달
```

#### 6.11.3 전후 비교

| 증상 | 반영 전 | 반영 후 목표 |
|---|---|---|
| Tool을 호출하지 않음 | 사용 조건만 간단히 설명 | “~할 때만 사용”과 필수 인자 누락 시 질문을 명시 |
| 엉뚱한 Tool 호출 | 각 Tool의 역할만 개별 설명 | 주문 조회·정책 안내·티켓 접수의 상호 배제 조건 명시 |
| 이상한 인자 전달 | 짧은 필드 설명 | 허용 형식, enum 값, 예시와 사용자 원문 사용 규칙 추가 |
| 같은 Tool 반복 호출 | 별도 지침 없음 | 성공·실패 결과 이후 동일 요청 재호출 금지 |
| 타인 주문 조회 | Repository에서 소유자 조건 검증 | 설명 개선과 무관하게 Tool 내부 소유권 검증 계속 유지 |
| 티켓 중복 생성 | 호출마다 새 티켓 생성 가능 | 모델 재호출 방지 설명 추가, 이후 DB 멱등성 정책도 검토 |
| 처리 완료 오인 | PENDING과 승인 후 처리를 응답에 포함 | Tool 설명과 반환 문구 모두에서 “접수만 수행”을 강조 |

#### 6.11.4 설명과 코드의 역할 구분

Tool 설명은 모델의 선택 정확도를 높이지만 보안 경계가 될 수 없다. 특히 권한 문제는 프롬프트나 설명만으로 해결하지 않는다.

```text
Tool 설명
  → 모델이 적절한 Tool과 인자를 선택하도록 유도

서버 코드
  → 인증 사용자 확인
  → orderId + userId 소유권 쿼리
  → 허용 타입과 사유 길이 검증
  → PENDING 승인 게이트
```

따라서 심화 설계 반영 후에도 `ToolRequestContext.requiredUserId()`와 `findByIdAndOwnerId()` 검증은 제거하지 않는다. 모델의 Tool 선택이 잘못되어도 데이터 접근과 쓰기 작업은 서버 코드에서 최종 차단한다.

#### 6.11.5 반영 후 검증 계획

설명 개선은 컴파일 성공만으로 효과를 확인할 수 없으므로 다음 시나리오를 평가한다.

| 사용자 질문 | 기대 동작 |
|---|---|
| “주문 12345 어디쯤 왔어요?” | 주문 조회 Tool 1회 호출 |
| “반품 규정이 어떻게 되나요?” | 주문·티켓 Tool 미호출, RAG 사용 |
| “그 주문 환불해 주세요.” | 주문번호 또는 사유가 없으면 먼저 질문 |
| “12345를 단순 변심으로 환불해 주세요.” | 티켓 Tool 1회 호출, PENDING 안내 |
| “방금 요청 다시 해줘.” | 기존 티켓 결과를 이용하고 중복 접수하지 않음 |
| user2가 user1의 12345 조회 | 찾을 수 없다는 동일 응답, 정보 비노출 |

모델 선택 동작은 실제 ChatClient 또는 Tool 호출 기록을 사용하는 통합 테스트로 확인하고, 권한과 입력 검증은 기존 단위 테스트로 계속 보장한다.

### 6.12 후속 과제

- Spring Security 인증 사용자와 ToolContext 연결
- 관리자 티켓 승인 기능
- Tool 실행 감사 로그와 호출 횟수 제한
- 중복 티켓 접수 방지 정책
- 주문 상태별 교환·환불 가능 여부 검증
- Tool 실패를 사용자용 오류 응답으로 변환

## 7. 이후 작성 예정

이 문서는 Phase별 결과를 별도 파일로 나누지 않고 계속 누적한다. 이후 Phase를 완료할 때마다 목표, 설계, 구현 내용, 검증 결과와 제한사항을 동일한 형식으로 추가한다.
