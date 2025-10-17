[![CI](https://github.com/junrar/junrar/actions/workflows/ci.yml/badge.svg)](https://github.com/junrar/junrar/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.junrar/junrar)](https://search.maven.org/artifact/com.github.junrar/junrar)
![Maven Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fcom%2Fgithub%2Fjunrar%2Fjunrar%2Fmaven-metadata.xml&label=maven%20snapshot&color=blue)
[![javadoc](https://javadoc.io/badge2/com.github.junrar/junrar/javadoc.svg)](https://javadoc.io/doc/com.github.junrar/junrar)
[![codecov](https://codecov.io/gh/junrar/junrar/branch/master/graph/badge.svg)](https://codecov.io/gh/junrar/junrar)

# Junrar

Read and extracts from a .rar file. This is a fork of the junrar codebase, formerly on sourceforge.

Code may not be used to develop a RAR (WinRAR) compatible archiver.

## Supported features

- RAR 4 and lower (there is no RAR 5 support)
- password protected archives (also with encrypted headers)
- multi-part archives
- extract from `File` and `InputStream`
- extract to `File` and `OutputStream`

## Installation

### Gradle

```groovy
implementation "com.github.junrar:junrar:{version}"
```

### Gradle (Kotlin DSL)

```kotlin
implementation("com.github.junrar:junrar:{version}")
```

### Maven

```xml
<dependency>
  <groupId>com.github.junrar</groupId>
  <artifactId>junrar</artifactId>
  <version>{version}</version>
</dependency>
```

where `{version}` corresponds to version as below:

- Java 8 Version: [![Maven Central](https://img.shields.io/maven-central/v/com.github.junrar/junrar)](https://search.maven.org/artifact/com.github.junrar/junrar)
- Java 6 Compatible Version: [![Maven Central](https://img.shields.io/maven-central/v/com.github.junrar/junrar?versionPrefix=4.0.0)](https://search.maven.org/artifact/com.github.junrar/junrar/4.0.0/jar)

Apache Commons VFS support has been removed from `5.0.0`, and moved to a dedicated repo: https://github.com/junrar/commons-vfs-rar

## Usage

### Extract from a file to a directory:
```java
Junrar.extract("/tmp/foo.rar", "/tmp");
//or
final File rar = new File("foo.rar");  
final File destinationFolder = new File("destinationFolder");
Junrar.extract(rar, destinationFolder);    
//or
final InputStream resourceAsStream = Foo.class.getResourceAsStream("foo.rar");//only for a single rar file
Junrar.extract(resourceAsStream, tempFolder);
```

### Extract from an InputStream to an OutputStream 
```java
// Assuming you already have an InputStream from the rar file and an OutputStream for writing to
final Archive archive = new Archive(inputStream);
while (true) {
  FileHeader fileHeader = archive.nextFileHeader();
  if (fileHeader == null) {
      break;
  }
  archive.extractFile(fileHeader, outputStream); 
}
```

### Extract from an InputStream to an InputStream
```java
// Assuming you already have an InputStream from the rar file and an OutputStream for writing to
final Archive archive = new Archive(inputStream);
while (true) {
  FileHeader fileHeader = archive.nextFileHeader();
  if (fileHeader == null) {
      break;
  }
  try (InputStream is = archive.getInputStream(fileHeader)) {
      // Then use the InputStream for any method that uses that as an input, ex.:
      Files.copy(is, Paths.get("destinationFile.txt"));
  }
}
```

### List files
```java
final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(testDocuments);    
```

### Extract a password protected archive
```java
Junrar.extract("/tmp/foo.rar", "/tmp", "password");
//or
final File rar = new File("foo.rar");  
final File destinationFolder = new File("destinationFolder");
Junrar.extract(rar, destinationFolder, "password");    
//or
final InputStream resourceAsStream = Foo.class.getResourceAsStream("foo.rar");//only for a single rar file
Junrar.extract(resourceAsStream, tempFolder, "password");
```

### Extract a multi-volume archive
```java
Junrar.extract("/tmp/foo.001.rar", "/tmp");
```

## Configuration

Junrar allows for some tuning using System Properties:

- Options for `Archive#getInputStream(FileHeader)`:
  - `junrar.extractor.buffer-size`: accepts any positive integer. Defaults to `32 * 1024`. 
    - Sets the maximum size used for the dynamic byte buffer in the `PipedInputStream`.
  - `junrar.extractor.use-executor`: accepts either `true` or `false`. Defaults to `true`.
    - If `true`, it uses a cached thread pool for extracting the contents, which is generally faster.
    - If `false`, it will create a new thread on each call. This may be slower, but may require slightly less memory.
    - Options for tuning the thread pool:
      - `junrar.extractor.max-threads`: accepts any positive integer. Defaults to `2^31`.
        - Sets the maximum number of threads to be used in the pool. By default, there is no hard limit on the number 
          of threads, but they are only created when needed, so the maximum should not exceed the number of threads 
          calling this method at any given moment. Use this if you need to restrict the number of threads.
      - `junrar.extractor.thread-keep-alive-seconds`: accepts any positive integer. Defaults to `5`. 
        - Sets the number of seconds a thread can be kept alive in the pool, waiting for a next extraction operation. 
          After that time, the thread may be stopped.
