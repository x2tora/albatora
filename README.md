<div align="center">
  <img src="assets/icon.png" alt="AlbaTora" width="88" height="88" />

  <h1>알바토라</h1>
  <p>실시간 급여 트래킹과 정규·단기·일용직 급여 정산을 위한 안드로이드 앱</p>

  <p>
    <a href="https://github.com/x2tora/albatora/releases/latest">
      <img src="https://img.shields.io/badge/Download_for_Android-Latest_Release-38BDF8?style=for-the-badge&logo=android&logoColor=white" alt="최신 버전 다운로드" />
    </a>
  </p>

  <p>
    <a href="https://github.com/x2tora/albatora/releases/latest">
      <img src="https://img.shields.io/github/v/release/x2tora/albatora?style=flat-square&label=Version&color=3b82f6" alt="Latest Version" />
    </a>
    <a href="https://github.com/x2tora/albatora/releases">
      <img src="https://img.shields.io/github/downloads/x2tora/albatora/total?style=flat-square&label=Downloads&color=64748b" alt="Total Downloads" />
    </a>
  </p>

  <p>
    <a href="https://github.com/x2tora/albatora/releases">모든 릴리즈</a>
    &nbsp;·&nbsp;
    <a href="https://github.com/x2tora/albatora/issues">이슈 제보</a>
  </p>
</div>

---

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| **하이브리드 실시간 트래커** | 등록된 정규 근무 시간에 맞춰 자동 작동하며, 대타·단기·연장 근무는 **[즉시 출근]** 스톱워치로 바로 기록합니다. |
| **수당·휴게 자동 반영** | 야간·연장·휴일 1.5배 수당 가속, 고정·비례 무급 휴게 차감, 상단바 실시간 알림 제어를 지원합니다. |
| **단기·일용직·미수금 관리** | 10초 퀵 등록과 프리셋 도장으로 빠르게 기록하고, 교통비·식비·수수료·세금 공제 후 순수익과 미수금을 관리합니다. |
| **한국형 급여·정산 규칙** | 주 15시간 주휴수당 자동 계산, 시급 변경 이력 보존, 맞춤 정산 기간, 공휴일 보정 월급날 D-Day 안내를 제공합니다. |
| **OneUI 스타일 위젯 4종** | 실시간 타이머(4×2), 슬림 타이머 바(4×1), 주간 일정(4×2), 월간 요약(2×2) 위젯과 위젯 스튜디오 커스터마이징을 지원합니다. |
| **달력·명세서·계산기** | 스와이프 달력과 원탭 퀵 액션, 텍스트 급여명세서 복사, 4종 급여 계산기, 위시리스트 달성률 게이지를 제공합니다. |
| **로컬 백업·인앱 업데이트** | 모든 기록을 JSON 파일이나 클립보드 텍스트로 내보내기/복원하며, GitHub Releases 최신 버전을 앱 안에서 바로 업데이트합니다. |

## 시작하기

### 1. 설치

1. 상단의 **Download for Android** 버튼 또는 [최신 릴리즈](https://github.com/x2tora/albatora/releases/latest)에서 기기에 맞는 APK를 내려받아 설치합니다.
2. 별도의 플레이 스토어 서명이 없는 오픈소스 배포본이므로 최초 설치 시 **출처를 알 수 없는 앱 설치 허용** 안내가 나타날 수 있습니다. 허용 후 설치를 진행하세요.

| 파일 이름 | 추천 기기 |
| --- | --- |
| **`albatora-v1.3.1.3_hotfix-arm64-v8a.apk`** | 대부분의 최신 안드로이드 스마트폰 (가장 추천) |
| `albatora-v1.3.1.3_hotfix-armeabi-v7a.apk` | 구형 32비트 안드로이드 기기 |
| `albatora-v1.3.1.3_hotfix-x86_64.apk` / `x86.apk` | 일부 태블릿·크롬북·에뮬레이터 |
| `albatora-v1.3.1.3_hotfix-universal.apk` | 기기 사양을 잘 모를 때 (모든 기기 호환 통합본) |

### 2. 권장 초기 설정

1. **더보기 · 설정**에서 기본 최저시급(기본값 2026년 10,320원)과 테마 스타일(**기본 / Material You / 커스텀**)을 원하는 대로 맞춥니다.
2. **정규 알바 추가** 또는 **단기 10초 등록**으로 근무지를 등록하고, 상단바 실시간 수익 알림을 사용하려면 첫 출근 시 알림 권한을 허용하세요.

## 데이터 처리 및 보안

- 모든 근무 기록, 알바 설정, 프리셋, 목표 금액은 외부 서버를 거치지 않고 기기 내부 저장소에만 보관됩니다.
- 네트워크 통신은 앱 실행 시 GitHub Releases(`api.github.com`)에서 최신 버전을 확인하고 APK를 내려받는 용도로만 사용됩니다.

## 로컬 개발 및 빌드

JDK 21 및 Android SDK(compileSdk 37 / targetSdk 36) 환경에서 빌드할 수 있습니다.

```bash
./gradlew testDebugUnitTest   # 단위 테스트 실행
./gradlew assembleDebug       # 디버그 APK 빌드
./gradlew assembleRelease     # R8 프로덕션 멀티 ABI APK 빌드
```

## 안내

AlbaTora는 독립적인 오픈소스 유틸리티이며, 별도의 세무·노무 법률 자문을 대체하지 않습니다.
