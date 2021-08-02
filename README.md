# PAC
[![Build Status](https://travis-ci.com/gino0631/pac.svg?branch=master)](https://travis-ci.com/gino0631/pac)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.gino0631/pac-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.gino0631/pac-maven-plugin)

A pure Java implementation of tools for building Arch Linux packages.

# Requirements
* Maven 3
* Java 8

# Usage
The tools consist of a Java library and plugins for build systems (currently, only Maven is supported).

## Maven plugin
The first step is to add the plugin to your project:
```xml
<project>
  ...
  <build>
    <!-- To define the plugin version in your parent POM -->
    <pluginManagement>
        <plugin>
          <groupId>com.github.gino0631</groupId>
          <artifactId>pac-maven-plugin</artifactId>
          <version>...</version>
        </plugin>
        ...
      </plugins>
    </pluginManagement>
    <!-- To use the plugin goals in your POM or parent POM -->
    <plugins>
      <plugin>
        <groupId>com.github.gino0631</groupId>
        <artifactId>pac-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>create-arch-package</id>
            <phase>package</phase>
            <goals><goal>package</goal></goals>
            <configuration>
              <root>${project.build.directory}/product/linux-noarch</root>
              ...
              <permissionSets>
                ...
              </permissionSets>
            </configuration>
          </execution>
        </executions>
      </plugin>
    ...
    </plugins>
    ...
  </build>
  ...
</project>
```

The most important configuration parameter is `root`, which specifies the directory containing the payload to be installed. Its contents should be relative to the root directory on a target system.

The following parameters are required, but they have reasonable default values, so it is necessary to specify them only to change the defaults:
```xml
<packageName>${project.artifactId}</packageName>
<packageVersion>${project.artifact.selectedVersion.majorVersion}.${project.artifact.selectedVersion.minorVersion}.${project.artifact.selectedVersion.incrementalVersion}</packageVersion>
<releaseNumber>1</releaseNumber>
<architecture>any</architecture>
```

Other optional parameters allow to further define the properties of the package:
```xml
<description>...</description>
<url>...</url>
<packager>...</packager>
<licenses>
  <license>...</license>
</licenses>
<depends>
  <depend>...</depend>
</depends>
<optDepends>
  <optDepend>...</optDepend>
</optDepends>
```

Refer to [PKGBUILD(5)](https://www.archlinux.org/pacman/PKGBUILD.5.html#_options_and_directives) and
[makepkg.conf(5)](https://www.archlinux.org/pacman/makepkg.conf.5.html#_options) manual pages for information about their meanings and possible values.

By default, all installed files and directories will have `0644` and `0755` permissions set accordingly. To change this, use `permissionSets`, for example:
```xml
<permissionSets>
  <permissionSet>
    <includes>
      <include>**/*.sh</include>
    </includes>
    <fileMode>0755</fileMode>
  </permissionSet>
</permissionSets>
```
The `permissionSet`s are processed in the order they are specified, and every `permissionSet` that matches (according to its `include` and `exclude` patterns) sets `fileMode`, `directoryMode`, `uid` and `gid` (if they are specified) on the file or directory in question.

## Standalone library
Add a dependency on `com.github.gino0631:pac-core` to your project, and use `PackageBuilder` class.
