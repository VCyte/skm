# SKM 1.0.5 — Paper API

## 주요 변경

- `skm-api` 독립 Gradle 모듈을 추가했습니다. Maven 좌표는 `tmin.click:skm-api:1.0.5`입니다.
- 외부 플러그인이 사용하는 action 모델, 실행 서비스, 결과 타입, 레벨 통합 계약, cancellable 이벤트를 `io.github.skm.api` 패키지로 분리했습니다.
- Paper 서버 플러그인은 이 API 클래스를 포함해 배포되므로, 소비 플러그인은 API를 `compileOnly`로 참조하고 `plugin.yml`에 `depend: [SKM]`을 선언합니다.
- API는 `publishToMavenLocal` 또는 설정된 원격 Maven 저장소의 `publish` task로 배포할 수 있습니다.
- 서버와 Fabric 클라이언트의 wire protocol은 v4를 유지합니다. Minecraft 26.2 / Paper 26.2 / Java 25 대상입니다.

## 의존성 사용

```groovy
repositories { mavenLocal() }
dependencies {
    compileOnly 'tmin.click:skm-api:1.0.5'
    compileOnly 'io.papermc.paper:paper-api:26.2.build.124-stable'
}
```

원격 Maven 배포와 API 사용 코드는 [API 연동·배포 가이드](docs/API.md)를 참고하세요. 이 작업에서 원격 Maven에 실제 업로드되지는 않았습니다. 저장소 위치와 인증정보가 제공되지 않았기 때문입니다.

## 산출물

- `skm-api/build/libs/skm-api-1.0.5.jar` — 컴파일 의존성/API 문서
- `paper-plugin/build/libs/SKM-Server-1.0.5.jar` — Paper 서버 플러그인 (API 클래스 포함)
- `fabric-client/build/libs/skm-client-26.2-1.0.5.jar` — Fabric 클라이언트 모드
