# Real repository fixtures

`stirling-cbr-utils.java` is a minimized, compileable fixture derived from
Stirling-PDF commit
[`cd3a59f0777d37648069847fc8ee2e8c77215329`](https://github.com/Stirling-Tools/Stirling-PDF/tree/cd3a59f0777d37648069847fc8ee2e8c77215329).

- Original path:
  `app/common/src/main/java/stirling/software/common/util/CbrUtils.java`
- Dependency owner:
  `app/common/build.gradle` line 45 declared
  `api 'com.github.junrar:junrar:7.5.8'`
- Relevant original shape: `new Archive(File)`, iteration over `FileHeader`,
  `getFileName()`, `Archive#getInputStream(FileHeader)`,
  `CorruptHeaderException`/`RarException`, and cleanup in `finally`.
- License: MIT for this path, as declared by the repository root `LICENSE`.

The fixture removes PDF/Spring/Lombok business code but preserves the Junrar
control-flow boundaries needed by the recipe test. It is not an invented API
example and is not used as production source.
