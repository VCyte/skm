# SKM Protocol v4

v4는 Paper plugin-message의 raw UTF-8 JSON body를 유지하면서 모든 custom payload 채널을 `skm:*` namespace로 통일한 버전입니다. 이전 버전과 채널 이름이 호환되지 않으므로 서버 플러그인과 클라이언트 모드를 함께 교체해야 합니다. Body 앞에 byte-array 길이 VarInt는 붙이지 않습니다.

## 채널

| 채널 | 방향 | 용도 |
|---|---|---|
| `skm:hello` | C→S | 클라이언트 프로토콜 선언 |
| `skm:handshake` | S→C | 프로토콜, 서버 signature, revision, action 상한 |
| `skm:ack` | C→S | 캐시 적중 통지 또는 sync 요청 |
| `skm:sync` | S→C | 서버 키 매핑 목록 |
| `skm:input` | C→S | action ID와 press/release 의도 |
| `skm:feedback` | S→C | 서버 실행 결과 |

각 payload body는 JSON object를 UTF-8로 인코딩한 raw bytes입니다. 최대 payload 크기는 128 KiB이며 action 상한은 1~256, 기본 128입니다.

## Handshake

```json
{"protocol":4}
```

```json
{
  "protocol": 4,
  "minClient": 4,
  "signature": "skm-dungeon",
  "revision": "ac56e52a0849d491",
  "maxActions": 128
}
```

클라이언트는 signature와 revision이 모두 일치할 때만 로컬 action cache를 재사용합니다. 캐시가 없거나 revision이 변경되면 `sync-request`를 요청합니다. Backend마다 고유 `server-signature`를 지정해야 합니다.

`server-signature`는 서버가 선택하는 로컬 설정 namespace이지 암호학적 서명이나 서버 인증 토큰이 아닙니다. 연결당 첫 유효 handshake가 signature를 고정하며, 같은 연결에서 다른 signature를 보내는 후속 handshake는 무시됩니다. 서버를 바꿀 때는 새 게임 연결에서 새 signature가 적용됩니다.

## 동적 키 목록

```json
{
  "revision": "ac56e52a0849d491",
  "maxActions": 128,
  "actions": [
    {
      "id": "skill.fireball",
      "name": "화염구",
      "defaultKey": "key.keyboard.z",
      "cooldownMs": 3000,
      "category": "combat",
      "holdable": false
    }
  ]
}
```

`actions` 배열의 각 항목은 독립적인 Minecraft `KeyMapping` 한 개가 됩니다. ID는 `[a-z0-9._-]`로 구성된 1~64자이며 목록에서 고유해야 합니다. 이름은 최대 128자입니다.

## 신뢰 경계와 자원 상한

서버에서 온 payload는 모두 신뢰할 수 없는 입력으로 취급됩니다. UTF-8 JSON payload는 최대 128 KiB, JSON 구조 중첩은 최대 32단계, 액션 목록은 서버가 선언한 상한과 절대 상한 256개를 넘을 수 없습니다. 클라이언트는 handshake burst 256개/초당 32개, sync burst 128개/초당 24개, feedback burst 128개/초당 64개의 token-bucket 상한을 적용합니다. 중복 revision은 다시 키를 등록하거나 캐시에 쓰지 않습니다.

액션 snapshot 캐시는 재생성할 수 있으므로 최대 128개 파일로 최근 사용순 정리되고, 같은 signature의 캐시 파일은 초당 최대 한 번 저장됩니다. 플레이어가 직접 바꾼 per-server 키 설정은 이 캐시 정리에 포함되지 않습니다. Paper 서버는 같은 tick 동안 발생한 여러 액션 변경을 최신 snapshot 하나로 합쳐 보냅니다. 이 동작은 `ActionRegistrationService` API 호출이나 기존 `max-actions` 설정에 새 제한을 추가하지 않습니다.

동적 조작 식별자는 `key.skm.<signature>.<action-id>`입니다. SKM 카테고리는 Minecraft category sort order의 **맨 앞**에 삽입되므로 설정 목록 최상단에 표시됩니다. 연결 해제/서버 전환 때 이전 서버의 key mapping은 Controls 목록과 내부 registry에서 제거됩니다.

## 입력과 서버 검증

```json
{"action":"skill.fireball","state":"press"}
```

입력에는 대상, 좌표, 피해량, 게임 자원, 레벨, 클라이언트 쿨다운 값을 포함하지 않습니다. 서버가 action 존재, 권한, 레벨 서비스, hold 상태, 속도 제한, 쿨다운을 검증합니다.

## Raw framing 확인

Hello payload JSON의 첫 byte는 `{` (`0x7b`)이어야 합니다. 모든 `skm:*` 채널은 동일한 raw UTF-8 JSON codec을 사용합니다.
