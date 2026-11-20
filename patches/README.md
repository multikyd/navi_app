# 공통 업데이트 코드

`common/com/kisa/transport`는 현재 두 APK에 포함된 업데이트·신호 진단 코드이고, `common/com/kisa/update`는 다운로드 폴더 저장·완료 알림 코드입니다. `NaviAppState`의 초기화, 액티비티 resume/pause와 전송 패킷 처리에서 호출합니다.

이 폴더만으로 원본 내비 앱 전체를 재빌드할 수는 없습니다. 원본 APK의 디컴파일 자료와 앱별 연결 코드, Android SDK 및 기존 서명 환경이 별도로 필요합니다. 원본 버전 변경 시 같은 DEX 번호라고 가정해서 교체하지 마세요.

`NaviUpdater.BUILD`, `VERSION`과 검증 보고서의 `update_build`를 함께 올린 뒤 `tools/package-updates.py`로 배포 파일과 목록을 만듭니다. 개인 주행 로그와 서명 비밀정보는 포함하지 않습니다.

`tmap-native`와 `naver-native`의 smali 패치는 대응 버전 디컴파일 자료의 DEX별 루트에서 `git apply --unidiff-zero`로 적용합니다.
