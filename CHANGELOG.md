# [7.1.0](https://github.com/junrar/junrar/compare/v7.0.0...v7.1.0) (2020-07-27)


### Features

* multi-part extraction from inputstream ([9ee32ea](https://github.com/junrar/junrar/commit/9ee32eafc12436b625c020ac96cd2a4e091af972)), closes [#52](https://github.com/junrar/junrar/issues/52)

# [7.0.0](https://github.com/junrar/junrar/compare/v6.0.1...v7.0.0) (2020-07-25)


### Code Refactoring

* cleanup public entry points ([70499bb](https://github.com/junrar/junrar/commit/70499bb89442098048af183ab3ca77fca0fd92da))
* move Volume classes to volume package ([c2405e0](https://github.com/junrar/junrar/commit/c2405e04e1dbeb06b01dff419189105321d243e5))
* simplify IO ([b59fc78](https://github.com/junrar/junrar/commit/b59fc78f10f9b650d73ad72a60e139adb4dff09e))


### Features

* add password parameter for inputstream archives ([b218bb9](https://github.com/junrar/junrar/commit/b218bb973ae2ddbb59cae7ba7558b7accab70091))
* check if archive is password protected ([c1b7728](https://github.com/junrar/junrar/commit/c1b77289ccfcf36061513ae419cf1770b76c0d57))
* support for password protected archives ([4402afc](https://github.com/junrar/junrar/commit/4402afc0c5a6a53e8dc10956b902f2fe0e960c7e)), closes [#48](https://github.com/junrar/junrar/issues/48) [#40](https://github.com/junrar/junrar/issues/40)


### BREAKING CHANGES

* public API changed
* public API changed
* name of classes have changed

## [6.0.1](https://github.com/junrar/junrar/compare/v6.0.0...v6.0.1) (2020-07-21)


### Bug Fixes

* inaccurate file times ([b1f9638](https://github.com/junrar/junrar/commit/b1f96385ff1738c1488b26968f71413f2a5085d4)), closes [edmund-wagner/junrar#20](https://github.com/edmund-wagner/junrar/issues/20)

# [6.0.0](https://github.com/junrar/junrar/compare/v5.0.0...v6.0.0) (2020-07-19)


### Code Refactoring

* exception inheritance ([aece14d](https://github.com/junrar/junrar/commit/aece14d42ec402e40f6600cbdb576b717a4220bc))
* migrate from commons-logging to SLF4J ([e6f461b](https://github.com/junrar/junrar/commit/e6f461b60875e582ac54ee4b8b3a23744d5d97c0))
* remove deprecated code ([99d4399](https://github.com/junrar/junrar/commit/99d43991023ca8d510663f9816a88c7796f7b210))


### Reverts

* rollback dependency-analysis plugin version ([29f8ab5](https://github.com/junrar/junrar/commit/29f8ab5ac250666823324e7b3ded5f1461b8290d))


### BREAKING CHANGES

* migrate from commons-logging to SLF4J
* RarException has changed
* remove ExtractArchive classes, use Junrar.extract instead

# [5.0.0](https://github.com/junrar/junrar/compare/v4.0.0...v5.0.0) (2020-07-18)


### Bug Fixes

* nullPointerException on corrupt headers ([b721589](https://github.com/junrar/junrar/commit/b721589640bdbf142b5e2daebe5fc0d5c8fab388)), closes [#36](https://github.com/junrar/junrar/issues/36) [#45](https://github.com/junrar/junrar/issues/45)


### Build System

* use gradle instead of maven ([9aa5794](https://github.com/junrar/junrar/commit/9aa579434ed50ee4150c696ba359ac7fa3cb557f)), closes [#20](https://github.com/junrar/junrar/issues/20)


### Code Refactoring

* remove VFS support ([3bbe8ed](https://github.com/junrar/junrar/commit/3bbe8eda39fb7d289bfacfda97169de035112416)), closes [#41](https://github.com/junrar/junrar/issues/41)


### BREAKING CHANGES

* carved-out into its own repo
* minimum java version bumped from 6 to 8
