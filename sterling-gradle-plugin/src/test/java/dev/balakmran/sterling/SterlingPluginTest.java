package dev.balakmran.sterling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.junit.jupiter.api.Test;

class SterlingPluginTest {
  @Test
  void daemonJvmCheckAccepts21AndLater() {
    List.of(JavaVersion.VERSION_21, JavaVersion.VERSION_25)
        .forEach(version ->
            assertThatCode(() -> SterlingPlugin.checkDaemonJvm(version))
                .doesNotThrowAnyException());
  }

  @Test
  void daemonJvmCheckRejects17WithTheFix() {
    assertThatThrownBy(() -> SterlingPlugin.checkDaemonJvm(JavaVersion.VERSION_17))
        .isInstanceOf(GradleException.class)
        .hasMessage(
            "Sterling requires Gradle to run on JDK 21+ (found 17). "
                + "Run ./gradlew updateDaemonJvm --jvm-version=21, or point JAVA_HOME at JDK 21+.");
  }

  @Test
  void relativePathUsesForwardSlashesAndClimbsOutOfTheProject() {
    File projectDir = new File("/work/service");

    assertThat(SterlingPlugin.relativePath(projectDir, new File("/work/service/config/a.xml")))
        .isEqualTo("config/a.xml");
    assertThat(SterlingPlugin.relativePath(projectDir, new File("/work/shared/a.xml")))
        .isEqualTo("../shared/a.xml");
  }
}
