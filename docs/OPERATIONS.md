# 운영 가이드 — 서명별 동적 키

## 서버별 서명과 액션 수

`plugins/SKM/config.yml`에서 backend마다 signature를 고유하게 지정합니다.

```yaml
server-signature: skm-dungeon
max-actions: 128
watch-actions-file: true
watch-interval-ticks: 100
```

`max-actions`는 1~256입니다. 액션 수를 늘리면 모든 온라인 호환 클라이언트에 sync되고, 클라이언트는 그 개수에 맞춰 실제 Minecraft key mapping을 만듭니다. 256은 프로토콜/메모리 하드 상한이며 설정 기본값은 128입니다.

예시:

| 백엔드 | 권장 signature |
|---|---|
| 로비 | `skm-lobby` |
| 생존 | `skm-survival` |
| 던전 | `skm-dungeon` |

`server-signature`를 두 서버에서 공유하면 클라이언트 캐시·로컬 키 파일을 의도치 않게 공유합니다. 파일을 수정하면 재시작하지 않아도 되지만 서명 교체는 해당 backend를 재시작해 새 설정을 로드하세요.

## 액션 등록

```yaml
actions:
  skill.fireball:
    name: "화염구"
    default-key: "key.keyboard.z"
    cooldown-ms: 3000
    category: "combat"
    holdable: false
    permission: "game.skill.fireball"
    required-level: 5
```

- 키는 action ID마다 하나씩 생성됩니다. 고정 슬롯/slot 번호를 쓰지 않습니다.
- `skill.test`처럼 점을 포함한 ID를 사용할 수 있습니다. Paper는 이를 중첩 YAML 경로로 보일 수 있지만 플러그인이 dotted ID를 복원합니다.
- action ID를 유지하면 클라이언트의 사용자 지정 키가 유지됩니다.
- ID를 변경하면 새로운 키 항목입니다. 삭제된 ID의 키는 현재 서버 화면에서 제거됩니다.
- 사용하지 않는 action은 YAML에서 삭제하면 됩니다. 유효하지 않은 변경은 마지막 정상 registry를 유지합니다.
- 저장 즉시 기본 5초 감시 또는 `/skm reload`로 검증되며, 의미 있는 변경은 새 revision으로 sync됩니다.

액션을 소비 플러그인에서 직접 관리하려면 `tmin.click:skm-api:1.2.0`의 `ActionRegistrationService`를 사용합니다. 플러그인이 `onEnable()`에서 `ActionDefinition`과 해당 플러그인의 `ActionExecutionService` handler를 등록하면, SKM은 접속 중인 호환 클라이언트에 키 항목을 즉시 추가합니다. `unregister(owner, id)`로 한 개를 제거하거나 `unregisterAll(owner)`로 플러그인 소유 액션을 모두 제거할 수 있습니다. 플러그인 disable 때도 SKM이 자동 정리합니다. 런타임 등록 액션은 YAML에 중복으로 선언하지 마세요.

## 플레이어 확인 절차

1. Fabric Loader, Fabric API, SKM Fabric 모드가 있는 26.2 클라이언트로 해당 backend에 접속합니다.
2. handshake의 signature가 의도한 서버 값인지 확인합니다.
3. `설정 → 조작`에서 SKM 카테고리가 맨 위에 있고 그 아래 서버 액션 이름들이 나타나는지 확인합니다. 바인딩이 다른 키와 겹치면 항목이 노란색으로 강조됩니다.
4. 설정된 key가 입력되고 서버가 action cooldown/permission을 계속 검증하는지 확인합니다. SKM은 사용 불가 안내를 액션바나 채팅으로 띄우지 않습니다.
5. 서버를 나가거나 다른 backend로 이동합니다. 이전 action 이름들이 Controls 목록에서 사라지고, 새 서버 목록만 보여야 합니다.
6. 같은 서버에 다시 접속해 앞서 지정한 키가 config에서 복구되는지 확인합니다.

## 클라이언트 로컬 파일

```text
.minecraft/config/skm/cache/<signature>.json
.minecraft/config/skm/servers/<signature>.json
```

