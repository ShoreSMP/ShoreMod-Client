"""Review tracked release inputs without printing secret values."""
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCKED_DIRS = {'.git', '.svn', '.hg', '.gradle', '.idea', '.vscode', '.codex',
                '.agents', '.aws', '.ssh', '.gnupg', '.kube', '__pycache__', 'build', 'dist', 'outputs', 'run', 'logs',
                'server', 'servers', 'world', 'worlds', 'screenshots', 'shoremod-clips'}
BLOCKED_FILES = {'local.properties', 'gradle-local.properties', 'server.properties',
                 'usercache.json', 'whitelist.json', 'ops.json', 'banned-players.json',
                 'banned-ips.json', '.netrc', '.npmrc', '.pypirc', '.git-credentials'}
BLOCKED_EXTS = {'.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.log', '.db',
                '.sqlite', '.sqlite3', '.bak', '.dump', '.zip', '.class', '.pyc'}
RULES = {
    'private key': re.compile(r'-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----'),
    'GitHub credential': re.compile(r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})'),
    'cloud access key': re.compile(r'(?:AKIA|ASIA)[A-Z0-9]{16}'),
    'service webhook': re.compile(r'https://(?:discord(?:app)?\.com/api/webhooks|hooks\.slack\.com/services)/[^\s\x22\x27]+'),
    'credential assignment': re.compile(r'''(?i)(?:password|passwd|api[_-]?key|client[_-]?secret|access[_-]?token|auth[_-]?token)\s*[:=]\s*["'][^"'\s]{8,}["']'''),
    'authenticated URL': re.compile(r'https?://[^\s/:@\x22\x27]+:[^\s/@\x22\x27]+@'),
    'authenticated query': re.compile(r'''(?i)[?&](?:access_token|api_key|client_secret|token|signature|x-amz-signature)=[A-Za-z0-9_%+./=-]{16,}'''),
    'private home path': re.compile(r'(?i)(?:[A-Z]:[\\/]Users[\\/][^\s\\/\x22\x27]+|/(?:Users|home)/[^\s/\x22\x27]+/)'),
}
PERSONAL_EMAIL = re.compile(r'(?i)[A-Z0-9_.+-]+@(?:gmail|outlook|hotmail|yahoo|icloud|protonmail)\.[A-Z.]+')


def tracked_paths(root=ROOT):
    data = subprocess.check_output(['git', 'ls-files', '-z', '--cached'], cwd=root)
    return sorted(set(data.decode('utf-8').rstrip('\0').split('\0'))) if data else []


def path_problem(name):
    path = PurePosixPath(name)
    parts = [part.lower() for part in path.parts]
    leaf = parts[-1] if parts else ''
    if path.is_absolute() or '..' in parts or '\\' in name or ':' in name:
        return 'unsafe path'
    if any(part in BLOCKED_DIRS for part in parts) or (parts and parts[0] == 'config'):
        return 'local/runtime directory'
    if leaf in BLOCKED_FILES or path.suffix.lower() in BLOCKED_EXTS:
        return 'sensitive/runtime filename'
    if (leaf == '.env' or leaf.startswith('.env.')) and leaf != '.env.example':
        return 'environment file'
    if leaf.startswith(('credentials', 'secrets')) or leaf.endswith('credentials.json'):
        return 'credential filename'
    if path.suffix.lower() == '.jar' and name != 'gradle/wrapper/gradle-wrapper.jar':
        return 'non-wrapper binary'
    return None


def audit(root, paths):
    problems = []
    root = root.resolve()
    for name in paths:
        problem = path_problem(name)
        if problem:
            problems.append((name, problem))
            continue
        file = root / name
        if file.is_symlink() or not file.is_file() or not file.resolve().is_relative_to(root):
            problems.append((name, 'missing file, symlink, or outside source root'))
            continue
        text = file.read_bytes().decode('utf-8', errors='replace')
        for label, pattern in RULES.items():
            if pattern.search(text):
                problems.append((name, label))
        # Keep the original, legally required attribution of third-party assets.
        third_party = '/licenses/' in name or name.startswith('gradle/') or name in {'gradlew', 'gradlew.bat', 'THIRD_PARTY.md'} or name.endswith('.ttf')
        if not third_party and PERSONAL_EMAIL.search(text):
            problems.append((name, 'personal email address'))
        if name == 'plugin/src/main/resources/config.yml':
            for key in ('developer-names', 'developer-uuids'):
                if not re.search(r'(?m)^' + key + r':\s*\[\]\s*$', text):
                    problems.append((name, 'nonempty default developer identity'))
            if re.search(r'(?m)^pinned-developers:', text):
                problems.append((name, 'default pinned developer identity'))
    return problems


def require_clean(root, paths):
    problems = audit(root, paths)
    if problems:
        # Report names/categories only. Never echo matched secret contents.
        for name, label in problems:
            print(f'BLOCKED: {name}: {label}', file=sys.stderr)
        raise SystemExit(1)
    if not paths:
        raise SystemExit('No tracked source files found')
    print(f'PASS: {len(paths)} tracked source files passed the public-source checks')


if __name__ == '__main__':
    require_clean(ROOT, tracked_paths())
