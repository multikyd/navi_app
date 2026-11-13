# 내비게이션 앱 · ARM64

수정된 티맵과 네이버지도를 ARM64 기기용으로 패키징한 저장소입니다.

| 앱 | 버전 | ARM64 APK | 저장소 압축 파일 |
|---|---|---:|---:|
| 티맵 | 11.8.2.4051 | 208.7MB | ZIP 97.8MB · 1개 |
| 네이버지도 | 6.11.2.2 | 234.8MB | ZIP 157.9MB · 2개로 분할 |

MB는 1,000,000바이트 기준입니다. GitHub 일반 Git 파일 제한은 100MiB(104,857,600바이트)이므로 티맵 ZIP은 하나로, 네이버 ZIP은 80MiB 단위의 두 파일로 보관합니다.

## APK 복원

1. 이 저장소를 Git으로 받거나 GitHub의 **Code → Download ZIP**으로 받아 압축을 풉니다.
2. `packages` 폴더와 파일 이름을 그대로 유지합니다.
3. **`restore-apks.cmd`를 실행**합니다.
4. 생성된 `dist` 폴더의 APK를 폰으로 옮겨 설치합니다.

- `dist/tmap-c4-arm64.apk`
- `dist/naver-map-c4-arm64.apk`

티맵은 `packages/tmap-c4-arm64.zip`을 일반 압축 해제로 풀어도 됩니다. 네이버지도는 `.part001`과 `.part002`가 모두 있어야 하며, 복원 스크립트가 자동으로 합칩니다. 분할 파일 자체는 설치용 APK가 아닙니다.

복원은 Windows 기본 PowerShell과 .NET으로 실행되며 별도 압축 프로그램이나 Python이 필요하지 않습니다. 각 파일, 합친 ZIP, 복원된 APK의 SHA256과 길이를 `manifest.json`과 대조합니다. 이미 정상 복원된 APK는 다시 덮어쓰지 않습니다.

명령줄에서 앱 하나만 복원하거나 검사할 수도 있습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\restore-apks.ps1 -App tmap
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\restore-apks.ps1 -App naver-map
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\restore-apks.ps1 -VerifyOnly
```

## 이번 패키지

- `armeabi-v7a` 라이브러리만 제거했습니다. `arm64-v8a` 라이브러리, DEX 코드, 지도·음성·화면 리소스와 AndroidManifest 내용은 이전 수정본과 동일합니다.
- 기존 Android Debug 인증서로 재서명하고, 서명과 네이티브 라이브러리의 16KB 정렬을 검증했습니다.
- 티맵의 목적지 제안 5초·안내 시작 10초 설정을 포함합니다.
- ARM64 기기 전용입니다. 32비트 ARM 전용 기기에는 설치할 수 없습니다.
- APK 원본은 저장소에 중복 커밋하지 않으며 복원 결과인 `dist`도 Git에서 제외합니다.

파일별 SHA256, APK 패키지 정보와 서명 인증서 SHA256은 `manifest.json`에 있습니다.

향후 앱을 자주 갱신하면 바이너리 변경 이력이 저장소에 계속 쌓입니다. 그때는 APK를 GitHub Releases로 배포하고 저장소에는 버전 정보만 두는 방식이 적합합니다. Releases는 파일당 2GiB 미만을 지원합니다.

- [GitHub 일반 파일 제한](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)
- [GitHub Releases 파일 제한](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)
