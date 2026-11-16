"""Package verified build_current.py outputs and generate the phone update catalog.

Run from any directory with --checks pointing to the verified builder report.
The compiled NaviUpdater.BUILD and this report's update_build must match.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import zipfile

REPO = Path(__file__).resolve().parents[1]
CERT = '0d774b5cb060ac8a7ccb64a6191803336204c3c986e353f7f941c49fa5282d3b'
CHUNK = 80 * 1024 * 1024
LIMIT = 100 * 1024 * 1024
PACKAGES = {'tmap': 'com.skt.tmap.ku', 'naver-map': 'com.nhn.android.nmap'}


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(data)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checks', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--notes', required=True)
    args = parser.parse_args()
    reports = json.loads(args.checks.read_text(encoding='utf-8-sig'))
    if len(reports) != 2:
        raise ValueError('Expected exactly two verified APK build reports')
    manifest_path = REPO / 'manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    rows = {row['id']: row for row in manifest['apps']}
    builds = set()
    inputs = {}
    for report in reports:
        apk = Path(report['apk']).resolve(strict=True)
        app = next((app for app in PACKAGES if apk.name == app + '-c4-arm64-updater.apk'), None)
        if app is None or app in inputs:
            raise ValueError('Unexpected or duplicate APK')
        if (report['sha256'] != sha(apk) or report['bytes'] != apk.stat().st_size or
                report['signing_certificate'] != CERT or not report['alignment_16kb'] or
                not report['other_entries_unchanged']):
            raise ValueError('APK does not match the verified builder report')
        build = int(report['update_build'])
        previous = rows[app].get('update_build', 0)
        if build < previous or (build == previous and rows[app]['apk']['sha256'] != report['sha256']):
            raise ValueError('Increase the compiled update build before changing an APK')
        with zipfile.ZipFile(apk) as archive:
            abis = {name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
            if abis != {'arm64-v8a'}:
                raise ValueError('Only ARM64 APKs are accepted')
        builds.add(build)
        inputs[app] = (apk, report)
    if len(builds) != 1 or set(inputs) != set(PACKAGES):
        raise ValueError('Both APKs must use the same update build')
    (REPO / 'dist').mkdir(exist_ok=True)
    # Build and verify both archives before replacing any distribution files.
    with tempfile.TemporaryDirectory(prefix='package-', dir=REPO / 'dist') as folder:
        staging = Path(folder)
        pending = []
        for app, (apk, report) in inputs.items():
            filename = app + '-c4-arm64.apk'
            archive = staging / (app + '-c4-arm64.zip')
            with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
                z.write(apk, filename)
            with zipfile.ZipFile(archive) as z, z.open(filename) as source:
                digest = hashlib.sha256()
                for data in iter(lambda: source.read(1024 * 1024), b''):
                    digest.update(data)
                if digest.hexdigest() != report['sha256']:
                    raise ValueError('Archive round trip failed')
            parts = []
            if archive.stat().st_size < LIMIT:
                paths = [archive]
            else:
                paths = []
                with archive.open('rb') as source:
                    index = 1
                    for data in iter(lambda: source.read(CHUNK), b''):
                        part = staging / (archive.name + f'.part{index:03d}')
                        part.write_bytes(data)
                        paths.append(part)
                        index += 1
            for part in paths:
                assert part.stat().st_size < LIMIT
                parts.append(dict(path='packages/' + part.name, bytes=part.stat().st_size, sha256=sha(part)))
                pending.append(part)
            row = rows[app]
            row.update(update_build=report['update_build'], modified_version=args.version,
                       package_name=PACKAGES[app], release_notes=args.notes,
                       apk=dict(filename=filename, bytes=apk.stat().st_size, sha256=report['sha256']),
                       archive=dict(format='zip', filename=archive.name, bytes=archive.stat().st_size,
                                    sha256=sha(archive), parts=parts))
        package_dir = REPO / 'packages'
        package_dir.mkdir(exist_ok=True)
        for part in pending:
            shutil.copyfile(part, package_dir / part.name)
        expected = {part.name for part in pending}
        for old in package_dir.iterdir():
            if old.is_file() and old.name not in expected and any(
                    old.name == app+'-c4-arm64.zip' or old.name.startswith(app+'-c4-arm64.zip.part') for app in PACKAGES):
                old.unlink()
        manifest.update(build=args.version + '-arm64', auto_download_wifi=True)
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps([dict(app=a, update_build=r['update_build'], parts=len(r['archive']['parts']))
                      for a, r in rows.items()], ensure_ascii=False))


if __name__ == '__main__':
    main()
