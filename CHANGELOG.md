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
