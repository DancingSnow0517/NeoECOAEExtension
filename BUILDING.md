# Building Neo ECO AE Extension

Use **JDK 21** to run the Gradle wrapper for this Minecraft 1.21.1 / NeoForge 1.21.1 project.

## Required toolchain

- **Gradle wrapper:** 8.12.1
- **Gradle runtime JDK:** 21
- **Java toolchain target:** 21

The project now fails fast when Gradle 8.12.1 is launched on Java 25+.

## Command line

### Windows PowerShell

Use the helper for the current shell:

```powershell
. .\scripts\use-jdk21.ps1
.\gradlew.bat --version
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat jar --no-daemon
```

To update your user environment permanently:

```powershell
setx JAVA_HOME "C:\Program Files\Java\jdk-21"
setx PATH "%JAVA_HOME%\bin;%PATH%"
```

### Linux / macOS

Use the helper for the current shell:

```bash
source ./scripts/use-jdk21.sh
./gradlew --version
./gradlew compileJava --no-daemon
./gradlew jar --no-daemon
```

To update your current shell manually:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

## IDE setup

Set the **Gradle JVM** and **Project SDK** to **JDK 21**.

- IntelliJ IDEA: `Settings | Build, Execution, Deployment | Build Tools | Gradle | Gradle JVM`
- Also set `Project Structure | SDK` to JDK 21

`runClient`, `runServer`, and other Gradle run configurations should use the same JDK 21 Gradle JVM.

## Known build limitation

`compileJava` and `jar` work on JDK 21 in the current repository state.

The `:test` task currently fails during task instantiation with a pre-existing Gradle reporting issue (`DefaultTestTaskReports` / `DefaultReportContainer`, `Type T not present`). That problem is not part of the ECO crafting watchdog fix and was left unchanged.
