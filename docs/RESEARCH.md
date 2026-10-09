# 26.2 / Java 25 및 동적 키 구조 참고

Minecraft Java Edition 26.2 런타임 metadata는 Java 주 버전 25를 명시한다. Paper 플러그인과 Fabric 클라이언트 모두 Java 25 toolchain/release를 사용한다. [1]

Paper 공식 프로젝트 설정 가이드는 26.2 플러그인의 Java 25 toolchain, 26.2 API 의존성 예시를 제공하며, `plugin.yml`의 API version 설정을 설명한다. [2] [3]

Fabric 26.2 개발 문서는 JDK 25를 요구한다. 26.1부터 무난독화 game class 이름 체계를 쓰므로 본 버전 Fabric client mixin accessor는 26.2 class/field 명칭에 직접 적용된다. [4] [5]

Fabric 네트워킹 문서는 양방향 custom payload type/codec 등록과 서버 측 입력 검증을 설명한다. Paper plugin messaging은 등록된 채널을 통해 byte-array payload를 전송한다. [6] [7]

Fabric 키 매핑 helper는 게임 `Options`가 초기화된 뒤에는 신규 key mapping 등록을 거부하므로 동작 목록을 받기 전에 미리 몇 개의 임의 슬롯을 정하는 방식으로 구현하지 않았다. 대신 클라이언트 전용 Mixin accessor와 Minecraft `KeyMapping` registry를 통해 현재 서버가 보낸 action ID 목록과 똑같은 수의 mapping을 추가하고, disconnect 시 mapping·Options 목록을 복원/정리한다. 이 접근은 Minecraft 26.2 client 통합 테스트에서 검증해야 하므로 `docs/TESTING.md`의 server-switch와 Controls 목록 테스트를 배포 게이트로 둔다. [8]

각 backend의 signature가 action cache 및 key override 파일 경로의 namespace다. 키 선택 정보는 클라이언트 로컬 config이며 서버로 전송되지 않는다.

**References**

[1]: https://piston-meta.mojang.com/v1/packages/987b91a95ae93b3bb78cc14d6e0bbd31bad08d59/26.2.json
[2]: https://docs.papermc.io/paper/dev/project-setup/
[3]: https://docs.papermc.io/paper/dev/plugin-yml/
[4]: https://docs.fabricmc.net/develop/getting-started/setting-up
[5]: https://docs.fabricmc.net/develop/porting/mappings/
[6]: https://docs.fabricmc.net/develop/networking
[7]: https://docs.papermc.io/paper/dev/plugin-messaging/
[8]: https://docs.fabricmc.net/develop/key-mappings
