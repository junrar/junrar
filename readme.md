Junrar
=====

Adds support to read and extract a rar.

Usage:
```

final File rar = new File("foo.rar");  
final File destinationFolder = new File("destinationFolder");  
ExtractArchive extractArchive = new ExtractArchive();  
extractArchive.extractArchive(rar, destinationFolder);  
```
Dependency on maven:  
```

<dependency>  
  <groupId>com.github.junrar</groupId>  
  <artifactId>junrar</artifactId>
  <version>0.7</version>  
</dependency>  
```
