# SKM Paper 플러그인 개발자 가이드

SKM API는 **다른 Paper 플러그인이 서버 소유 키 액션을 등록하고, 플레이어의 입력 의도를 서버 기능에 연결**하기 위한 API입니다. 플러그인 개발자는 실제 게임 효과와 게임 규칙을 계속 서버에서 구현합니다. 클라이언트 모드는 키를 표시하고 입력을 전달할 뿐입니다.

## 빠른 시작 경로

- 바로 복사해 시작: [`examples/plugin-template`](../examples/plugin-template/README.md)
- 동작을 확인할 수 있는 전체 예제: [`examples/examplewithskm`](../examples/examplewithskm/README.md) — 명령으로 런타임 바인딩 추가/제거와 재시작 복구
- 공개 API 좌표와 게시 안내: [API 연동·배포 가이드](API.md)

대상 조합은 **Paper 26.2 / Java 25 / SKM API 1.2.1**입니다. 접속 플레이어에게는 Minecraft 26.2용 Fabric Loader, Fabric API, SKM 클라이언트 모드도 필요합니다. Paper 플러그인만으로 사용자의 조작 메뉴를 바꿀 수는 없습니다.

## 의존성 구성

소비 플러그인은 SKM API와 Paper API를 `compileOnly`로 사용합니다. SKM 서버 플러그인이 API 클래스를 제공하므로 API를 shading하거나 소비 플러그인 JAR에 복사하지 마세요.

```groovy
repositories {
    mavenLocal() // 로컬 개발 시
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
    maven {
        url = uri('https://maven.pkg.github.com/vcyte/skm')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN') // read:packages 권한
        }
    }
}

dependencies {
    compileOnly 'tmin.click:skm-api:1.2.1'
    compileOnly 'io.papermc.paper:paper-api:26.2.build.124-stable'
}
```

GitHub Packages의 Maven registry는 공개 패키지도 인증이 필요할 수 있습니다. 토큰은 환경 변수나 비밀 저장소로만 전달하고 소스에 커밋하지 마세요. 로컬에서 API를 테스트할 때는 SKM 저장소 루트에서 `./gradlew :skm-api:publishToMavenLocal`을 실행하고 `mavenLocal()`을 사용하면 됩니다.

`plugin.yml`은 SKM을 필수 의존성으로 선언합니다.

```yaml
name: MyGameSkills
version: '1.0.0'
main: example.mygame.MyGameSkills
api-version: '26.2'
depend: [SKM]
```

`depend`는 SKM을 먼저 로드하고 API를 사용할 수 없을 때 플러그인이 잘못 켜지는 것을 방지합니다. SKM을 선택 의존성으로 만들려면 `softdepend`와 API 부재 처리 경로를 직접 구현해야 합니다.

## 액션 생명주기

1. `onEnable()`에서 `ActionRegistrationService`를 가져옵니다.
2. 고유하고 안정적인 ID를 가진 `ActionDefinition`과 `ActionExecutionService` handler를 등록합니다.
3. handler에서 입력을 받은 뒤 **게임별 조건을 서버에서 다시 검증**하고 실제 효과를 적용합니다.
4. `onDisable()`에서 `unregisterAll(this)`를 호출합니다. SKM도 소유 플러그인의 비활성화 때 액션을 자동 정리하지만, 명시적인 정리는 권장됩니다.

> **등록은 실행 중 메모리에만 존재합니다.** SKM은 액션 ID나 Java handler를 서버 DB에 저장하지 않습니다. 플러그인은 시작 때 코드에서 액션을 다시 등록해야 합니다. 액션 ID는 플레이어 키 설정과 연결되는 안정적인 식별자이지 등록을 저장하는 저장소가 아닙니다.
>
> 사용자가 `/add`로 켜고 끄는 기능이라면 원하는 상태만 플러그인 자체 `config.yml`에 저장하고, 시작 때 그 값에 따라 다시 등록하면 충분합니다. 예제 [`examplewithskm`](../examples/examplewithskm/README.md)가 이 방식을 구현합니다. DB는 플레이어별 대량 데이터·조회·여러 서버 간 동기화가 필요할 때 소비 플러그인에서 선택하세요. SKM에 DB를 넣을 필요는 없습니다.

