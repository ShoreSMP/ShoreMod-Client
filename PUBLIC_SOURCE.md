# Public source preparation

The 1.2.0 public snapshot excludes the private companion plugin source and its offline build helper. The remaining client and shared modules build independently. It also removes personal account names from example tests, documentation and the shipped developer-access defaults. No developer identity is enabled on a fresh plugin install. Required third-party authorship and license notices, product branding, public dependency repositories and public support-service URLs remain.

The review covered the current tracked text and 58 commits reachable from the four development branch heads, including 357 distinct historical text blobs. No credential-pattern matches were found in those reviewed blobs. This is a bounded review, not a guarantee that no secret can exist. Git author metadata and historical personal examples are excluded by distributing a snapshot rather than repository history.

## Export

Run these commands in a Git checkout:

```sh
python3 scripts/audit_public_source.py
python3 scripts/test_public_source.py
python3 scripts/package_source.py
```

The exporter uses only Git's tracked file list, audits tracked inputs and selects client/shared source, rejects symlinks and sensitive runtime/config filenames, and writes source under a single ShoreMod folder. Untracked files, Git metadata, credentials, local settings, logs, clips, player lists and build outputs cannot be pulled in by a recursive directory walk. The ZIP has no commit names, author emails, branches, tags, remotes or file ownership metadata. A separate checksum is generated alongside it.

Default developer lists must remain empty. Do not add real accounts, UUID grants, deployment secrets, webhook URLs, authenticated download links or production server configs to source. Use local configuration and the hosting service's secret store. The scanner rejects common credentials and credential-bearing URLs; public third-party license attribution is allowed.

## Publishing

Extract the audited ZIP into a **new** public repository and create new history there, using a public display name and a GitHub no-reply author address. Do not copy the original .git directory, issues, PRs, workflow artifacts or release attachments.

The original development repository stays private. Merely deleting a current file or adding it to .gitignore does not erase previous commits, other branches, tags, pull requests, logs, artifacts or caches. If a real credential is ever found in an older copy, rotate it before distribution; rewriting history alone cannot revoke it.

Existing private server configs and player-created data are outside this source archive. Published binaries must be built from the cleaned tree to obtain the empty developer defaults; older plugin JARs retain their earlier defaults.
