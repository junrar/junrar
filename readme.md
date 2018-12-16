[![Build Status](https://travis-ci.com/junrar/junrar.svg?branch=master)](https://travis-ci.com/junrar/junrar)

Junrar
=====

Read and extracts from a .rar file. Supported RAR formats are V4 and lower. This is a fork of the junrar codebase, formerly on sourceforge.

Code may not be used to develop a RAR (WinRAR) compatible archiver.

If you are using a version below 1.0.1 please upgrade

Dependency on maven:  
```

<dependency>  
  <groupId>com.github.junrar</groupId>  
  <artifactId>junrar</artifactId>
  <version>3.1.0</version>  
</dependency>  
```


Extract from files:
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

List files:
```java
final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(testDocuments);    
```





