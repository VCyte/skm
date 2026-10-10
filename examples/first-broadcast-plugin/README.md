# SKM First Broadcast — 의존성 예시 플러그인

`tmin.click:skm-api:1.2.0`를 `compileOnly`로 사용하는 별도 Paper 플러그인 예시입니다. `ActionRegistrationService`로 `skill.first`를 런타임 등록하므로 `plugins/SKM/actions.yml`을 편집할 필요가 없습니다. SKM Fabric 모드에서 등록된 키를 누르면 전체 온라인 플레이어에게 **Minecraft 계정 이름**을 넣어 다음 메시지를 broadcast합니다.

```text
안녕하세요! <Minecraft 계정 이름> 님!
```

## 빌드

Java 25가 필요합니다.

```bash
bash gradlew clean build
```

완성 JAR:

```text
build/libs/SKM-First-Broadcast-1.2.0.jar
```

GitHub Packages에서 API를 받으려면 사용자 홈의 `~/.gradle/gradle.properties`에 인증값을 설정할 수 있습니다. 비밀값은 프로젝트 파일이나 저장소에 넣지 마세요.

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

GitHub의 Apache Maven registry는 **공개된 패키지도 다운로드할 때 인증을 요구**하므로, 로컬 빌드나 일반적인 외부 CI에서는 `read:packages` 권한을 가진 **classic PAT**를 사용하세요. `GITHUB_ACTOR`와 `GITHUB_TOKEN`은 소비 플러그인의 GitHub Actions 저장소에 해당 패키지 읽기 권한이 부여된 경우에만 사용할 수 있습니다. `GH_TOKEN` 환경 변수는 GitHub CLI용 토큰일 수 있어 Maven registry 인증에 사용할 수 없습니다. API JAR을 같은 PC의 Maven 로컬 저장소에 `publishToMavenLocal`로 설치한 경우에는 별도 인증 없이 `mavenLocal()`에서 받습니다.

## 서버 설정

1. Paper 서버 `plugins/`에 `SKM-Server-1.2.0.jar`와 `SKM-First-Broadcast-1.2.0.jar`를 함께 설치합니다.
2. 플레이어 PC의 `mods/`에는 Fabric API와 `skm-client-26.2-1.2.0.jar`를 설치합니다.
3. 서버를 시작하거나 예시 플러그인을 다시 활성화합니다. 플러그인 `onEnable()`이 `skill.first` 키를 등록하면 접속 중인 클라이언트에 새 목록이 전송됩니다.
4. 플레이어가 Minecraft `설정 → 조작`에서 SKM 카테고리의 `첫 인사` 키를 지정하고 접속 상태에서 누릅니다.

`plugin.yml`의 `depend: [SKM]`이 SKM 서버 플러그인을 먼저 로드하게 합니다. 예제는 `onDisable()`에서 등록 액션을 직접 해제하며, SKM도 플러그인 비활성화 때 해당 소유자의 액션을 자동 정리합니다. 메시지의 이름은 표시 닉네임이 아닌 `Player#getName()`으로 얻는 **Minecraft 계정 이름**입니다.

SKM `config.yml`의 `server-signature`는 소문자만 허용합니다. 예전 설정이 `AFE-RPG`라면 `afe-rpg`로 바꾸세요. 대문자 값은 SKM 플러그인이 시작 시 거부합니다.
