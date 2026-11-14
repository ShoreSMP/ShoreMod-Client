"""Export audited tracked source only; never walk the working directory."""
from pathlib import Path
import hashlib
import zipfile
from audit_public_source import ROOT, require_clean, tracked_paths


def export_source(root, output):
    paths = tracked_paths(root)
    require_clean(root, paths)
    # The companion plugin stays private; the offline helper only builds that plugin.
    paths = [name for name in paths if not name.startswith('plugin/') and name != 'scripts/verify_local.py']
    # Snapshot the audited bytes before creating the archive.
    payload = [(name, (root / name).read_bytes()) for name in paths]
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in payload:
            info = zipfile.ZipInfo('ShoreMod/' + name, date_time=(1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.zip.sha256').write_text(digest + '  ' + output.name + '\n', encoding='utf-8')
    print('Exported', output.name, 'with', len(paths), 'files and no Git history')
    print('SHA256:', digest)
    return output


if __name__ == '__main__':
    export_source(ROOT, ROOT / 'build/distributions/ShoreMod-Public-Source.zip')
