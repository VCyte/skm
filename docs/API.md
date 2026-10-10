# SKM API — Paper 플러그인 연동 및 배포

SKM API는 별도 Paper 플러그인이 SKM 입력을 받도록 공개한 Java API입니다. 현재 API는 **Paper 26.2 / Java 25**를 대상으로 합니다. Fabric 클라이언트 모드가 아니라 서버 플러그인에서 사용합니다.

## 좌표와 타입

```text
group:    tmin.click
artifact: skm-api
version:  1.1.0
```

주요 API 타입은 `io.github.skm.api` 패키지에 있습니다.

- `ActionExecutionService`: 검증이 끝난 `press` / `release` 입력을 실제 서버 기능에 연결
- `ActionDefinition`: action ID, 표시 이름, 기본 키, 쿨다운 등 서버 메타데이터
- `ActionResult`: `SUCCESS`, `NO_RESOURCE`, `INVALID_TARGET`, `DENIED`
- `LevelRequirementService`: 외부 레벨/진행도 시스템 연동
- `PlayerActionEvent`: 실행 서비스 호출 직전에 발생하는 cancellable Bukkit 이벤트

`ActionExecutionService`는 Bukkit Services 방식의 단일 실행 제공자입니다. 여러 플러그인이 동시에 각자 실행을 처리하려면 하나의 제공자가 action ID별로 위임하세요. 여러 플러그인이 수신만 하면 되는 경우 `PlayerActionEvent` listener를 쓰면 됩니다.

## 소비 플러그인에서 의존성 선언

```groovy
repositories {
    mavenLocal() // 같은 개발 PC에서 로컬 설치한 API 테스트용
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly 'tmin.click:skm-api:1.1.0'
    compileOnly 'io.papermc.paper:paper-api:26.2.build.124-stable'
}
```

서버에 `SKM-Server-1.1.0.jar`도 설치해야 합니다. 소비 플러그인의 `plugin.yml`에 로드 의존성을 넣어 SKM API 클래스를 런타임에 사용할 수 있게 합니다.

```yaml
name: MyGameSkills
main: example.mygame.MyGameSkills
version: 1.0.0
depend: [SKM]
```

## ActionExecutionService 구현 예

```java
package example.mygame;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyGameSkills extends JavaPlugin implements ActionExecutionService {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(
            ActionExecutionService.class, this, this, ServicePriority.Normal
        );
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        if (!"skill.fireball".equals(action.id()) || state != InputState.PRESS) {
            return ActionResult.SUCCESS;
        }

        // 대상·거리·마나 등은 반드시 서버에서 검증하고 여기서 실제 효과를 적용합니다.
        player.sendMessage("화염구 입력을 받았습니다.");
        return ActionResult.SUCCESS;
    }
}
```

액션 ID `skill.fireball`은 서버의 `plugins/SKM/actions.yml`에도 등록해야 하며, 해당 액션 입력은 위 서비스로 전달됩니다. 실제 효과와 게임 규칙은 소비 플러그인이 담당합니다.

## 로컬 Maven 저장소에 설치

저장소가 아직 없을 때 가장 간단한 개발 방법입니다. 프로젝트 루트에서:

```bash
./gradlew :skm-api:publishToMavenLocal
```

이 명령은 API를 현재 PC의 `~/.m2/repository/tmin/click/skm-api/1.1.0/`에 설치합니다. `mavenLocal()`을 선언한 다른 프로젝트에서 사용할 수 있지만, 다른 개발자나 CI 서버에 자동으로 공유되지는 않습니다.

## GitHub 소스 저장소 업데이트

소스는 `https://github.com/VCyte/skm` Git 저장소에서 관리합니다. clone한 작업 폴더에서 변경사항을 검토한 뒤 feature/release 브랜치로 commit하고 push하세요. `.gitignore`가 Gradle 캐시·로컬 도구·배포 산출물을 제외합니다.

```bash
git clone https://github.com/VCyte/skm.git
cd skm
git switch -c release/1.1.0
git add .
git commit -m "Prepare SKM 1.1.0"
git push -u origin release/1.1.0
```

저장소를 공개로 만들지는 사용자가 선택해야 합니다. 저장소 생성 후에는 GitHub 웹 화면에서 저장소 이름과 공개/비공개 설정을 확인하세요.

## GitHub Packages에 Maven 의존성 게시

