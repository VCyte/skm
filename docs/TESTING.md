# 프로토콜 v4 통합 테스트

## 준비

- Java 25 Paper 26.2 테스트 서버 2대: signature `skm-test-a`, `skm-test-b`
- 두 서버에 Paper 플러그인 설치
- Fabric Loader + Fabric API + SKM 모드가 설치된 Minecraft 26.2 클라이언트
- 키 입력 뒤 효과 확인이 필요하면 `ActionExecutionService` 테스트 구현

Paper 서버 JAR과 별도로 각 플레이어 PC에 Fabric API와 SKM Fabric client JAR이 설치되어야 합니다. 서버 `/pl` 목록은 client mod 상태를 보여 주지 않습니다.

## 접속 handshake 로그 확인

1. Fabric client `logs/latest.log`에 `SKM client initialized`가 있는지 확인합니다.
2. 접속 후 약 1초 뒤 `Sent SKM hello`가 출력되어야 하고, Paper 콘솔에는 `Received SKM hello`가 기록되어야 합니다.
3. 클라이언트 로그에서 `Received SKM handshake`, `Synchronized N ... action(s)`, `Registered N ... key binding(s)` 순서로 확인합니다.
4. 클라이언트의 hello body가 `{`로 시작하고 앞에 길이 VarInt가 없어야 합니다. 서버가 Hello JSON을 수신하면 `Received SKM hello` 로그를 기록합니다.
5. 5회 hello 후 서버 응답이 없으면 클라이언트 로그에 설치·버전 안내가 남습니다. 이때는 컨트롤 키 목록을 검사하기 전에 Fabric API/mod 설치와 프로토콜 버전부터 바로잡습니다.

## 액션 수만큼 키가 생기는지 확인

1. 서버 A `actions.yml`에 action 3개, 서버 B에는 action 1개를 준비합니다. 슬롯 필드를 넣지 않습니다.
2. `max-actions`가 최소 등록 개수 이상인지 확인합니다.
3. 서버 A에 접속해 `설정 → 조작`을 엽니다. 서버 signature만 제목으로 표시되는 카테고리가 키 목록의 맨 위에 있고, 서버 A의 세 action 이름이 각각 한 키로 보여야 합니다.
4. 키 하나를 플레이어가 직접 바꾸고 세 키 모두 입력되는지 확인합니다.
5. 액션 목록을 3개에서 2개로 수정하고 `/skm reload` 또는 감시 갱신을 기다립니다. revision 변경 sync 뒤 삭제된 키가 사라져야 합니다.
6. 액션을 0개로 둔 경우 SKM key mapping이 하나도 노출되지 않아야 합니다.

## 서버 전환과 서명별 설정 격리

1. 서버 A의 action `skill.test`에 Z를 지정하고 플레이어가 Q로 재지정합니다.
2. 서버 A에서 나가면 액션 키가 Controls 목록에서 사라져야 합니다.
3. 서버 B에 접속하면 B의 액션만 보여야 합니다. A의 이름/키가 보이면 실패입니다.
4. B에서 같은 action ID `skill.test`를 사용하되 이름과 기본키를 다르게 둡니다. B의 독립 키가 보여야 합니다.
5. A로 돌아와 `skill.test` 키가 Q로 복구되는지 확인합니다. B의 override는 B에서만 복구되어야 합니다.
6. 클라이언트 파일을 확인합니다: `config/skm/servers/skm-test-a.json`와 `...test-b.json`이 분리되어야 합니다.

## 캐시 및 라이브 갱신

1. 서버 A 최초 접속에서 sync 후 로컬 `cache/<signature>.json`이 생성되는지 확인합니다.
2. 같은 signature와 revision으로 재접속하면 캐시 적중 뒤에도 같은 키 목록이 바로 적용되어야 합니다.
3. 서버 action name/default key를 바꿔 revision이 바뀌면 서버 정보가 갱신되어야 합니다. 사용자가 직접 변경한 per-signature 키는 보존합니다.
4. 유효하지 않은 `actions.yml`로 reload를 시도하면 이전 정상 목록은 유지되어야 합니다.

## 연결 수명주기·호환성

| 조건 | 기대 결과 |
|---|---|
| SKM 없는 일반 서버 | 액션 키가 하나도 보이지 않고 일반 플레이 유지 |
| Paper 서버에서 disconnect | 서버 액션 메뉴 항목 즉시 사라짐, 메모리의 동적 key registry도 정리 |
| A에서 B로 전환 | B signature 목록만 표시, 입력은 B 액션 ID에만 전달 |
| 클라이언트 v3 / 서버 v4 | protocol mismatch로 비활성화, 오류 안내 로그 |
| v4 서버 액션 목록 0개 | 빈 동기화 성공, 동적 key mapping 0개 |
| 구성된 최대치 초과 | 설정/registry 적용 거부, 마지막 정상 목록 유지 |
| 256 action 하드 상한 | 256 이하만 가능; 128 KiB 초과 payload는 거부 |

## 서버 권위 검사

- 권한이 없는 플레이어의 키 입력에서 실행 서비스가 호출되지 않아야 합니다.
- `required-level > 0`에서 `LevelRequirementService`가 없으면 거부돼야 합니다.
- 잘못된 action ID·state는 무시됩니다.
- 패킷에는 action ID와 press/release만 포함되고 좌표·대상·피해·마나가 없어야 합니다.
- 쿨다운 중 재입력은 서버가 거부하고 feedback을 보냅니다.

## 배포 기준

- [ ] `./gradlew clean build`가 Java 25에서 성공
- [ ] Paper와 Fabric의 protocol version이 모두 4
- [ ] 서버 signature만 제목으로 표시하는 key category가 Controls sort order 맨 앞에 표시
- [ ] Hello 및 server-to-client payload가 raw JSON bytes이고 length prefix 없음
- [ ] Paper 설정의 `server-signature` 및 `max-actions` 검증
- [ ] 액션 수와 Controls 키 개수가 정확히 일치
- [ ] 다른 signature로 이동 시 서버별 목록과 키 설정 분리
- [ ] disconnect 시 서버 키 목록이 비고 vanilla/mod 다른 키들은 보존
- [ ] 통합 환경에서 Fabric client mixin accessor가 정상 초기화됨
