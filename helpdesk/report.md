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

## 4. 이후 작성 예정

이 문서는 Phase별 결과를 별도 파일로 나누지 않고 계속 누적한다. 이후 Phase를 완료할 때마다 목표, 설계, 구현 내용, 검증 결과와 제한사항을 동일한 형식으로 추가한다.
