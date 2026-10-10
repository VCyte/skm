# SKM 릴리스 노트

## 1.2.0 — 플러그인별 런타임 액션 등록

- `ActionRegistrationService`를 추가해 소비 플러그인이 `actions.yml` 편집 없이 액션을 런타임에 등록·해제하고, 각자 실행 핸들러를 연결할 수 있습니다.
- 런타임 액션 추가·제거는 목록 revision을 갱신하고 접속 중인 호환 클라이언트와 즉시 동기화됩니다. 소유 플러그인이 비활성화되면 해당 플러그인의 모든 액션을 자동 제거합니다.
- 고정 `actions.yml` 액션과 런타임 액션을 함께 지원하며, ID 충돌과 서버 액션 상한을 검증합니다.
- `examplewithskm.jar` 예제를 추가해 명령으로 런타임 액션을 등록·제거하고 개인 플레이어 입력을 처리하도록 했습니다.
- Controls 화면에서 SKM 바인딩이 다른 키와 겹치면 기본 키끼리의 충돌도 노란색으로 표시하도록 수정했습니다.
- 클라이언트는 `holdable: false` 키를 누르고 있을 때 한 번만 입력을 보내며, `[SKM] 스킬 사용 불가` 액션바 안내를 표시하지 않습니다.
- Maven 좌표 및 산출물 버전을 `1.2.0`으로 올렸습니다. Protocol v4는 유지되므로 기존 v4 클라이언트와 호환됩니다.

## 1.1.0 — Minecraft 26.2 입력 수정 및 통합 버전 정리

- 모든 모듈과 소비 예제의 버전을 1.1.0으로 통일했습니다. Maven 좌표는 `tmin.click:skm-api:1.1.0`입니다.
- v1.1.0 API에 맞는 `examplewithskm.jar` 호환 예제와 정적 `actions.yml` 설정 샘플을 릴리스 자산으로 제공합니다.
- GitHub Release 게시 후 Actions workflow가 API 1.1.0을 GitHub Packages로 배포하도록 구성했습니다.
- Minecraft 26.2에서 실제 활성 화면을 `Minecraft.gui.screen()`으로 확인하도록 변경했습니다. 오래된 화면 추적 상태가 입력을 막던 문제를 해결합니다.
- 서버 권위 쿨다운은 그대로 유지하면서 `cooldown` 피드백의 `[SKM] 스킬 쿨다운` 오버레이 표시는 제거했습니다.
- 키 입력 진단용 반복 로그는 정식 배포 소스에서 제거했습니다.
- Paper 26.2 / Java 25 / Minecraft 26.2 / Fabric Loader 0.19.5+ / Fabric API 0.161.0+26.2, Protocol v4.

## 1.0.6 — Fabric 클라이언트 표시 수정

- Minecraft `설정 → 조작`에서 서버별 SKM 카테고리 이름의 `SKM ·` 접두어를 제거했습니다. 예: `skm-main`.
- 이 변경은 표시만 수정하며 wire protocol v4와 서버 API에는 변경이 없습니다.
- Paper 서버 플러그인과 공개 API는 기존 `1.0.5`를 유지합니다. 클라이언트 모드만 `skm-client-26.2-1.0.6.jar`로 교체하세요.

## 1.0.5 — Paper API

- `skm-api` 독립 Gradle 모듈을 추가했습니다. Maven 좌표는 `tmin.click:skm-api:1.0.5`입니다.
- 외부 플러그인이 사용하는 action 모델, 실행 서비스, 결과 타입, 레벨 통합 계약, 취소 가능 이벤트를 `io.github.skm.api` 패키지로 분리했습니다.
- Paper 서버 플러그인은 API 클래스를 포함해 배포합니다. 소비 플러그인은 API를 `compileOnly`로 참조하고 `plugin.yml`에 `depend: [SKM]`을 선언합니다.
- API 1.0.5는 GitHub Packages의 `VCyte/skm` 저장소에 게시되어 있습니다.
- 서버와 Fabric 클라이언트의 wire protocol은 v4입니다. 대상은 Minecraft 26.2 / Paper 26.2 / Java 25입니다.

소비 플러그인 의존성 예:

```groovy
repositories {
    maven { url = uri("https://maven.pkg.github.com/VCyte/skm") }
}
dependencies {
    compileOnly "tmin.click:skm-api:1.0.5"
    compileOnly "io.papermc.paper:paper-api:26.2.build.124-stable"
}
```

전체 인증·연동 절차는 [API 연동·배포 가이드](docs/API.md)를 참고하세요.

## 산출물

- `skm-api/build/libs/skm-api-1.0.5.jar` — Paper 컴파일 API
- `paper-plugin/build/libs/SKM-Server-1.0.5.jar` — Paper 서버 플러그인 (API 클래스 포함)
- `fabric-client/build/libs/skm-client-26.2-1.0.6.jar` — 표시 수정이 포함된 Fabric 클라이언트 모드
