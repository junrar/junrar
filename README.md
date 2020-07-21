[![Build Status](https://travis-ci.com/junrar/junrar.svg?branch=master)](https://travis-ci.com/junrar/junrar)
[![Download](https://api.bintray.com/packages/bintray/jcenter/com.github.junrar%3Ajunrar/images/download.svg) ](https://bintray.com/bintray/jcenter/com.github.junrar%3Ajunrar/_latestVersion)
[![codecov](https://codecov.io/gh/junrar/junrar/branch/master/graph/badge.svg)](https://codecov.io/gh/junrar/junrar)

# Junrar

Read and extracts from a .rar file. Supported RAR formats are V4 and lower. This is a fork of the junrar codebase, formerly on sourceforge.

Code may not be used to develop a RAR (WinRAR) compatible archiver.

## Installation

<table>
<tr>
    <td>Gradle</td>
    <td>
        <pre>implementation "com.github.junrar:junrar:{version}"</pre>
    </td>
</tr>
<tr>
    <td>Gradle (Kotlin DSL)</td>
    <td>
        <pre>implementation("com.github.junrar:junrar:{version}")</pre>
        </td>
</tr>
<tr>
    <td>Maven</td>
    <td>
        <pre>&lt;dependency&gt;
    &lt;groupId&gt;com.github.junrar&lt;/groupId&gt;
    &lt;artifactId&gt;junrar&lt;/artifactId&gt;
    &lt;version&gt;{version}&lt;/version&gt;
&lt;/dependency&gt;</pre>
    </td>
</tr>
</table>

where `{version}` corresponds to version as below:

- Java 8 Version: [![Download](https://api.bintray.com/packages/bintray/jcenter/com.github.junrar%3Ajunrar/images/download.svg) ](https://bintray.com/bintray/jcenter/com.github.junrar%3Ajunrar/_latestVersion)
- Java 6 Compatible Version: [![Download](https://api.bintray.com/packages/bintray/jcenter/com.github.junrar%3Ajunrar/images/download.svg?version=4.0.0) ](https://bintray.com/bintray/jcenter/com.github.junrar%3Ajunrar/4.0.0/link)

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
      archive.extractFile(fileHeader, outputStream)); 
  }
```

### List files:
```java
final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(testDocuments);    
```





