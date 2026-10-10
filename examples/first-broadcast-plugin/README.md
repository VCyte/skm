# SKM First Broadcast — 의존성 예시 플러그인

`tmin.click:skm-api:1.1.0`를 `compileOnly`로 사용하는 별도 Paper 플러그인 예시입니다. SKM Fabric 모드에서 서버 action `skill.first`를 누르면 전체 온라인 플레이어에게 **Minecraft 계정 이름**을 넣어 다음 메시지를 broadcast합니다.

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
build/libs/SKM-First-Broadcast-1.1.0.jar
```

GitHub Packages에서 API를 받으려면 사용자 홈의 `~/.gradle/gradle.properties`에 인증값을 설정할 수 있습니다. 비밀값은 프로젝트 파일이나 저장소에 넣지 마세요.

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

GitHub의 Apache Maven registry는 **공개된 패키지도 다운로드할 때 인증을 요구**하므로, 로컬 빌드나 일반적인 외부 CI에서는 `read:packages` 권한을 가진 **classic PAT**를 사용하세요. `GITHUB_ACTOR`와 `GITHUB_TOKEN`은 소비 플러그인의 GitHub Actions 저장소에 해당 패키지 읽기 권한이 부여된 경우에만 사용할 수 있습니다. `GH_TOKEN` 환경 변수는 GitHub CLI용 토큰일 수 있어 Maven registry 인증에 사용할 수 없습니다. API JAR을 같은 PC의 Maven 로컬 저장소에 `publishToMavenLocal`로 설치한 경우에는 별도 인증 없이 `mavenLocal()`에서 받습니다.

## 서버 설정

1. Paper 서버 `plugins/`에 `SKM-Server-1.1.0.jar`와 `SKM-First-Broadcast-1.1.0.jar`를 함께 설치합니다.
2. 플레이어 PC의 `mods/`에는 Fabric API와 `skm-client-26.2-1.1.0.jar`를 설치합니다.
3. `plugins/SKM/actions.yml`에 아래 action을 등록합니다.

```yaml
actions:
  skill.first:
    name: "첫 인사"
    default-key: "key.keyboard.z"
    cooldown-ms: 1000
    category: "general"
    holdable: false
```

4. 서버를 재시작하거나 `/skm reload`를 실행합니다.
5. 플레이어가 Minecraft `설정 → 조작`에서 SKM 카테고리의 `첫 인사` 키를 지정한 뒤, 서버에 접속한 상태에서 누르면 됩니다.

`plugin.yml`의 `depend: [SKM]`이 SKM 서버 플러그인을 먼저 로드하게 합니다. 메시지의 이름은 표시 닉네임이 아닌 `Player#getName()`으로 얻는 **Minecraft 계정 이름**입니다. 예시 플러그인은 API 서비스의 기본 Lowest보다 높은 Normal 우선순위로 실행 provider를 등록합니다. `skill.first` 이외의 action이나 키 release 입력은 성공 처리만 하고 아무 메시지도 보내지 않습니다.

SKM `config.yml`의 `server-signature`는 소문자만 허용합니다. 예전 설정이 `AFE-RPG`라면 `afe-rpg`로 바꾸세요. 대문자 값은 SKM 플러그인이 시작 시 거부합니다.