```java
public final class MyGameSkills extends JavaPlugin implements ActionExecutionService {
    private ActionRegistrationService actions;
    private static final String ID = "mygameskills.dash";

    @Override
    public void onEnable() {
        actions = getServer().getServicesManager().load(ActionRegistrationService.class);
        if (actions == null) throw new IllegalStateException("SKM service unavailable");

        ActionDefinition action = new ActionDefinition(
            ID, "대시", "key.keyboard.g", 750, "MyGameSkills",
            false, "mygameskills.dash", 0
        );
        actions.register(this, action, this);
    }

    @Override
    public void onDisable() {
        if (actions != null) actions.unregisterAll(this);
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        if (!ID.equals(action.id())) return ActionResult.DENIED;
        if (state == InputState.RELEASE) return ActionResult.SUCCESS;
        if (!player.isOnline() || player.isDead()) return ActionResult.INVALID_TARGET;

        // TODO: 서버에서 대상·거리·자원·월드·인벤토리 등 게임 규칙을 확인하고 효과를 적용.
        return ActionResult.SUCCESS;
    }
}
```

`register`, `unregister`, `unregisterAll`은 **서버 메인 스레드에서 호출**해야 합니다. 비동기 작업에서 결과가 돌아오면 `getServer().getScheduler().runTask(plugin, () -> actions.register(...))`처럼 메인 스레드로 전환하세요. 중복 ID 또는 서버 `max-actions` 한도를 넘으면 `IllegalArgumentException`, 비동기 호출은 `IllegalStateException`이 발생합니다.

액션 추가/제거는 서버 액션 revision을 갱신하고 접속 중인 클라이언트에 다음 tick의 최신 목록을 동기화합니다. 같은 tick에 여러 API 호출이 있어도 네트워크 동기화는 합쳐집니다. 등록 API에는 플러그인별 호출 빈도 제한이 없지만, SKM 서버의 **전체 액션 수 상한**은 `plugins/SKM/config.yml`의 `max-actions`(기본 128, 허용 범위 1~256)로 적용됩니다.

## ID, 키, holdable 의미

- 액션 ID는 `yourplugin.feature`처럼 플러그인 고유 접두어를 사용하세요. 유효 형식은 소문자 영문/숫자로 시작하고 이후 소문자, 숫자, `.`, `_`, `-`를 사용하며 최대 64자입니다.
- 액션 ID는 플레이어의 서버별 사용자 지정 키와 연결됩니다. **공개한 뒤에는 이름을 바꾸지 마세요.** ID를 바꾸면 새 키 항목으로 취급됩니다.
- 키 예: `key.keyboard.g`, `key.keyboard.unknown`, `key.mouse.1`.
- `holdable: false`는 누름 입력만 의미합니다. 키를 누르고 있어도 한 번의 press로 전달되고 release는 보내지 않습니다.
- `holdable: true`는 `PRESS`와 `RELEASE` 모두 전달합니다. 누르고 있는 동작은 서버 tick·자원 소모·플레이어 퇴장 때 정리를 포함해 서버 측에서 관리하세요.
- `cooldownMillis`는 0~3,600,000, `requiredLevel`은 0~1,000,000이어야 합니다. 표시 이름은 최대 128자, category는 최대 64자입니다.

고정된 서버 액션은 `plugins/SKM/actions.yml`로 관리할 수 있습니다. 특정 플러그인이 소유하고 플러그인 생명주기에 따라 추가/제거할 액션은 `ActionRegistrationService`로 등록하세요. 같은 ID를 두 방식에 동시에 쓰지 마세요.

## 권한, 레벨, 결과 처리

`ActionDefinition.permission`은 Paper 권한으로 SKM이 handler 호출 전에 검사합니다. `requiredLevel > 0`은 `LevelRequirementService`가 서비스로 등록되어 있고 플레이어 레벨이 충족될 때만 통과합니다. 레벨 연동 플러그인은 `getServer().getServicesManager().register(LevelRequirementService.class, provider, this, ServicePriority.Normal)` 형태로 제공할 수 있습니다. 레벨 provider가 없으면 요구 레벨 액션은 거부됩니다.

예를 들어 진행도 플러그인은 자기 레벨 저장소를 조회하는 provider를 등록하고 disable 때 해제합니다. `levels.levelOf(...)` 부분은 실제 플러그인의 API로 바꾸세요.

```java
public final class MyProgressionPlugin extends JavaPlugin implements LevelRequirementService {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(
            LevelRequirementService.class, this, this, ServicePriority.Normal
        );
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public boolean meets(Player player, int requiredLevel) {
        return levels.levelOf(player.getUniqueId()) >= requiredLevel;
    }
}
```

`PlayerActionEvent`는 공통 로깅이나 사전 차단에 사용할 수 있습니다. listener는 자기 플러그인의 `onEnable()`에서 `registerEvents(listener, this)`로 등록하고, 취소하면 개별 액션 handler가 호출되지 않습니다.

```java
@EventHandler
public void beforeDash(PlayerActionEvent event) {
    if ("mygameskills.dash".equals(event.getAction().id())
            && event.getState() == ActionExecutionService.InputState.PRESS
            && event.getPlayer().isDead()) {
        event.setCancelled(true);
    }
}
```

