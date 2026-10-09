# SKM — Server Key Mapping

**SKM은 서버가 제공하는 목록만큼 Minecraft 조작 키를 동적으로 만드는 키 매핑 모드**입니다. Paper 서버 플러그인과 Fabric 클라이언트 모드가 짝을 이뤄 동작하며, 특정 게임 장르에 종속되지 않습니다. Minecraft `설정 → 조작`에서 SKM 카테고리는 **목록 최상단**에 표시됩니다.

> Paper 서버 플러그인만으로는 플레이어 PC의 조작 메뉴를 바꿀 수 없습니다. 각 클라이언트에도 Fabric API와 SKM Fabric 모드가 필요합니다.

## 배포 파일 — 1.0.5

| 파일 | 설치 위치 | 역할 |
|---|---|---|
| `SKM-Server-1.0.5.jar` | Paper 서버 `plugins/` | 서버별 action 목록, 검증, 실행 서비스 및 API 클래스 |
| `skm-client-26.2-1.0.5.jar` | Fabric 클라이언트 `mods/` | 동적 키 등록, 서버별 키 설정, 입력 전달 |
| `skm-api-1.0.5.jar` | 소비 플러그인 compileOnly | 다른 Paper 플러그인이 컴파일할 SKM API |

Maven 좌표: **`tmin.click:skm-api:1.0.5`**. 대상 환경: **Minecraft 26.2 / Java 25 / Paper 26.2 / Fabric Loader 0.19.5+ / Fabric API 0.161.0+26.2**. wire protocol은 v4이며, 현재 서버와 클라이언트 플러그인은 1.0.5로 함께 교체하세요.

## 빌드

```bash
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build
```

산출물:

```text
skm-api/build/libs/skm-api-1.0.5.jar
paper-plugin/build/libs/SKM-Server-1.0.5.jar
fabric-client/build/libs/skm-client-26.2-1.0.5.jar
```

## 다른 Paper 플러그인에서 의존성으로 받기

현재 API artifact는 Maven Central에 자동 게시되어 있지 않습니다. 로컬 개발 시 프로젝트에서 한 번 설치하세요.

```bash
./gradlew :skm-api:publishToMavenLocal
```

소비 플러그인의 Gradle 설정:

```groovy
repositories {
    mavenLocal()
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly 'tmin.click:skm-api:1.0.5'
    compileOnly 'io.papermc.paper:paper-api:26.2.build.124-stable'
}
```

서버에는 `SKM-Server`도 설치하고 소비 플러그인의 `plugin.yml`에 `depend: [SKM]`을 넣어야 합니다. 그 다음 `ActionExecutionService` 구현을 Paper Services에 등록하면 검증된 입력을 받을 수 있습니다. API 사용 예와 원격 Maven 업로드 절차는 [API 연동·배포 가이드](docs/API.md)를 참고하세요.

## 설치와 업그레이드

1. 서버를 종료하고 기존 구버전 서버 플러그인을 `plugins/`에서 제거한 뒤 `SKM-Server-1.0.5.jar`를 설치합니다.
2. 클라이언트의 `mods/`에서 구버전 클라이언트 JAR을 제거하고 `skm-client-26.2-1.0.5.jar`를 설치합니다. 기존 JAR과 새 JAR을 함께 두지 마세요.
3. 서버를 시작합니다. 이전 버전 데이터 폴더의 `config.yml`, `actions.yml`은 `plugins/SKM/`로 자동 복사됩니다. 새 위치에 파일이 이미 있으면 덮어쓰지 않습니다.
4. 기존 서버별 캐시와 키 설정은 사용할 때 `.minecraft/config/skm/`로 자동 복사됩니다. 원본 파일은 삭제하지 않습니다.
5. Fabric 클라이언트로 접속해 Paper 콘솔에서 `Received SKM hello`, `Sending N SKM action(s) ... ids: ...`를 확인합니다.

## 서버 액션 설정

서버는 `plugins/SKM/actions.yml`을 읽습니다. 각 항목이 키 매핑 하나가 되며 `skill.test` 같은 점 포함 ID도 지원합니다.

```yaml
actions:
  skill.test:
    name: "테스트 스킬"
    default-key: "key.keyboard.z"
    cooldown-ms: 1000
    category: "test"
    holdable: false

  skill.sprint:
    name: "질주"
    default-key: "key.keyboard.x"
    cooldown-ms: 250
    category: "movement"
    holdable: true
```

Paper의 YAML path 처리로 ID가 중첩 section처럼 보이더라도 SKM은 `skill.test`, `skill.sprint`로 복원합니다. 로그의 `Sending N ... ids:`에는 실제 전송되는 ID가 나옵니다. 액션 목록이 바뀌면 파일 감시가 기본 5초 이내 자동 적용하고 접속 중인 클라이언트에 동기화합니다.

- ID는 영구 식별자이므로 유지하면 플레이어의 키 설정도 유지됩니다.
- `default-key` 예: `key.keyboard.z`, `key.mouse.1`; 생략하면 미지정입니다.
- `cooldown-ms` 범위는 0~3,600,000입니다.
- `holdable: true`는 누름/뗌 입력을 서버에 전달합니다.
- 기본 action 상한은 128, 설정 가능 범위는 1~256입니다.

새 설정은 `plugins/SKM/config.yml`에서 backend별 고유 `server-signature`를 사용합니다. 명령은 `/skm status`, `/skm reload`입니다.

## 키 설정과 서버 전환

- 서버 접속 시 해당 signature의 action만 Controls에 표시되고, 카테고리는 **정렬 우선순위 1번**으로 고정됩니다.
- action 표시명은 서버 설정의 `name`을 사용합니다.
- 연결 해제나 서버 전환 시 이전 서버 키를 즉시 숨기고 등록에서 제거합니다. 다음 서버의 액션만 표시합니다.
- 키 변경값은 클라이언트에만 저장됩니다. 서버로 전송되지 않습니다.

```text
.minecraft/config/skm/cache/<server-signature>.json
.minecraft/config/skm/servers/<server-signature>.json
```

## 프로토콜과 확장

Fabric mod ID, key ID, payload 채널 namespace는 `skm`이며 wire protocol은 v4입니다. v4 미만 서버/클라이언트와 통신하지 않으므로 양쪽 JAR을 함께 교체해야 합니다. 클라이언트는 action ID와 `press`/`release` 의도만 보냅니다. 권한, 레벨, 쿨다운, 실행은 서버가 검증합니다. 기본 실행 서비스는 안전한 no-op입니다. 실제 게임 효과는 별도 Paper 플러그인이 API의 `ActionExecutionService`를 구현해 담당합니다.

- [Paper API 소비 및 Maven 업로드 가이드](docs/API.md)
- [프로토콜 v4 명세](docs/PROTOCOL.md)
- [운영·서버 전환 가이드](docs/OPERATIONS.md)
- [통합 테스트 절차](docs/TESTING.md)
- [26.2/Java 25 호환성 참고](docs/RESEARCH.md)
- [배포 메모](RELEASE-NOTES.md)
