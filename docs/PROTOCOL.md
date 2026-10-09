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

동적 조작 식별자는 `key.skm.<signature>.<action-id>`입니다. SKM 카테고리는 Minecraft category sort order의 **맨 앞**에 삽입되므로 설정 목록 최상단에 표시됩니다. 연결 해제/서버 전환 때 이전 서버의 key mapping은 Controls 목록과 내부 registry에서 제거됩니다.

## 입력과 서버 검증

```json
{"action":"skill.fireball","state":"press"}
```

입력에는 대상, 좌표, 피해량, 게임 자원, 레벨, 클라이언트 쿨다운 값을 포함하지 않습니다. 서버가 action 존재, 권한, 레벨 서비스, hold 상태, 속도 제한, 쿨다운을 검증합니다.

## Raw framing 확인

Hello payload JSON의 첫 byte는 `{` (`0x7b`)이어야 합니다. 모든 `skm:*` 채널은 동일한 raw UTF-8 JSON codec을 사용합니다.
