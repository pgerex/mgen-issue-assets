#!/usr/bin/env bash
set -euo pipefail

# Gradle 7.3.1 (the wrapper distribution) runs on Java 8-17; prefer the JDKs shipped in the image.
JAVA_CHOSEN=""
for candidate in /usr/lib/jvm/temurin-8 /usr/lib/jvm/temurin-11 /usr/lib/jvm/temurin-17; do
  if [ -x "$candidate/bin/java" ]; then JAVA_CHOSEN="$candidate"; break; fi
done
if [ -n "$JAVA_CHOSEN" ]; then
  export JAVA_HOME="$JAVA_CHOSEN"
fi

# The published snapshot omits the binary wrapper jar; restore it from the upstream repository.
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
    https://raw.githubusercontent.com/sotudeko/mgen/main/gradle/wrapper/gradle-wrapper.jar \
  || {
      tmpdir="$(mktemp -d)"
      curl -fsSL -o "$tmpdir/mgen.tgz" https://codeload.github.com/sotudeko/mgen/tar.gz/refs/tags/83
      mkdir -p "$tmpdir/unpacked"
      tar -xzf "$tmpdir/mgen.tgz" -C "$tmpdir/unpacked"
      cp "$tmpdir"/unpacked/mgen-83/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar
      rm -rf "$tmpdir"
    }
fi
chmod +x gradlew

# repo.maven.apache.org rate limits this network, so route Gradle through mirrors.
# The plugin portal marker poms for the two third-party plugins are not on Maven
# Central; publish tiny local markers so plugin resolution never needs the portal.
mkdir -p "$HOME/.gradle"
MARKER_ROOT="$HOME/.mgen-local-repo"
mkdir -p "$MARKER_ROOT/io/spring/dependency-management/io.spring.dependency-management.gradle.plugin/1.0.11.RELEASE"
mkdir -p "$MARKER_ROOT/org/sonatype/gradle/plugins/scan/org.sonatype.gradle.plugins.scan.gradle.plugin/1.2.0"
cat > "$MARKER_ROOT/io/spring/dependency-management/io.spring.dependency-management.gradle.plugin/1.0.11.RELEASE/io.spring.dependency-management.gradle.plugin-1.0.11.RELEASE.pom" << 'POM_EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.spring.dependency-management</groupId>
  <artifactId>io.spring.dependency-management.gradle.plugin</artifactId>
  <version>1.0.11.RELEASE</version>
  <packaging>pom</packaging>
  <dependencies>
    <dependency>
      <groupId>io.spring.gradle</groupId>
      <artifactId>dependency-management-plugin</artifactId>
      <version>1.0.11.RELEASE</version>
    </dependency>
  </dependencies>
</project>
POM_EOF
cat > "$MARKER_ROOT/org/sonatype/gradle/plugins/scan/org.sonatype.gradle.plugins.scan.gradle.plugin/1.2.0/org.sonatype.gradle.plugins.scan.gradle.plugin-1.2.0.pom" << 'POM_EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.sonatype.gradle.plugins.scan</groupId>
  <artifactId>org.sonatype.gradle.plugins.scan.gradle.plugin</artifactId>
  <version>1.2.0</version>
  <packaging>pom</packaging>
  <dependencies>
    <dependency>
      <groupId>org.sonatype.gradle.plugins</groupId>
      <artifactId>scan-gradle-plugin</artifactId>
      <version>1.2.0</version>
    </dependency>
  </dependencies>
</project>
POM_EOF
cat > "$HOME/.gradle/init.gradle" << 'INIT_EOF'
def localRepo = new File(System.getProperty('user.home'), '.mgen-local-repo').toURI().toString()
settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            clear()
            maven { url localRepo }
            maven { url 'https://maven-central.storage-download.googleapis.com/maven2/' }
        }
    }
}
allprojects {
    repositories {
        clear()
        maven { url 'https://maven-central.storage-download.googleapis.com/maven2/' }
        maven { url 'https://maven.aliyun.com/repository/central/' }
        maven { url 'https://mirrors.huaweicloud.com/repository/maven/' }
    }
}
INIT_EOF

# Warm the Gradle distribution and compile every production dependency once.
./gradlew --no-daemon --console=plain compileJava
