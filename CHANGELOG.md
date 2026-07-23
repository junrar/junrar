# [8.0.0](https://github.com/junrar/junrar/compare/v7.6.1...v8.0.0) (2026-07-23)
## 🚀 Features
**api**
- 🚨 remove UnsupportedRarV5Exception: *UnsupportedRarV5Exception is removed. It has not been
thrown since RAR5 extraction landed; callers catching it should catch
RarException, or UnsupportedRarVersionException for a version this library
genuinely does not implement.* ([823b42d](https://github.com/junrar/junrar/commits/823b42d)), closes [#289](https://github.com/junrar/junrar/issues/289)

**archive**
- parse RAR5 main/end/crypt + file/service headers (M3.3) ([9205d23](https://github.com/junrar/junrar/commits/9205d23)), closes [#24](https://github.com/junrar/junrar/issues/24)
- add RAR5 block-header framework (M3.2) ([9a1916a](https://github.com/junrar/junrar/commits/9a1916a)), closes [#23](https://github.com/junrar/junrar/issues/23)
- add vint reader, signature dispatch + SFX scan (M3.1) ([4eb0a95](https://github.com/junrar/junrar/commits/4eb0a95)), closes [#22](https://github.com/junrar/junrar/issues/22)

**crypt**
- RAR5 Blake2s/Blake2sp + DataHash hash seam (M3.5) ([938b8fa](https://github.com/junrar/junrar/commits/938b8fa)), closes [#26](https://github.com/junrar/junrar/issues/26)
- RAR5 KDF, pswcheck, header + data decryption (M3.4) ([faae8cc](https://github.com/junrar/junrar/commits/faae8cc)), closes [#25](https://github.com/junrar/junrar/issues/25)

**links**
- RAR5 REDIR extraction + three symlink-safety layers (M3.10) ([410a646](https://github.com/junrar/junrar/commits/410a646)), closes [#31](https://github.com/junrar/junrar/issues/31)

**rar5**
- segment the window lazily above 1 GB, capability 64 GB (M4.3) ([85b61ad](https://github.com/junrar/junrar/commits/85b61ad)), closes [#35](https://github.com/junrar/junrar/issues/35)
- decode RAR7 extended distances and route version 70 (M4.2) ([434de0e](https://github.com/junrar/junrar/commits/434de0e)), closes [#34](https://github.com/junrar/junrar/issues/34)
- parse RAR7 compression info and gate its dictionary (M4.1) ([345c291](https://github.com/junrar/junrar/commits/345c291)), closes [#33](https://github.com/junrar/junrar/issues/33) [#34](https://github.com/junrar/junrar/issues/34)
- 🚨 lift the V5 extraction gate, delete the pre-gate harness (M3.11): *RAR5 archives now open and extract instead of throwing
UnsupportedRarV5Exception; the exception class is deprecated.

Refs #32* ([dd64124](https://github.com/junrar/junrar/commits/dd64124)), closes [#32](https://github.com/junrar/junrar/issues/32)

**unpack**
- RAR5 filters (DELTA/E8/E8E9/ARM) + sweep topology (M3.8) ([8eaea37](https://github.com/junrar/junrar/commits/8eaea37)), closes [#29](https://github.com/junrar/junrar/issues/29)
- RAR5 Unpack5 decode loop + engine lifecycle (M3.7) ([5abb2d8](https://github.com/junrar/junrar/commits/5abb2d8)), closes [#28](https://github.com/junrar/junrar/issues/28)
- RAR5 Unpack5 skeleton — window, tables, block header (M3.6) ([ee6386a](https://github.com/junrar/junrar/commits/ee6386a)), closes [#27](https://github.com/junrar/junrar/issues/27)

**volume**
- RAR5 multi-volume spanning + typed volume errors (M3.9) ([3308906](https://github.com/junrar/junrar/commits/3308906)), closes [#30](https://github.com/junrar/junrar/issues/30)

**unscoped**
- verify RAR3 header CRCs, refuse extracting broken FILE headers ([a0870d3](https://github.com/junrar/junrar/commits/a0870d3)), closes [#12](https://github.com/junrar/junrar/issues/12)
- add ArchiveOptions construction-time configuration API (P0.8) ([a9f5af7](https://github.com/junrar/junrar/commits/a9f5af7))

## 🐛 Fixes
**archive**
- add unrar's second, unconditional FILE/SERVICE header-CRC check ([9eca3ec](https://github.com/junrar/junrar/commits/9eca3ec)), closes [#12](https://github.com/junrar/junrar/issues/12) [#38](https://github.com/junrar/junrar/issues/38)
- reject backward-seeking RAR5 DataSize vint ([8974da5](https://github.com/junrar/junrar/commits/8974da5)), closes [#23](https://github.com/junrar/junrar/issues/23)

**build**
- pin compileTestJava release=21 so ArchUnit can parse test bytecode ([4600a08](https://github.com/junrar/junrar/commits/4600a08)), closes [#36](https://github.com/junrar/junrar/issues/36)

**rar3**
- decode narrow names as UTF-8 when the bytes are valid ([e00b6c5](https://github.com/junrar/junrar/commits/e00b6c5)), closes [#44](https://github.com/junrar/junrar/issues/44)

**rar5**
- widen the split-before guard to any RAR5-container entry ([30d1aaf](https://github.com/junrar/junrar/commits/30d1aaf)), closes [#30](https://github.com/junrar/junrar/issues/30) [#43](https://github.com/junrar/junrar/issues/43)
- route unknown-algorithm-version entries by container, not family ([57d38d8](https://github.com/junrar/junrar/commits/57d38d8)), closes [#43](https://github.com/junrar/junrar/issues/43)
- wrap window positions instead of masking them (M4.3) ([c60795b](https://github.com/junrar/junrar/commits/c60795b)), closes [#35](https://github.com/junrar/junrar/issues/35)

**rarfile**
- bounds-check encname decode and drop method 36 ([47198c5](https://github.com/junrar/junrar/commits/47198c5)), closes [#19](https://github.com/junrar/junrar/issues/19)

**unpack**
- zero-fill distance-into-void via FirstWinDone tracking (7.0.3) ([4c59fe5](https://github.com/junrar/junrar/commits/4c59fe5)), closes [#18](https://github.com/junrar/junrar/issues/18)
- mask RAR1.5 LongLZ distance write index ([a81a5e9](https://github.com/junrar/junrar/commits/a81a5e9)), closes [#17](https://github.com/junrar/junrar/issues/17)
- compare RAR filter fields unsigned ([65fe437](https://github.com/junrar/junrar/commits/65fe437))
- reject unsigned delta sizes ([d2d0f72](https://github.com/junrar/junrar/commits/d2d0f72))
- reject unsigned filter positions ([146e15b](https://github.com/junrar/junrar/commits/146e15b))
- bound RAR3 delta channels ([1cfdccd](https://github.com/junrar/junrar/commits/1cfdccd))
- guard RAR 1.5 flags index ([22f95f9](https://github.com/junrar/junrar/commits/22f95f9))
- bound RAR3 filter stacks ([4f36413](https://github.com/junrar/junrar/commits/4f36413))

**unscoped**
- guard PPM state arrays ([af0af77](https://github.com/junrar/junrar/commits/af0af77))
- latch nested PPM decode errors ([146bc6b](https://github.com/junrar/junrar/commits/146bc6b))
- mark *.bin oracle fixtures binary to survive Windows checkout ([ea03470](https://github.com/junrar/junrar/commits/ea03470)), closes [#37](https://github.com/junrar/junrar/issues/37)
- give v20-solid-negative-backref.rar real header CRCs, not 0x0000 ([16a5fc8](https://github.com/junrar/junrar/commits/16a5fc8))
- decode plain-ANSI names byte-transparently, not as UTF-8 (T6 fix round) ([1171d33](https://github.com/junrar/junrar/commits/1171d33))
- decode ANSI names with explicit UTF-8, port whole-name branch (T6) ([a58a48a](https://github.com/junrar/junrar/commits/a58a48a))
- serialize RAR3 KDF passwords as UTF-16LE, not platform charset (T4) ([90e6840](https://github.com/junrar/junrar/commits/90e6840))
- promote unpSize sentinel to INT64MAX (T1) ([1e32b05](https://github.com/junrar/junrar/commits/1e32b05))
- honor PPM MaxMB verbatim per unrar policy (S8/P0.5) ([e23273d](https://github.com/junrar/junrar/commits/e23273d))
- correct ProtectHeader layout to unrar SIZEOF_PROTECTHEAD 26 (T2) ([266f2bb](https://github.com/junrar/junrar/commits/266f2bb))

## 🔄️ Changes
**vm**
- delete the dead RAR3 bytecode interpreter (M2.2) ([e2287ab](https://github.com/junrar/junrar/commits/e2287ab)), closes [#20](https://github.com/junrar/junrar/issues/20) [#21](https://github.com/junrar/junrar/issues/21)
- replace RAR3 interpreter with 5.5.1 fingerprint recognition ([b1ca0f2](https://github.com/junrar/junrar/commits/b1ca0f2)), closes [#20](https://github.com/junrar/junrar/issues/20)

**unscoped**
- 🚨 rename deprecated methods in FileHeader: *remove deprecated FileHeader#getFileNameString and FileHeader#getFileNameW* ([c78e224](https://github.com/junrar/junrar/commits/c78e224))
- 🚨 remove deprecated BaseBlock#getHeaderSize(): *remove deprecated BaseBlock#getHeaderSize()* ([81dd982](https://github.com/junrar/junrar/commits/81dd982))
- apply Spotless formatting ([9ec0986](https://github.com/junrar/junrar/commits/9ec0986))
- ci: keep the regression test in its own workflow ([0f6ddaa](https://github.com/junrar/junrar/commits/0f6ddaa)), closes [#289](https://github.com/junrar/junrar/issues/289)

## 🧪 Tests
**archive**
- pin encrypted-header fatal-at-open to Archive construction ([d86980e](https://github.com/junrar/junrar/commits/d86980e)), closes [#38](https://github.com/junrar/junrar/issues/38)
- pin short-input rejection, refresh stale VC snapshot ([8712039](https://github.com/junrar/junrar/commits/8712039)), closes [#42](https://github.com/junrar/junrar/issues/42) [#288](https://github.com/junrar/junrar/issues/288)

**corpus**
- add M3.11 three-shape audit + all-members oracle-check scripts ([b099bcc](https://github.com/junrar/junrar/commits/b099bcc)), closes [#32](https://github.com/junrar/junrar/issues/32)
- flip 345 RAR5 member expectations for the lifted V5 gate ([6f76df5](https://github.com/junrar/junrar/commits/6f76df5)), closes [#32](https://github.com/junrar/junrar/issues/32)

**ppm**
- assert reflected unpack/ppm non-null in heap dump oracle ([0e0532d](https://github.com/junrar/junrar/commits/0e0532d))

**rar5**
- make two vint-guard tests earn their names ([0912a82](https://github.com/junrar/junrar/commits/0912a82))
- pin the solid-window grow-mid-set rejection ([4bc018c](https://github.com/junrar/junrar/commits/4bc018c))
- pin file-header compat quirks and crypt-record guards ([85361f3](https://github.com/junrar/junrar/commits/85361f3))
- pin main-header hostile-input guards ([3272828](https://github.com/junrar/junrar/commits/3272828))

**regression**
- ship a payload-stripped corpus, drop the 7 GB download ([82f54fa](https://github.com/junrar/junrar/commits/82f54fa)), closes [#41](https://github.com/junrar/junrar/issues/41)

**unpack**
- drive M1.4 first-window distance-into-void from hostile fixtures ([7ed0a2d](https://github.com/junrar/junrar/commits/7ed0a2d)), closes [#18](https://github.com/junrar/junrar/issues/18)
- drive M1.3 filter/channel/flags limits from hostile archives ([39d5b8e](https://github.com/junrar/junrar/commits/39d5b8e)), closes [#17](https://github.com/junrar/junrar/issues/17)
- pin RAR 1.5 flags boundary ([d2cf4eb](https://github.com/junrar/junrar/commits/d2cf4eb))
- pin RAR3 filter upper bound ([f67e7ae](https://github.com/junrar/junrar/commits/f67e7ae))

**unscoped**
- make every suite runnable on a real JVM 8 toolchain ([6ca1348](https://github.com/junrar/junrar/commits/6ca1348)), closes [#289](https://github.com/junrar/junrar/issues/289)
- reconcile upstream regression tests with this branch (R6 sync) ([fb62ab3](https://github.com/junrar/junrar/commits/fb62ab3)), closes [#5](https://github.com/junrar/junrar/issues/5) [#12](https://github.com/junrar/junrar/issues/12)
- document PPM fixture provenance ([3cff6b6](https://github.com/junrar/junrar/commits/3cff6b6))
- add PPMd heap dump oracle ([5a540ce](https://github.com/junrar/junrar/commits/5a540ce))
- refresh 7 corpus reference JSONs for T6 byte-transparent decode ([3efb1bc](https://github.com/junrar/junrar/commits/3efb1bc)), closes [#11](https://github.com/junrar/junrar/issues/11) [#38](https://github.com/junrar/junrar/issues/38)
- discriminate builder password copy; document null-literal ambiguity (P0.8 fix round) ([7a5f90f](https://github.com/junrar/junrar/commits/7a5f90f)), closes [#13](https://github.com/junrar/junrar/issues/13)
- pin C15 signedness class rule + audit ledger (P0.4) ([2eae244](https://github.com/junrar/junrar/commits/2eae244))
- pin C13 long-clean lengths and D1 byte[] refusal (P0.3) ([9f768e6](https://github.com/junrar/junrar/commits/9f768e6))
- pin C7 protect-header seek-past-data ([2fee927](https://github.com/junrar/junrar/commits/2fee927))
- pin C1 solid-v20 negative back-ref (P0.1) ([ff86dda](https://github.com/junrar/junrar/commits/ff86dda))

## 🛠  Build
**regression**
- retry transient corpus download failures ([1d39cc3](https://github.com/junrar/junrar/commits/1d39cc3))
- fetch corpus by id via gdown with caching ([5b6ec04](https://github.com/junrar/junrar/commits/5b6ec04))

**unscoped**
- drop dead JGit dependency constraint ([258a0b0](https://github.com/junrar/junrar/commits/258a0b0))
- enable Spotless with JGit-free parameters ([0ccc14c](https://github.com/junrar/junrar/commits/0ccc14c))
- gate release on JRE smoke ([ec5a10a](https://github.com/junrar/junrar/commits/ec5a10a))
- run the unit and regression suites on a Java 8/25 matrix ([3023d06](https://github.com/junrar/junrar/commits/3023d06)), closes [#289](https://github.com/junrar/junrar/issues/289)
- build and test on JDK 25, pin the regression harness bytecode ([c168901](https://github.com/junrar/junrar/commits/c168901))
- compile the smoke runner through a Gradle source set ([904f2a4](https://github.com/junrar/junrar/commits/904f2a4))
- build the smoke jar with Gradle on JDK 25, keep the JRE 8/25 matrix ([4c8f1c7](https://github.com/junrar/junrar/commits/4c8f1c7)), closes [#36](https://github.com/junrar/junrar/issues/36)
- build the smoke jar with a real JDK 8 and run it on JRE 8 and 25 ([f7b1704](https://github.com/junrar/junrar/commits/f7b1704)), closes [#289](https://github.com/junrar/junrar/issues/289)
- keep the regression test in its own workflow ([544b18d](https://github.com/junrar/junrar/commits/544b18d))
- fold the regression test into the main CI workflow ([560f07d](https://github.com/junrar/junrar/commits/560f07d)), closes [#289](https://github.com/junrar/junrar/issues/289)
- compile the jre8 smoke runner with explicit -encoding UTF-8 ([43c0afe](https://github.com/junrar/junrar/commits/43c0afe))
- add JRE 8 runtime extraction smoke job (P0.9) ([d503bff](https://github.com/junrar/junrar/commits/d503bff))

## 📝 Documentation
**api**
- explain why getFileCRC() stays a signed int, and pin it ([3b63fa3](https://github.com/junrar/junrar/commits/3b63fa3)), closes [#260](https://github.com/junrar/junrar/issues/260) [#289](https://github.com/junrar/junrar/issues/289)

**contributing**
- keep the original corpus URL for regeneration ([7515dfc](https://github.com/junrar/junrar/commits/7515dfc)), closes [#289](https://github.com/junrar/junrar/issues/289)

**porting**
- cite port-tracker issues by PT-NN appendix keys ([cd6323d](https://github.com/junrar/junrar/commits/cd6323d)), closes [#289](https://github.com/junrar/junrar/issues/289)
- make issue citations survive the port tracker ([d6d3aff](https://github.com/junrar/junrar/commits/d6d3aff)), closes [#31](https://github.com/junrar/junrar/issues/31) [#40](https://github.com/junrar/junrar/issues/40) [#289](https://github.com/junrar/junrar/issues/289)
- stop claiming checkstyle enforces the house rules ([d9515ab](https://github.com/junrar/junrar/commits/d9515ab)), closes [#289](https://github.com/junrar/junrar/issues/289)

**rar5**
- close #40 FHEXTRA_REDIR link-path divergences as keep ([6c40209](https://github.com/junrar/junrar/commits/6c40209)), closes [#40](https://github.com/junrar/junrar/issues/40)

**unscoped**
- update supported RAR version in README ([6c5ca7d](https://github.com/junrar/junrar/commits/6c5ca7d))
- remap two commit citations the markdown-only sweep missed ([ad35e1b](https://github.com/junrar/junrar/commits/ad35e1b))
- remap commit citations orphaned by the upstream rebase ([576dbc0](https://github.com/junrar/junrar/commits/576dbc0))
- correct the M3.10 commit citation in no-go row D3 ([fca2a6c](https://github.com/junrar/junrar/commits/fca2a6c)), closes [#40](https://github.com/junrar/junrar/issues/40)
- add missing javadoc summary on BaseBlock#setBrokenHeader ([c66cd00](https://github.com/junrar/junrar/commits/c66cd00))
- record T6 ISO-8859-1 decision and T4 non-BMP divergence (manual §6) ([6ffc2a0](https://github.com/junrar/junrar/commits/6ffc2a0))
- qualify P0.8 additive claim in plan §5.4 (source-level null-literal caveat) ([656f501](https://github.com/junrar/junrar/commits/656f501)), closes [#13](https://github.com/junrar/junrar/issues/13)
- reconcile S8 resolution and clear residual UNPINNED markers after P0.5 ([60b1ecc](https://github.com/junrar/junrar/commits/60b1ecc))
- reconcile C15 pin state and d276f937 attribution after P0.4 ([e47d8d6](https://github.com/junrar/junrar/commits/e47d8d6))
- precise post-sweep shift-history claim for RarVM (36a58836) ([a3b88de](https://github.com/junrar/junrar/commits/a3b88de))
- correct signedness-audit claims per verification ([d1c534d](https://github.com/junrar/junrar/commits/d1c534d)), closes [#9](https://github.com/junrar/junrar/issues/9)
- reconcile pin state after P0.3; snapshot-note the divergences report ([2f0a493](https://github.com/junrar/junrar/commits/2f0a493))
- reconcile manual UNPINNED/T2 state after P0.1-P0.2 ([34e9fa7](https://github.com/junrar/junrar/commits/34e9fa7))
- revise parity plan per external review (GPT-5.6 Sol) ([c0abbd9](https://github.com/junrar/junrar/commits/c0abbd9))
- correct RAR5 KDF snapshot offsets and header-CRC semantics ([72c2d49](https://github.com/junrar/junrar/commits/72c2d49))
- revise parity plan per independent adversarial review ([3e0022b](https://github.com/junrar/junrar/commits/3e0022b))
- correct Blake2sp/Bouncy Castle claim in migration manual ([25c4db2](https://github.com/junrar/junrar/commits/25c4db2))
- add RAR5/RAR7 extraction parity plan ([7a0f936](https://github.com/junrar/junrar/commits/7a0f936))
- add C++→Java migration manual and porting analysis reports ([55e165e](https://github.com/junrar/junrar/commits/55e165e))

# [7.6.1](https://github.com/junrar/junrar/compare/v7.6.0...v7.6.1) (2026-07-21)
## 🐛 Fixes

- better handling of RarVM VM_JMP ([687a09c](https://github.com/junrar/junrar/commits/687a09c))
- prevent directory creation outside target directory ([e6e333b](https://github.com/junrar/junrar/commits/e6e333b))

## 🔄️ Changes

- build: plugin isolation for spotless ([9fd65e3](https://github.com/junrar/junrar/commits/9fd65e3))
- style: spotless apply" ([4a67eb6](https://github.com/junrar/junrar/commits/4a67eb6))
- spotless apply ([0b15bec](https://github.com/junrar/junrar/commits/0b15bec))

## 🛠  Build
**deps**
- bump gradle-wrapper from 9.5.1 to 9.6.1 ([5059b1a](https://github.com/junrar/junrar/commits/5059b1a))
- bump ch.qos.logback:logback-classic from 1.5.32 to 1.5.38 ([631b5bb](https://github.com/junrar/junrar/commits/631b5bb))
- bump org.jreleaser from 1.24.0 to 1.25.0 ([99866b7](https://github.com/junrar/junrar/commits/99866b7))
- bump actions/checkout from 6 to 7 ([d4133ea](https://github.com/junrar/junrar/commits/d4133ea))
- bump codecov/codecov-action from 6 to 7 ([609aa12](https://github.com/junrar/junrar/commits/609aa12))
- bump shanegenschaw/pull-request-comment-trigger ([82d29d0](https://github.com/junrar/junrar/commits/82d29d0))

**unscoped**
- try fixing JReleaser JGit issue ([50665df](https://github.com/junrar/junrar/commits/50665df))
- try fixing JReleaser JGit issue ([87102cd](https://github.com/junrar/junrar/commits/87102cd))
- disable spotless ([51db43c](https://github.com/junrar/junrar/commits/51db43c))
- rollback debugging steps ([8bf5c21](https://github.com/junrar/junrar/commits/8bf5c21))
- try to debug jreleaser error ([815a9d9](https://github.com/junrar/junrar/commits/815a9d9))
- try to debug jreleaser error ([a5597d1](https://github.com/junrar/junrar/commits/a5597d1))
- try to debug jreleaser error ([208157d](https://github.com/junrar/junrar/commits/208157d))
- fix jreleaser output step ([c84d5aa](https://github.com/junrar/junrar/commits/c84d5aa))
- always perform release if the flag is set ([1181f48](https://github.com/junrar/junrar/commits/1181f48))
- allow current version for release workflow ([4e495f6](https://github.com/junrar/junrar/commits/4e495f6))
- plugin isolation for spotless ([0645360](https://github.com/junrar/junrar/commits/0645360))
- replace checkstyle by spotless ([e59ed4b](https://github.com/junrar/junrar/commits/e59ed4b))
- remove the old regression test workflow ([1303567](https://github.com/junrar/junrar/commits/1303567))
- setup regression test to run with manual approval ([4872ae7](https://github.com/junrar/junrar/commits/4872ae7))

# [7.6.0](https://github.com/junrar/junrar/compare/v7.5.10...v7.6.0) (2026-05-13)
## 🚀 Features

- support random access for files in solid RAR4 archives ([e0874d2](https://github.com/junrar/junrar/commits/e0874d2))

## 🏎 Perf

- replace RarCRC.checkCrc with java.util.zip.CRC32 ([5270d23](https://github.com/junrar/junrar/commits/5270d23))

## 🛠  Build
**deps**
- bump gradle-wrapper to 9.5.1 ([cb4b7fd](https://github.com/junrar/junrar/commits/cb4b7fd))
- bump com.fasterxml.jackson.core:jackson-databind ([0bb56b3](https://github.com/junrar/junrar/commits/0bb56b3))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([ca621b2](https://github.com/junrar/junrar/commits/ca621b2))
- bump org.jreleaser from 1.23.0 to 1.24.0 ([90f0548](https://github.com/junrar/junrar/commits/90f0548))
- bump commons-io:commons-io from 2.21.0 to 2.22.0 ([83a5d08](https://github.com/junrar/junrar/commits/83a5d08))
- bump com.github.ben-manes.versions from 0.53.0 to 0.54.0 ([d5abcdb](https://github.com/junrar/junrar/commits/d5abcdb))

**unscoped**
- replace deprecated action ([338efcb](https://github.com/junrar/junrar/commits/338efcb))

# [7.5.10](https://github.com/junrar/junrar/compare/v7.5.9...v7.5.10) (2026-04-15)
## 🐛 Fixes

- better handling of files outside directory when extracting ([d77e9a8](https://github.com/junrar/junrar/commits/d77e9a8))

## 🧪 Tests

- disable test on windows due to path ([154e3bf](https://github.com/junrar/junrar/commits/154e3bf))

## 🛠  Build

- publish test results ([e36ee09](https://github.com/junrar/junrar/commits/e36ee09))
- update homebrew action ([a60857b](https://github.com/junrar/junrar/commits/a60857b))

# [7.5.9](https://github.com/junrar/junrar/compare/v7.5.8...v7.5.9) (2026-04-13)
## 🐛 Fixes

- ArrayIndexOutOfBoundsException in solid RAR v20 archive extraction ([9b69c6b](https://github.com/junrar/junrar/commits/9b69c6b))
- seek past SubHeader packed data after parsing to prevent corrupt reads ([ad7ad33](https://github.com/junrar/junrar/commits/ad7ad33))

## 🛠  Build
**deps**
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([7e1b558](https://github.com/junrar/junrar/commits/7e1b558))
- bump org.mockito:mockito-core from 5.22.0 to 5.23.0 ([f800f10](https://github.com/junrar/junrar/commits/f800f10))
- bump com.fasterxml.jackson.core:jackson-databind ([1886aec](https://github.com/junrar/junrar/commits/1886aec))
- bump gradle-wrapper from 9.4.0 to 9.4.1 ([832f685](https://github.com/junrar/junrar/commits/832f685))
- bump gradle/actions from 5 to 6 ([b2f434d](https://github.com/junrar/junrar/commits/b2f434d))
- bump codecov/codecov-action from 5 to 6 ([aaaede2](https://github.com/junrar/junrar/commits/aaaede2))
- bump EndBug/add-and-commit from 9 to 10 ([884dde4](https://github.com/junrar/junrar/commits/884dde4))
- bump gradle-wrapper from 9.3.1 to 9.4.0 ([5ff5c7e](https://github.com/junrar/junrar/commits/5ff5c7e))
- bump org.mockito:mockito-core from 5.21.0 to 5.22.0 ([d9e9e49](https://github.com/junrar/junrar/commits/d9e9e49))
- bump ch.qos.logback:logback-classic from 1.5.26 to 1.5.32 ([935ece8](https://github.com/junrar/junrar/commits/935ece8))
- bump org.jreleaser from 1.22.0 to 1.23.0 ([000fcdb](https://github.com/junrar/junrar/commits/000fcdb))
- bump actions/upload-artifact from 6 to 7 ([2c83103](https://github.com/junrar/junrar/commits/2c83103))

# [7.5.8](https://github.com/junrar/junrar/compare/v7.5.7...v7.5.8) (2026-02-26)
## 🐛 Fixes

- better handle files outside directory when extracting ([947ff1d](https://github.com/junrar/junrar/commits/947ff1d))

## 🛠  Build
**deps**
- bump jreleaser to 1.22.0 ([a489f58](https://github.com/junrar/junrar/commits/a489f58))
- bump gradle-wrapper from 8.14 to 9.3.1 ([a319fc7](https://github.com/junrar/junrar/commits/a319fc7))
- bump com.fasterxml.jackson.core:jackson-databind ([c7041fc](https://github.com/junrar/junrar/commits/c7041fc))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([618feeb](https://github.com/junrar/junrar/commits/618feeb))
- bump ch.qos.logback:logback-classic from 1.5.24 to 1.5.26 ([55399ea](https://github.com/junrar/junrar/commits/55399ea))
- bump org.assertj:assertj-core from 3.27.6 to 3.27.7 ([de33003](https://github.com/junrar/junrar/commits/de33003))
- bump ch.qos.logback:logback-classic from 1.5.23 to 1.5.24 ([a4ba2c1](https://github.com/junrar/junrar/commits/a4ba2c1))
- bump ch.qos.logback:logback-classic from 1.5.22 to 1.5.23 ([75864b6](https://github.com/junrar/junrar/commits/75864b6))
- bump ch.qos.logback:logback-classic from 1.5.21 to 1.5.22 ([b937e7f](https://github.com/junrar/junrar/commits/b937e7f))
- bump org.mockito:mockito-core from 5.20.0 to 5.21.0 ([9e861fa](https://github.com/junrar/junrar/commits/9e861fa))
- bump actions/upload-artifact from 5 to 6 ([c1e7ef4](https://github.com/junrar/junrar/commits/c1e7ef4))
- bump actions/checkout from 5 to 6 ([add2452](https://github.com/junrar/junrar/commits/add2452))
- bump ch.qos.logback:logback-classic from 1.5.19 to 1.5.20 ([7d1047c](https://github.com/junrar/junrar/commits/7d1047c))
- bump commons-io:commons-io from 2.20.0 to 2.21.0 ([0bd12a9](https://github.com/junrar/junrar/commits/0bd12a9))
- bump org.jreleaser from 1.20.0 to 1.21.0 ([6d16d40](https://github.com/junrar/junrar/commits/6d16d40))
- bump actions/upload-artifact from 4 to 5 ([23788a9](https://github.com/junrar/junrar/commits/23788a9))
- bump com.github.ben-manes.versions from 0.52.0 to 0.53.0 ([b0cc6b3](https://github.com/junrar/junrar/commits/b0cc6b3))

**unscoped**
- more ci fixes ([97bf405](https://github.com/junrar/junrar/commits/97bf405))
- add flags to rerun partial releases ([36f6011](https://github.com/junrar/junrar/commits/36f6011))
- replace macos-13 with macos-latest ([d7dc7d6](https://github.com/junrar/junrar/commits/d7dc7d6))
- fix svu install ([3d1a3f7](https://github.com/junrar/junrar/commits/3d1a3f7))

# [7.5.7](https://github.com/junrar/junrar/compare/v7.5.6...v7.5.7) (2025-10-17)
## 🛠  Build

- fix failing version ([beccd50](https://github.com/junrar/junrar/commits/beccd50))
- fix failing version ([4ccf1d2](https://github.com/junrar/junrar/commits/4ccf1d2))
- use bump when computing snapshot version ([20e9105](https://github.com/junrar/junrar/commits/20e9105))
- use java 21 ([ae8bff6](https://github.com/junrar/junrar/commits/ae8bff6))
- remove java toolchains and use release flag instead ([0d99993](https://github.com/junrar/junrar/commits/0d99993)), closes [#218](https://github.com/junrar/junrar/issues/218)

## 📝 Documentation

- update maven snapshot badge ([04481cf](https://github.com/junrar/junrar/commits/04481cf))

# [7.5.6](https://github.com/junrar/junrar/compare/v7.5.5...v7.5.6) (2025-10-16)
## 🐛 Fixes

- CorruptHeaderException when EndArcHeader is missing and parsing as stream ([964801c](https://github.com/junrar/junrar/commits/964801c)), closes [#216](https://github.com/junrar/junrar/issues/216)

## 🧪 Tests

- replace deprecation ([ae8870d](https://github.com/junrar/junrar/commits/ae8870d))

## 🛠  Build
**deps**
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 from 2.19.0 to 2.20.0 ([a1143e2](https://github.com/junrar/junrar/commits/a1143e2))
- bump ch.qos.logback:logback-classic from 1.5.18 to 1.5.19 ([06ba358](https://github.com/junrar/junrar/commits/06ba358))
- bump org.mockito:mockito-core from 5.17.0 to 5.20.0 ([9880cc4](https://github.com/junrar/junrar/commits/9880cc4))
- bump com.fasterxml.jackson.core:jackson-databind ([9912de1](https://github.com/junrar/junrar/commits/9912de1))
- bump commons-io:commons-io from 2.19.0 to 2.20.0 ([716b0fc](https://github.com/junrar/junrar/commits/716b0fc))
- bump org.assertj:assertj-core from 3.27.4 to 3.27.6 ([23ba3d7](https://github.com/junrar/junrar/commits/23ba3d7))
- bump peter-evans/create-or-update-comment from 4 to 5 ([932af2e](https://github.com/junrar/junrar/commits/932af2e))
- bump gradle/actions from 4 to 5 ([d3b4237](https://github.com/junrar/junrar/commits/d3b4237))
- bump org.assertj:assertj-core from 3.27.3 to 3.27.4 ([a7b88da](https://github.com/junrar/junrar/commits/a7b88da))
- bump com.github.gotson.bestbefore:bestbefore-processor-java ([acf11b2](https://github.com/junrar/junrar/commits/acf11b2))
- bump org.jreleaser from 1.18.0 to 1.20.0 ([694c46c](https://github.com/junrar/junrar/commits/694c46c))
- bump actions/setup-java from 4 to 5 ([c6c2cb9](https://github.com/junrar/junrar/commits/c6c2cb9))
- bump actions/checkout from 4 to 5 ([f55f514](https://github.com/junrar/junrar/commits/f55f514))
- bump archunit to 1.4.1 ([4942838](https://github.com/junrar/junrar/commits/4942838))
- bump junit-pioneer to 2.3.0 ([75bd572](https://github.com/junrar/junrar/commits/75bd572))
- bump slf4j-api from 2.0.9 to 2.0.17 ([cd598e6](https://github.com/junrar/junrar/commits/cd598e6))
- bump ch.qos.logback:logback-classic from 1.4.11 to 1.5.18 ([666e572](https://github.com/junrar/junrar/commits/666e572))
- bump com.fasterxml.jackson.core:jackson-databind ([9258830](https://github.com/junrar/junrar/commits/9258830))
- bump org.mockito:mockito-core from 5.6.0 to 5.17.0 ([c2eeadc](https://github.com/junrar/junrar/commits/c2eeadc))
- bump io.github.gradle-nexus.publish-plugin ([777d966](https://github.com/junrar/junrar/commits/777d966))
- bump org.assertj:assertj-core from 3.24.2 to 3.27.3 ([76c8474](https://github.com/junrar/junrar/commits/76c8474))
- bump com.github.ben-manes.versions from 0.50.0 to 0.52.0 ([b6fa2a8](https://github.com/junrar/junrar/commits/b6fa2a8))
- bump codecov/codecov-action from 3 to 5 ([9c37e01](https://github.com/junrar/junrar/commits/9c37e01))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([ea99789](https://github.com/junrar/junrar/commits/ea99789))
- bump commons-io:commons-io from 2.15.0 to 2.19.0 ([2c02c73](https://github.com/junrar/junrar/commits/2c02c73))
- bump org.jreleaser from 1.9.0 to 1.18.0 ([d588832](https://github.com/junrar/junrar/commits/d588832))
- bump peter-evans/create-or-update-comment from 3 to 4 ([57b16d9](https://github.com/junrar/junrar/commits/57b16d9))
- bump gradle/wrapper-validation-action from 1 to 2 ([b589e68](https://github.com/junrar/junrar/commits/b589e68))
- bump actions/upload-artifact from 3 to 4 ([0c24976](https://github.com/junrar/junrar/commits/0c24976))
- bump actions/setup-java from 3 to 4 ([87ed611](https://github.com/junrar/junrar/commits/87ed611))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([fbfdc6c](https://github.com/junrar/junrar/commits/fbfdc6c))
- bump org.jreleaser from 1.8.0 to 1.9.0 ([ccae280](https://github.com/junrar/junrar/commits/ccae280))
- bump com.github.ben-manes.versions from 0.49.0 to 0.50.0 ([de20909](https://github.com/junrar/junrar/commits/de20909))
- bump com.fasterxml.jackson.core:jackson-databind ([2dc28e1](https://github.com/junrar/junrar/commits/2dc28e1))
- bump commons-io:commons-io from 2.14.0 to 2.15.0 ([13c6f8a](https://github.com/junrar/junrar/commits/13c6f8a))
- bump com.fasterxml.jackson.core:jackson-databind ([9968d24](https://github.com/junrar/junrar/commits/9968d24))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([2620ae0](https://github.com/junrar/junrar/commits/2620ae0))
- bump slf4j-api to 2.0.9 ([0f5ddb4](https://github.com/junrar/junrar/commits/0f5ddb4))
- bump com.github.ben-manes.versions to 0.49.0 ([081ad83](https://github.com/junrar/junrar/commits/081ad83))
- bump com.github.ben-manes.versions from 0.47.0 to 0.48.0 ([997ec6c](https://github.com/junrar/junrar/commits/997ec6c))
- bump actions/checkout from 3 to 4 ([3f5b413](https://github.com/junrar/junrar/commits/3f5b413))
- bump org.jreleaser from 1.7.0 to 1.8.0 ([8b87b05](https://github.com/junrar/junrar/commits/8b87b05))
- bump org.mockito:mockito-core from 5.4.0 to 5.5.0 ([377770b](https://github.com/junrar/junrar/commits/377770b))

**deps-dev**
- bump logback to 1.4.11 ([0474143](https://github.com/junrar/junrar/commits/0474143))
- bump mockito to 5.5.0 ([d81dfc6](https://github.com/junrar/junrar/commits/d81dfc6))
- bump archunit to 1.1.0 ([122ed81](https://github.com/junrar/junrar/commits/122ed81))
- bump junit-pioneer to 2.1.0 ([3514a03](https://github.com/junrar/junrar/commits/3514a03))
- bump commons-io to 2.14.0 ([1fe2be1](https://github.com/junrar/junrar/commits/1fe2be1))

**unscoped**
- OSSRH migration ([ea5ab2f](https://github.com/junrar/junrar/commits/ea5ab2f))
- bump gradle to 8.14 ([97a69f3](https://github.com/junrar/junrar/commits/97a69f3))
- migrate to gradle/actions/setup-gradlev4 ([9e5affe](https://github.com/junrar/junrar/commits/9e5affe))
- migrate to gradle/actions/setup-gradlev4 ([70c3834](https://github.com/junrar/junrar/commits/70c3834))
- use .svu.yml configuration file ([9472903](https://github.com/junrar/junrar/commits/9472903))
- adjust svu options for v3 ([74f643e](https://github.com/junrar/junrar/commits/74f643e))
- use macos-13 ([b4e51ae](https://github.com/junrar/junrar/commits/b4e51ae))
- bump gradle to 8.11.1 ([d74c30e](https://github.com/junrar/junrar/commits/d74c30e))
- migrate to gradle/actions/wrapper-validation@v3 ([9ea7d0f](https://github.com/junrar/junrar/commits/9ea7d0f))
- migrate to gradle/actions/setup-gradle@v3 ([6342706](https://github.com/junrar/junrar/commits/6342706))
- adjust svu command ([6b31739](https://github.com/junrar/junrar/commits/6b31739))
- fix quirks in the Gradle build file ([fe5d186](https://github.com/junrar/junrar/commits/fe5d186))
- bump gradle to 8.4 ([cab1bf2](https://github.com/junrar/junrar/commits/cab1bf2))
- add bestbefore to avoid shipping deprecated code in major versions ([3b9d690](https://github.com/junrar/junrar/commits/3b9d690))

## 📝 Documentation

- fix CI badge in README ([44dd10c](https://github.com/junrar/junrar/commits/44dd10c))

# [7.5.5](https://github.com/junrar/junrar/compare/v7.5.4...v7.5.5) (2023-07-18)
## 🐛 Fixes

- use correct filename in exception message ([7bfed98](https://github.com/junrar/junrar/commits/7bfed98))
- fix parsing VMSF_UPCASE in VMStandardFilters values from raw value to enum ([bc9889b](https://github.com/junrar/junrar/commits/bc9889b))
- better filename validation ([a5186a8](https://github.com/junrar/junrar/commits/a5186a8)), closes [#108](https://github.com/junrar/junrar/issues/108)

## 🛠  Build
**dependabot**
- allow dependabot PRs for Gradle ([5372493](https://github.com/junrar/junrar/commits/5372493))
- remove unused npm ([27c0d7f](https://github.com/junrar/junrar/commits/27c0d7f))

**deps**
- bump org.jreleaser from 1.6.0 to 1.7.0 ([0cdcffd](https://github.com/junrar/junrar/commits/0cdcffd))
- bump ch.qos.logback:logback-classic from 1.2.11 to 1.2.12 ([ad64851](https://github.com/junrar/junrar/commits/ad64851))
- bump org.mockito:mockito-core from 5.3.1 to 5.4.0 ([93a36c5](https://github.com/junrar/junrar/commits/93a36c5))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([adaf41f](https://github.com/junrar/junrar/commits/adaf41f))
- bump com.fasterxml.jackson.core:jackson-databind ([b2a1637](https://github.com/junrar/junrar/commits/b2a1637))
- bump commons-io:commons-io from 2.12.0 to 2.13.0 ([a61e662](https://github.com/junrar/junrar/commits/a61e662))
- bump com.github.ben-manes.versions from 0.46.0 to 0.47.0 ([559093e](https://github.com/junrar/junrar/commits/559093e))
- bump com.fasterxml.jackson.core:jackson-databind ([7967ecc](https://github.com/junrar/junrar/commits/7967ecc))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([892be80](https://github.com/junrar/junrar/commits/892be80))
- bump commons-io:commons-io from 2.11.0 to 2.12.0 ([920b524](https://github.com/junrar/junrar/commits/920b524))
- bump io.github.gradle-nexus.publish-plugin ([e7a5c8a](https://github.com/junrar/junrar/commits/e7a5c8a))
- bump org.junit-pioneer:junit-pioneer from 1.7.1 to 1.9.1 ([4a845dd](https://github.com/junrar/junrar/commits/4a845dd))
- bump org.mockito:mockito-core from 4.8.1 to 5.3.1 ([4442266](https://github.com/junrar/junrar/commits/4442266))
- bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 ([f5a40c7](https://github.com/junrar/junrar/commits/f5a40c7))
- bump com.github.ben-manes.versions from 0.43.0 to 0.46.0 ([f02470f](https://github.com/junrar/junrar/commits/f02470f))
- bump dev.jacomet.logging-capabilities from 0.10.0 to 0.11.1 ([20c58fd](https://github.com/junrar/junrar/commits/20c58fd))
- bump com.fasterxml.jackson.core:jackson-databind ([42af8fd](https://github.com/junrar/junrar/commits/42af8fd))
- bump ch.qos.logback:logback-classic from 1.2.11 to 1.4.7 ([de9b981](https://github.com/junrar/junrar/commits/de9b981))
- bump org.assertj:assertj-core from 3.23.1 to 3.24.2 ([28c66a5](https://github.com/junrar/junrar/commits/28c66a5))
- bump peter-evans/create-or-update-comment from 2 to 3 ([602a9f0](https://github.com/junrar/junrar/commits/602a9f0))

**regression**
- comment formatting ([1852b23](https://github.com/junrar/junrar/commits/1852b23))
- remove multi-os as it's not supported for gdrive download ([9cb62b1](https://github.com/junrar/junrar/commits/9cb62b1))
- regenerate corpus data in UTC timezone ([0ab266c](https://github.com/junrar/junrar/commits/0ab266c))
- force UTC timezone ([024986e](https://github.com/junrar/junrar/commits/024986e))

**unscoped**
- remove codeql ([95fa9ed](https://github.com/junrar/junrar/commits/95fa9ed))
- don't verify gradle wrapper twice for dependabot ([48721d5](https://github.com/junrar/junrar/commits/48721d5))
- remove semantic-release and husky ([7d1338c](https://github.com/junrar/junrar/commits/7d1338c))
- use svu and JReleaser for releases ([d827cec](https://github.com/junrar/junrar/commits/d827cec))
- add JReleaser ([9c9b4d6](https://github.com/junrar/junrar/commits/9c9b4d6))
- regression-test fixes and multi-os run ([878119b](https://github.com/junrar/junrar/commits/878119b))
- run regression test on PR comment or workflow_dispatch ([313e2a1](https://github.com/junrar/junrar/commits/313e2a1))
- only run check tag for regressionTest ([ae663be](https://github.com/junrar/junrar/commits/ae663be))
- add parameter to trigger release ([24ecc0a](https://github.com/junrar/junrar/commits/24ecc0a))
- disable stalebot ([96ac91d](https://github.com/junrar/junrar/commits/96ac91d))
- only run release job on master ([7bd6803](https://github.com/junrar/junrar/commits/7bd6803))
- remove matrix.java as we use Gradle toolchains ([ad96bf4](https://github.com/junrar/junrar/commits/ad96bf4))
- upload unit test results ([81c84d7](https://github.com/junrar/junrar/commits/81c84d7))
- add test for gh-108 ([092db9e](https://github.com/junrar/junrar/commits/092db9e))
- run tests on windows and macos ([fe0f765](https://github.com/junrar/junrar/commits/fe0f765))

## 📝 Documentation

- remove outdated documentation ([3e8674a](https://github.com/junrar/junrar/commits/3e8674a))
- document regression testing ([6e8e1af](https://github.com/junrar/junrar/commits/6e8e1af))
- reformat installation section for README ([e414d9f](https://github.com/junrar/junrar/commits/e414d9f))
- add maven snapshot badge in README ([b82b96a](https://github.com/junrar/junrar/commits/b82b96a))
- amend historical changelog to fit JReleaser format ([4f44af3](https://github.com/junrar/junrar/commits/4f44af3))

# [7.5.4](https://github.com/junrar/junrar/compare/v7.5.3...v7.5.4) (2022-11-04)


## Bug Fixes

* cannot extract large files (2G+) via InputStream ([cbbe99c](https://github.com/junrar/junrar/commit/cbbe99c45b6db8f2c2c8bec99db574729c0070c3)), closes [#104](https://github.com/junrar/junrar/issues/104)


## Performance Improvements

* improve copy string performance ([8e87641](https://github.com/junrar/junrar/commit/8e876416c5e6055eb7a9acce887f95a63e8c937b))

# [7.5.3](https://github.com/junrar/junrar/compare/v7.5.2...v7.5.3) (2022-08-08)


## Bug Fixes

* check validity of mark header and end archive header ([6c7dc7a](https://github.com/junrar/junrar/commit/6c7dc7af33e8a77b16ab2b12731be739994592e1))
* more checks on invalid headers ([cc33b6c](https://github.com/junrar/junrar/commit/cc33b6c179daa4a79899cec897857995bd6d1400))

# [7.5.2](https://github.com/junrar/junrar/compare/v7.5.1...v7.5.2) (2022-05-31)


## Bug Fixes

* cannot extract empty files to InputStream ([#90](https://github.com/junrar/junrar/issues/90)) ([c95a211](https://github.com/junrar/junrar/commit/c95a211a47466b2ca2f568edbb22dc976b22031d)), closes [#88](https://github.com/junrar/junrar/issues/88)

# [7.5.1](https://github.com/junrar/junrar/compare/v7.5.0...v7.5.1) (2022-04-28)


## Bug Fixes

* out of bounds read on corrupt extended time ([1d52d5d](https://github.com/junrar/junrar/commit/1d52d5d308afa607bce6fc5253bf950193e35852)), closes [#86](https://github.com/junrar/junrar/issues/86)

# [7.5.0](https://github.com/junrar/junrar/compare/v7.4.1...v7.5.0) (2022-03-20)


## Bug Fixes

* crc errors on audio data decompression ([15f4afa](https://github.com/junrar/junrar/commit/15f4afa23eff69c2e0f3c906815777abf5ac267c))
* NPE on audio data decompression ([952436b](https://github.com/junrar/junrar/commit/952436b204614a747fe8a401d213196cd326d818))


## Features

* parse extended time ([d5cc784](https://github.com/junrar/junrar/commit/d5cc784c937f461be1e71a7a92b4018af8aef8c7))
* use thread pool for getInputStream ([a3383b0](https://github.com/junrar/junrar/commit/a3383b0dc4db5c5c29334abadd42688fa5ea583b))


## Performance Improvements

* performance optimizations ([36a5883](https://github.com/junrar/junrar/commit/36a58836c3abd042a9c2cb544d7bbf8aec7beeb7))
* reduce array creation, unsigned shifts ([d276f93](https://github.com/junrar/junrar/commit/d276f937c0c328a4450164d138b3bc60db4f2542))

# [7.4.1](https://github.com/junrar/junrar/compare/v7.4.0...v7.4.1) (2022-01-27)


## Bug Fixes

* invalid subheader type would throw npe and make the extract loop ([7b16b3d](https://github.com/junrar/junrar/commit/7b16b3d90b91445fd6af0adfed22c07413d4fab7)), closes [#73](https://github.com/junrar/junrar/issues/73)

# [7.4.1](https://github.com/junrar/junrar/compare/v7.4.0...v7.4.1) (2022-01-27)


## Bug Fixes

* invalid subheader type would throw npe and make the extract loop ([7b16b3d](https://github.com/junrar/junrar/commit/7b16b3d90b91445fd6af0adfed22c07413d4fab7)), closes [#73](https://github.com/junrar/junrar/issues/73)

# [7.4.0](https://github.com/junrar/junrar/compare/v7.3.0...v7.4.0) (2020-11-01)


## Features

* make getContentsDescription usable with a Stream ([7a221f7](https://github.com/junrar/junrar/commit/7a221f7686e79d6d5fbd5fbc7573796b4057119d)), closes [#56](https://github.com/junrar/junrar/issues/56)

# [7.3.0](https://github.com/junrar/junrar/compare/v7.2.0...v7.3.0) (2020-08-03)


## Features

* support header-encrypted archive ([c2a24f3](https://github.com/junrar/junrar/commit/c2a24f3c509c4a10b8a03377e117a50013fdb16b))

# [7.2.0](https://github.com/junrar/junrar/compare/v7.1.0...v7.2.0) (2020-07-28)


## Features

* add FileHeader.getFileName ([b6da583](https://github.com/junrar/junrar/commit/b6da583ffbb861219fe20877fb78bf5092996914)), closes [#34](https://github.com/junrar/junrar/issues/34) [#53](https://github.com/junrar/junrar/issues/53)

# [7.1.0](https://github.com/junrar/junrar/compare/v7.0.0...v7.1.0) (2020-07-27)


## Features

* multi-part extraction from inputstream ([9ee32ea](https://github.com/junrar/junrar/commit/9ee32eafc12436b625c020ac96cd2a4e091af972)), closes [#52](https://github.com/junrar/junrar/issues/52)

# [7.0.0](https://github.com/junrar/junrar/compare/v6.0.1...v7.0.0) (2020-07-25)


## Code Refactoring

* cleanup public entry points ([70499bb](https://github.com/junrar/junrar/commit/70499bb89442098048af183ab3ca77fca0fd92da))
* move Volume classes to volume package ([c2405e0](https://github.com/junrar/junrar/commit/c2405e04e1dbeb06b01dff419189105321d243e5))
* simplify IO ([b59fc78](https://github.com/junrar/junrar/commit/b59fc78f10f9b650d73ad72a60e139adb4dff09e))


## Features

* add password parameter for inputstream archives ([b218bb9](https://github.com/junrar/junrar/commit/b218bb973ae2ddbb59cae7ba7558b7accab70091))
* check if archive is password protected ([c1b7728](https://github.com/junrar/junrar/commit/c1b77289ccfcf36061513ae419cf1770b76c0d57))
* support for password protected archives ([4402afc](https://github.com/junrar/junrar/commit/4402afc0c5a6a53e8dc10956b902f2fe0e960c7e)), closes [#48](https://github.com/junrar/junrar/issues/48) [#40](https://github.com/junrar/junrar/issues/40)


## BREAKING CHANGES

* public API changed
* public API changed
* name of classes have changed

# [6.0.1](https://github.com/junrar/junrar/compare/v6.0.0...v6.0.1) (2020-07-21)


## Bug Fixes

* inaccurate file times ([b1f9638](https://github.com/junrar/junrar/commit/b1f96385ff1738c1488b26968f71413f2a5085d4)), closes [edmund-wagner/junrar#20](https://github.com/edmund-wagner/junrar/issues/20)

# [6.0.0](https://github.com/junrar/junrar/compare/v5.0.0...v6.0.0) (2020-07-19)


## Code Refactoring

* exception inheritance ([aece14d](https://github.com/junrar/junrar/commit/aece14d42ec402e40f6600cbdb576b717a4220bc))
* migrate from commons-logging to SLF4J ([e6f461b](https://github.com/junrar/junrar/commit/e6f461b60875e582ac54ee4b8b3a23744d5d97c0))
* remove deprecated code ([99d4399](https://github.com/junrar/junrar/commit/99d43991023ca8d510663f9816a88c7796f7b210))


## Reverts

* rollback dependency-analysis plugin version ([29f8ab5](https://github.com/junrar/junrar/commit/29f8ab5ac250666823324e7b3ded5f1461b8290d))


## BREAKING CHANGES

* migrate from commons-logging to SLF4J
* RarException has changed
* remove ExtractArchive classes, use Junrar.extract instead

# [5.0.0](https://github.com/junrar/junrar/compare/v4.0.0...v5.0.0) (2020-07-18)


## Bug Fixes

* nullPointerException on corrupt headers ([b721589](https://github.com/junrar/junrar/commit/b721589640bdbf142b5e2daebe5fc0d5c8fab388)), closes [#36](https://github.com/junrar/junrar/issues/36) [#45](https://github.com/junrar/junrar/issues/45)


## Build System

* use gradle instead of maven ([9aa5794](https://github.com/junrar/junrar/commit/9aa579434ed50ee4150c696ba359ac7fa3cb557f)), closes [#20](https://github.com/junrar/junrar/issues/20)


## Code Refactoring

* remove VFS support ([3bbe8ed](https://github.com/junrar/junrar/commit/3bbe8eda39fb7d289bfacfda97169de035112416)), closes [#41](https://github.com/junrar/junrar/issues/41)


## BREAKING CHANGES

* carved-out into its own repo
* minimum java version bumped from 6 to 8
