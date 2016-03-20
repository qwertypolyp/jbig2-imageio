# Using the JBIG2 ImageIO-Plugin with Maven #
The JBIG2 ImageIO-Plugin is not currently available from Maven Central. However, we have set up a Maven repository on this site to host the artifacts. In order to use this repository, you need to define it inside your pom.xml like this:
```
<project...>
  ...
  <repositories>
    ...
    <repository>
      <id>jbig2.googlecode</id>
      <name>JBIG2 ImageIO-Plugin repository at googlecode.com</name>
      <url>http://jbig2-imageio.googlecode.com/svn/maven-repository</url>
    </repository>
    ... 
  </repositories>
```

To use the plugin, include a dependency to the following artifact:
```
<dependency>
  <groupId>com.levigo.jbig2</groupId>
  <artifactId>levigo-jbig2-imageio</artifactId>
  <version>1.3</version>
</dependency>
```