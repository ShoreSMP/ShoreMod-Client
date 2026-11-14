"""Exercise release-export boundaries with throwaway repositories."""
from pathlib import Path
import subprocess
import tempfile
import zipfile
from audit_public_source import audit, path_problem
from package_source import export_source


def git(root, *args):
    subprocess.run(['git', *args], cwd=root, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def run():
    for name in ('.git/config', '.env', '.env.production', 'logs/latest.log', 'server/server.properties',
                 'world/playerdata/a.dat', 'config/shoremod.json', 'ops.json', 'private.pem',
                 'build/output.txt', '../escape', 'credentials.json', 'plugin.jar'):
        assert path_problem(name), name
    for name in ('README.md', '.gitignore', '.github/workflows/build.yml',
                 'plugin/src/main/resources/config.yml', 'gradle/wrapper/gradle-wrapper.jar'):
        assert not path_problem(name), name
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        git(root, 'init', '-q')
        (root / 'README.md').write_text('Public source\n', encoding='utf-8')
        git(root, 'add', 'README.md')
        (root / '.env').write_text('untracked local setting', encoding='utf-8')
        (root / 'local-note.txt').write_text('Do not ship this', encoding='utf-8')
        (root / 'plugin').mkdir()
        (root / 'plugin/private-implementation.java').write_text('private companion source', encoding='utf-8')
        git(root, 'add', 'plugin/private-implementation.java')
        out = root / 'build/source.zip'
        export_source(root, out)
        with zipfile.ZipFile(out) as archive:
            assert archive.namelist() == ['ShoreMod/README.md']
            assert archive.getinfo('ShoreMod/README.md').date_time == (1980, 1, 1, 0, 0, 0)
            assert archive.comment == b''
        git(root, 'add', '-f', '.env')
        try:
            export_source(root, out)
            raise AssertionError('A tracked environment file was exported')
        except SystemExit:
            pass
        git(root, 'rm', '--cached', '.env')
        fixtures = [
            'gh' + 'p_' + 'a' * 36,
            '-----BEGIN ' + 'PRIVATE KEY-----',
            'https://discord.com/api/' + 'webhooks/123/test',
            'password' + '=\x22' + 'long-example-value\x22',
            'https://user:' + 'example-password@example.org',
            'https://example.org/?access_' + 'token=' + 'a' * 24,
            'someone' + '@gmail.com',
            'C:' + '\\Users\\' + 'Example\\Documents',
        ]
        for fixture in fixtures:
            (root / 'fixture.txt').write_text(fixture, encoding='utf-8')
            assert audit(root, ['fixture.txt']), 'Sensitive fixture was not detected'
        config = root / 'plugin/src/main/resources/config.yml'
        config.parent.mkdir(parents=True)
        config.write_text('developer-names: []\ndeveloper-uuids: []\n', encoding='utf-8')
        assert not audit(root, ['plugin/src/main/resources/config.yml'])
        config.write_text('developer-names:\n  - ExamplePlayer\ndeveloper-uuids: []\n', encoding='utf-8')
        assert audit(root, ['plugin/src/main/resources/config.yml'])
    print('PASS: public-source export excludes local data and blocks sensitive tracked inputs')


if __name__ == '__main__':
    run()