GitHub Release가 게시되면 `.github/workflows/publish-api.yml` workflow가 `GITHUB_TOKEN`과 `packages: write` 권한으로 API를 GitHub Packages에 게시합니다. Actions 안에서 이 저장소에 연결된 package를 게시할 때 별도 PAT가 필요하지 않습니다. 로컬에서 직접 게시하려면 GitHub Packages 쓰기 권한이 있는 **Personal Access Token (classic)** 이 필요합니다. 토큰은 채팅이나 저장소에 넣지 말고 터미널 환경 변수로 전달하세요.[3]

```bash
export SKM_MAVEN_URL="https://maven.pkg.github.com/OWNER/REPOSITORY"
export SKM_MAVEN_USERNAME="YOUR_GITHUB_USERNAME"
read -s SKM_MAVEN_PASSWORD; export SKM_MAVEN_PASSWORD
./gradlew :skm-api:publish
```

Release workflow가 성공하면 `tmin.click:skm-api:1.1.0`이 GitHub Packages에 게시됩니다. Apache Maven registry는 repository-scoped이므로 패키지의 접근 범위는 연결된 저장소를 따릅니다. `VCyte/skm`은 이미 공개 저장소이며 Maven 패키지만 따로 공개/비공개로 바꾸는 설정은 지원되지 않습니다. 다만 GitHub Maven registry는 **공개 패키지도 다운로드 시 인증을 요구**합니다. 외부 소비자는 `read:packages` 권한의 Personal Access Token (classic)을 Gradle 인증값으로 사용하세요. GitHub Actions의 `GITHUB_TOKEN`은 소비 workflow가 있는 저장소에 해당 패키지의 읽기 권한이 허용된 경우에만 사용할 수 있습니다.[3]

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/OWNER/REPOSITORY")
        credentials {
            username = System.getenv("SKM_MAVEN_USERNAME")
            password = System.getenv("SKM_MAVEN_PASSWORD")
        }
    }
}
dependencies {
    compileOnly "tmin.click:skm-api:1.1.0"
}
```

외부 프로젝트의 로컬 빌드와 CI에서는 `gpr.user` 및 `gpr.key`를 설정하고, `gpr.key`에 `read:packages` 권한이 있는 classic PAT를 넣으세요. 토큰은 프로젝트 파일이나 Git 저장소에 커밋하지 마세요. `mavenLocal()`로 개발 PC에 API를 설치한 경우에는 GitHub 인증이 필요하지 않습니다. GitHub Packages의 Maven 레지스트리는 공개 패키지에도 인증을 요구합니다.[3]

## Maven Central에 게시하려는 경우

Maven Central은 `tmin.click`이라는 좌표를 자동으로 받아주지 않습니다. Sonatype은 네임스페이스를 역도메인으로 검증합니다. 실제 도메인이 `tmin.click`이라면 통상 중앙 저장소용 groupId는 **`click.tmin`** 이고, Central Portal에서 네임스페이스를 신청한 뒤 발급된 확인 키를 `tmin.click`의 DNS TXT 레코드에 등록해야 합니다.[1]

또한 Central은 Sources/Javadoc JAR, 각 배포 파일의 체크섬과 GPG 서명, POM의 프로젝트 URL·개발자·SCM 메타데이터를 요구합니다.[2] 현재 프로젝트는 sources/Javadoc와 체크섬은 만들지만, 서명·도메인 검증·프로젝트 SCM 정보는 아직 설정하지 않았으므로 **Maven Central에 바로 게시할 수 있는 상태가 아닙니다**. Central을 목표로 선택하면 해당 요구사항에 맞춰 빌드를 추가 조정해야 합니다.

## References

[1]: https://central.sonatype.org/register/namespace/ "Maven Central namespace registration and domain verification"
[2]: https://central.sonatype.org/publish/requirements/ "Maven Central publishing requirements"
[3]: https://docs.github.com/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry "GitHub Packages Apache Maven registry"

## 버전 올리기

새 API를 출시할 때 루트 `build.gradle`의 `version`을 새 버전으로 바꾸고, 동일한 좌표를 다시 올리지 마세요. 이후 `./gradlew clean build :skm-api:publishToMavenLocal`로 검증하고, 원격 배포는 대상 저장소 credentials를 설정한 뒤 `./gradlew :skm-api:publish`를 실행합니다.