handler는 다음 `ActionResult` 중 하나를 반환합니다.

| 결과 | 사용 예 | SKM 쿨다운 |
|---|---|---|
| `SUCCESS` | 입력을 받아 실행함 | `PRESS`일 때 적용 |
| `NO_RESOURCE` | 마나/탄약/에너지가 부족함 | 적용 안 함 |
| `INVALID_TARGET` | 대상·거리·상태가 잘못됨 | 적용 안 함 |
| `DENIED` | 게임 규칙상 거부함 | 적용 안 함 |

`PlayerActionEvent`는 SKM의 액션/권한/레벨/쿨다운 검증 뒤, 선택된 handler가 실행되기 전에 발생하는 cancellable Bukkit 이벤트입니다. 관찰 또는 공통 차단 로직에 사용할 수 있습니다. 이벤트를 취소하면 handler는 실행되지 않습니다.

## 정적 YAML 액션과 런타임 API 중 선택

- 서버 운영자가 직접 관리하는 고정 목록은 `plugins/SKM/actions.yml`에 선언할 수 있습니다. 이 방식은 전역 `ActionExecutionService` provider 하나가 처리합니다.
- 소비 플러그인이 소유하고 enable/disable 또는 플러그인 상태에 따라 추가·제거할 목록은 `ActionRegistrationService`를 사용하세요. 등록마다 handler를 지정하므로 여러 플러그인이 서로 다른 ID를 안전하게 처리할 수 있습니다.
- 두 방식을 섞어도 되지만, ID는 전체 서버에서 유일해야 합니다. 같은 ID를 YAML과 런타임에 중복 정의하지 마세요.

## 보안 원칙

SKM 입력은 **플레이어의 입력 의도**만 나타내며, 공격력·대상·위치·거리·자원·쿨다운 우회 권한을 주지 않습니다. 클라이언트는 신뢰할 수 없는 입력원으로 취급하세요.

- 피해, 회복, 텔레포트, 아이템 소모 등을 handler에서 서버 상태로 다시 계산합니다.
- 클라이언트가 보낸 값을 게임 결과에 그대로 사용하지 않습니다. SKM 액션에 사용자 입력 문자열이나 대상 선택 데이터를 실어 보내는 기능은 없습니다.
- 민감한 액션에는 `permission`을 선언하고 필요한 서버 상태도 handler에서 확인합니다.
- `server-signature`는 사용자별 키 설정을 서버별로 나누는 레이블일 뿐 **서버 신원 인증이나 암호학적 서명은 아닙니다.** 보안 권한 검사로 사용하지 마세요.
- `ActionResult.SUCCESS`는 성공을 뜻하므로 실제 게임 효과를 적용한 뒤 반환합니다. 성공으로 반환하면 press 쿨다운이 시작됩니다.

## 스타터 프로젝트와 빌드

저장소의 [`examples/plugin-template`](../examples/plugin-template/README.md)은 복사해 쓸 수 있는 Paper 프로젝트입니다. 설정 파일에서 액션 메타데이터를 읽고, enable 때 런타임 등록, disable 때 정리, 서버 측 handler를 포함합니다. 저장소 전체 Gradle 빌드에도 들어 있으므로 `./gradlew :plugin-template:build`로 검증할 수 있습니다.

동작 예제 [`examples/examplewithskm`](../examples/examplewithskm/README.md)은 관리 명령으로 액션을 런타임 추가/제거하고 그 on/off 선택을 로컬 YAML에 보존하는 과정을 보여줍니다. 릴리스 태그에 템플릿이 포함된 버전부터 릴리스 workflow가 스타터 소스 ZIP도 첨부합니다.

## 검증 체크리스트

- [ ] Paper 26.2 / Java 25에서 플러그인이 enable되고 SKM 액션이 등록되는가?
- [ ] 모든 클라이언트가 Fabric API와 해당 Minecraft 버전의 SKM 모드를 설치했는가?
- [ ] `설정 → 조작`에서 키가 보이고 기본 키 및 사용자 지정 키가 동작하는가?
- [ ] `holdable`이 의도한 press/release 동작과 일치하는가?
- [ ] 권한 부족, 쿨다운, 자원 부족, 죽음/잘못된 대상에서 handler가 안전하게 거부하는가?
- [ ] 플러그인 disable/reload 후 키가 제거되고 중복 액션이 남지 않는가?
- [ ] 소비 플러그인 JAR에 SKM API 클래스를 중복 포함하지 않았는가?

문제 해결은 [운영 가이드](OPERATIONS.md)의 장애 진단도 참고하세요.
