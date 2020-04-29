[![Build Status](https://travis-ci.com/junrar/junrar.svg?branch=master)](https://travis-ci.com/junrar/junrar)
[![Release](https://img.shields.io/github/release/junrar/junrar.svg)](../../releases/latest)

Junrar
=====

Read and extracts from a .rar file. Supported RAR formats are V4 and lower. This is a fork of the junrar codebase, formerly on sourceforge.

Code may not be used to develop a RAR (WinRAR) compatible archiver.

Dependency on maven:  
```

<dependency>  
  <groupId>com.github.junrar</groupId>  
  <artifactId>junrar</artifactId>
  <version>4.0.0</version>  
</dependency>  
```


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
      archive.extractFile(fileHeader, outputStream)); 
  }
```

### List files:
```java
final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(testDocuments);    
```