- `cache`는 서버에서 받은 읽기용 action 메타데이터입니다. revision 변경 시 서버 목록이 우선합니다.
- `servers`는 플레이어별 키 변경만 저장합니다. 다른 signature 서버에는 적용되지 않습니다.
- 파일은 클라이언트 config 폴더에만 있으며 서버에 전송되지 않습니다.
- 이 파일을 지우면 해당 서버의 로컬 키 override만 초기화되어 서버 기본 키로 돌아옵니다.

## 권한·레벨·효과

`permission`은 Paper 권한으로 서버 검증합니다. `required-level > 0` action은 외부 레벨 플러그인이 `LevelRequirementService`를 `ServicePriority.Normal` 이상으로 등록할 때만 통과합니다. 연결되지 않은 상태는 거부됩니다.

`actions.yml`에 선언한 고정 액션은 전역 `ActionExecutionService` provider로 처리할 수 있습니다. 런타임 액션은 `ActionRegistrationService.register(owner, definition, handler)`로 전달한 플러그인별 handler로 처리됩니다. 기본 구현은 no-op 성공이므로, 별도 handler가 없는 고정 액션은 서버 상태를 변경하지 않습니다. 효과 플러그인에서 대상·거리·마나·피해를 서버 측으로 다시 계산합니다.

## 장애 진단

| 증상 | 확인 사항 |
|---|---|
| 조작 목록에 서버 키가 전혀 없음 | Paper 서버 JAR만으로는 불가합니다. 플레이어 PC의 `.minecraft/mods/`에 Fabric API와 SKM client JAR을 설치하고 Fabric 프로필로 실행했는지 확인합니다. 서버 `/pl`에는 클라이언트 모드가 표시되지 않습니다. |
| 서버에는 플레이어 접속만 보이고 Hello 로그가 없음 | 클라이언트 Fabric 모드 초기화 로그, 26.2·Java 25, Fabric API 설치를 확인합니다. 최신 v4 모드는 접속 시 Hello를 최대 5회 재시도합니다. |
| Hello는 오지만 handshake가 없음 | 서버와 client 모두 최신 v4 JAR인지, 서버 로그의 client protocol 호환 결과 및 plugin channel 오류를 확인합니다. |
| handshake는 왔지만 키가 없거나 이름이 `skill`로 표시됨 | Paper 콘솔의 `Sending N ... ids: ...`에서 실제 ID를 봅니다. dotted IDs는 `skill.test`, `skill.sprint`처럼 개별 항목으로 표시되어야 하며, 클라이언트 sync/등록 개수도 N과 같아야 합니다. |
| 들어와도 서버 키가 없음 | Paper 플러그인 실행, 클라이언트 모드/API 설치, action count > 0, protocol v4, raw payload framing |
| 다른 서버 키가 잠깐 보임 | 두 backend가 서로 다른 signature인지, 서버 전환의 disconnect/join 로그 확인 |
| 액션 개수 일부만 표시 | `max-actions`, 서버 액션 수, 서버 콘솔의 payload 크기/설정 검증 로그 |
| 서버 기본 키가 적용 안 됨 | 플레이어의 per-signature `keyOverrides` 존재 여부; 직접 지정한 키는 기본값보다 우선 |
| 삭제된 키가 계속 노출됨 | 최신 v4 모드인지 확인. 연결 해제 시 모드는 현재 mappings를 비우고 재접속 때 revision을 재동기화 |
| 액션은 뜨지만 효과가 없음 | 고정 액션은 `ActionExecutionService` 등록 여부, 런타임 액션은 등록할 때 올바른 handler를 전달했는지 확인. 기본 실행은 안전한 no-op |

## 보안 한계

모드는 편리한 입력 장치이지 신뢰 경계가 아닙니다. 각 입력에 서버가 계산해야 하는 전투 정보는 포함하지 않습니다. 유효한 action ID 하나에 대해 서버는 플레이어 권한, action 상태, 대상 및 게임 자원을 재검증해야 합니다.
