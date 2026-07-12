package ai.ultimate.tools.builtin;

import ai.ultimate.tools.JarvisTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

@Component
public class CodiumAutomationTool implements JarvisTool {

    private static final long MAX_PAYLOAD_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_LOG_SIZE_CHARS = 100_000;
    private static final String PINNED_ANDROID_IMAGE = "mobiledevops/android-sdk-image@sha256:d8a2a89369ab7870ebed239cebeaf0d540203f1b4efb4cefa37c54174092b2ff";
    private static final String PINNED_MAVEN_IMAGE = "maven@sha256:3d74bc4d422a57321ee7bf9a4c81033ce5401b3da362b1b3b27b3b9b00de37f2";
    private static final Pattern SAFE_IMAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9_./:-]+(@sha256:[a-fA-F0-9]{64})?$");

    @Tool(
        description = "Enterprise-grade sandboxed build automation compiler engine. Manages secure path workspaces, executes strict multi-payload size validation, enforces absolute Docker container privilege isolation constraints, and streams isolated native build toolchains."
    )
    public String executeInCodium(
        @ToolParam(description = "Workspace deployment directory path") String projectPath,
        @ToolParam(description = "Target compile binary variant: 'apk' or 'jar'") String buildType,
        @ToolParam(description = "The main source code string produced by Jarvis AI") String mainCode,
        @ToolParam(description = "The build system configuration layout input text") String manifestXml,
        @ToolParam(description = "The UI layout XML source code string") String layoutXml,
        @ToolParam(description = "Custom runtime environment Docker toolchain container image path") String customDockerImage
    ) {
        if (isPayloadSizeBreached(mainCode, manifestXml, layoutXml)) {
            return "Security Restriction: Inbound compilation transaction dropped. High data volume breach on payload limits.";
        }

        if (customDockerImage != null && !customDockerImage.trim().isEmpty() && !SAFE_IMAGE_PATTERN.matcher(customDockerImage).matches()) {
            return "Security Alert: Provided custom Docker image tag configuration contains non-permissible structures.";
        }

        if (!isBinaryAvailable("docker") || !isBinaryAvailable("codium")) {
            return "Environment Error: Required system host executables ('docker' or 'codium') are missing from the current system PATH environment configuration.";
        }

        if (!isDockerEngineOnlineSecurely()) {
            return "Infrastructure Halt: The Docker engine environment virtualization daemon is either offline or unresponsive to safety heartbeat pings.";
        }

        Path cleanPath;
        try {
            Path managedWorkspaceRoot = Paths.get(System.getProperty("user.home"), "ultimate-managed-workspaces").toAbsolutePath().normalize();
            cleanPath = Paths.get(projectPath).toAbsolutePath().normalize();
            if (!cleanPath.startsWith(managedWorkspaceRoot)) {
                return "Security Access Violation: Operational boundaries exceeded. Project instances must strictly reside within the designated workspace root folder structure.";
            }
            Files.createDirectories(cleanPath);
        } catch (Exception e) {
            return "Workspace Mapping Core Defect: " + e.getMessage();
        }

        String rootElementName = getXmlRootElementSecurely(manifestXml);
        if (manifestXml != null && manifestXml.trim().startsWith("<") && rootElementName.isEmpty()) {
            return "Syntax Execution Dropped: Configured descriptor text matched an invalid XML or highly unhardened block format structure.";
        }

        if ("apk".equalsIgnoreCase(buildType) && layoutXml != null && getXmlRootElementSecurely(layoutXml).isEmpty()) {
            return "Syntax Execution Dropped: User screen layout template parsing matrix failed deployment verification diagnostics.";
        }

        String cleanedSource = stripComments(mainCode);
        String packageName = extractRegex(cleanedSource, "package\\s+([a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*)\\s*;", "");
        String className = extractRegex(cleanedSource, "(?:class|interface|enum|record)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", "Main");

        if ("apk".equalsIgnoreCase(buildType)) {
            Pattern activityInheritanceCheck = Pattern.compile("class\\s+" + className + "\\s+extends\\s+([\\w.]+Activity|[\\w.]+AppCompatActivity)");
            if (!activityInheritanceCheck.matcher(cleanedSource).find()) {
                return "Architecture Compilation Aborted: Class context entity matching reference identifier '" + className + "' does not extend a verifiable Android Activity layer subsystem hierarchy.";
            }
        }

        String fullyQualifiedMainClass = packageName.isEmpty() ? className : packageName + "." + className;
        String appId = packageName.isEmpty() ? "com.ultimate.defaultapp" : packageName;

        try {
            deployEcosystemWorkspace(cleanPath.toString(), buildType, mainCode, manifestXml, layoutXml, packageName, className, appId, rootElementName);
        } catch (IOException e) {
            return "Blueprint Creation Crash: Local OS IO channel threw write anomalies: " + e.getMessage();
        }

        String containerName = "ultimate_secure_pipeline_" + System.currentTimeMillis();
        String targetImage = (customDockerImage != null && !customDockerImage.trim().isEmpty()) ? customDockerImage.trim() : 
                             ("apk".equalsIgnoreCase(buildType) ? PINNED_ANDROID_IMAGE : PINNED_MAVEN_IMAGE);

        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("run");
        dockerCmd.add("--rm");
        dockerCmd.add("--name");
        dockerCmd.add(containerName);
        dockerCmd.add("--network");
        dockerCmd.add("none");
        dockerCmd.add("--read-only");
        dockerCmd.add("--tmpfs"); dockerCmd.add("/tmp");
        dockerCmd.add("--tmpfs"); dockerCmd.add("/root/.gradle");
        dockerCmd.add("--tmpfs"); dockerCmd.add("/root/.m2");
        dockerCmd.add("--cap-drop");
        dockerCmd.add("ALL");
        dockerCmd.add("--security-opt");
        dockerCmd.add("no-new-privileges");
        dockerCmd.add("--memory=2g");
        dockerCmd.add("--cpus=2");
        dockerCmd.add("-v");
        dockerCmd.add(cleanPath.toString() + ":/workspace");
        dockerCmd.add("-w");
        dockerCmd.add("/workspace");
        dockerCmd.add(targetImage);

        if ("apk".equalsIgnoreCase(buildType)) {
            dockerCmd.add("gradle");
            dockerCmd.add("assembleDebug");
            dockerCmd.add("--no-daemon");
            dockerCmd.add("--offline");
        } else {
            dockerCmd.add("bash");
            dockerCmd.add("-c");
            dockerCmd.add("mvn clean package -q -o || (find src/main/java -name '*.java' > sources.txt && javac @sources.txt -d /tmp/out && jar cvfe app.jar " + fullyQualifiedMainClass + " -C /tmp/out .)");
        }

        Process sandboxProcess = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(dockerCmd);
            sandboxProcess = pb.start();

            StringBuilder standardLogStream = new StringBuilder();
            StringBuilder errorLogStream = new StringBuilder();

            Thread stdReader = new Thread(new StreamCollector(sandboxProcess.getInputStream(), standardLogStream));
            Thread errReader = new Thread(new StreamCollector(sandboxProcess.getErrorStream(), errorLogStream));

            stdReader.start();
            errReader.start();

            boolean transactionComplete = sandboxProcess.waitFor(1, TimeUnit.HOURS);
            stdReader.join();
            errReader.join();

            if (!transactionComplete) {
                return "Watchdog Interdiction: Sandbox pipeline instance breached hard runtime limit of 1 hour.";
            }

            int exitCode = sandboxProcess.exitValue();
            if (exitCode == 0) {
                return "Process Pipeline Terminal Sequence Complete.\nBinary generated successfully.\n\n[Stdout Context Output]:\n" + standardLogStream;
            } else {
                return "Process Pipeline Interrupted (Exit Error Code: " + exitCode + ").\n\n[Diagnostic Error Output]:\n" + errorLogStream + "\n\n[Standard Context Output]:\n" + standardLogStream;
            }

        } catch (Exception e) {
            return "Infrastructure Process Lifecycle Failure: Pipeline invocation dropped. Detail: " + e.getMessage();
        } finally {
            if (sandboxProcess != null && sandboxProcess.isAlive()) {
                sandboxProcess.destroyForcibly();
            }
            terminateContainerForcibly(containerName);
            if (!"apk".equalsIgnoreCase(buildType)) cleanWorkspaceSafely(cleanPath);
        }
    }

    private boolean isPayloadSizeBreached(String code, String manifest, String layout) {
        long cumulativeBytes = 0;
        if (code != null) cumulativeBytes += code.getBytes(StandardCharsets.UTF_8).length;
        if (manifest != null) cumulativeBytes += manifest.getBytes(StandardCharsets.UTF_8).length;
        if (layout != null) cumulativeBytes += layout.getBytes(StandardCharsets.UTF_8).length;
        return cumulativeBytes > MAX_PAYLOAD_SIZE_BYTES;
    }

    private void deployEcosystemWorkspace(String base, String mode, String code, String manifest, String layout, String pack, String clazz, String appId, String rootXmlName) throws IOException {
        String directoryOffset = pack.replace('.', '/');
        
        if ("apk".equalsIgnoreCase(mode)) {
            String appModule = base + "/app";
            String codeRoot = appModule + "/src/main/java/" + directoryOffset;
            String resLayout = appModule + "/src/main/res/layout";
            String resValues = appModule + "/src/main/res/values";

            writeFileSecurely(codeRoot + "/" + clazz + ".java", code);

            String activityName = pack.isEmpty() ? clazz : "." + clazz;
            String resolvedManifest = "manifest".equalsIgnoreCase(rootXmlName) ? manifest : 
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    <application android:allowBackup=\"true\" android:label=\"@string/app_name\" android:theme=\"@style/Theme.AppCompat.Light.DarkActionBar\">\n        <activity android:name=\"" + activityName + "\" android:exported=\"true\">\n            <intent-filter>\n                <action android:name=\"android.intent.action.MAIN\" />\n                <category android:name=\"android.intent.category.LAUNCHER\" />\n            </intent-filter>\n        </activity>\n    </application>\n</manifest>";

            writeFileSecurely(appModule + "/src/main/AndroidManifest.xml", resolvedManifest);
            
            String resolvedLayout = (layout != null && layout.trim().startsWith("<")) ? layout : 
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"></RelativeLayout>";
            writeFileSecurely(resLayout + "/activity_main.xml", resolvedLayout);

            writeFileSecurely(resValues + "/strings.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><string name=\"app_name\">UltimateApp</string></resources>");
            writeFileSecurely(resValues + "/colors.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><color name=\"colorPrimary\">#6200EE</color></resources>");
            writeFileSecurely(resValues + "/themes.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><style name=\"Theme.Ultimate\" parent=\"Theme.AppCompat.Light.DarkActionBar\"></style></resources>");
            
            writeFileSecurely(base + "/settings.gradle", "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name='UltimateEcosystemApp'\ninclude ':app'");
            writeFileSecurely(base + "/build.gradle", "plugins { id 'com.android.application' version '8.4.0' apply false }\ntasks.register('clean', Delete) { delete rootProject.buildDir }");
            
            String appGradle = "plugins { id 'com.android.application' }\n" +
                               "android {\n" +
                               "    namespace '" + appId + "'\n" +
                               "    compileSdk 35\n" +
                               "    defaultConfig {\n" +
                               "        applicationId '" + appId + "'\n" +
                               "        minSdk 26\n" +
                               "        targetSdk 35\n" +
                               "        versionCode 1\n" +
                               "        versionName \"1.0\"\n" +
                               "    }\n" +
                               "    compileOptions {\n" +
                               "        sourceCompatibility = JavaVersion.VERSION_21\n" +
                               "        targetCompatibility = JavaVersion.VERSION_21\n" +
                               "    }\n" +
                               "    java {\n" +
                               "        toolchain {\n" +
                               "            languageVersion = JavaLanguageVersion.of(21)\n" +
                               "        }\n" +
                               "    }\n" +
                               "    buildFeatures {\n" +
                               "        viewBinding true\n" +
                               "    }\n" +
                               "    packaging {\n" +
                               "        resources { excludes += '/META-INF/{AL2.0,LGPL2.1}' }\n" +
                               "    }\n" +
                               "    lint {\n" +
                               "        abortOnError = false\n" +
                               "        checkReleaseBuilds = false\n" +
                               "    }\n" +
                               "    buildTypes { release { minifyEnabled false } }\n" +
                               "}\n" +
                               "dependencies {\n" +
                               "    implementation 'androidx.appcompat:appcompat:1.6.1'\n" +
                               "    implementation 'com.google.android.material:material:1.11.0'\n" +
                               "    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'\n" +
                               "}";
            writeFileSecurely(appModule + "/build.gradle", appGradle);
            writeFileSecurely(base + "/gradle.properties", "android.useAndroidX=true\nandroid.enableJetifier=true\norg.gradle.jvmargs=-Xmx2048m");
        } else {
            String codeRoot = base + "/src/main/java/" + directoryOffset;
            if ("project".equalsIgnoreCase(rootXmlName)) {
                writeFileSecurely(base + "/pom.xml", manifest);
            } else if (manifest != null && !manifest.trim().isEmpty()) {
                writeFileSecurely(base + "/build.gradle", manifest);
            }
            writeFileSecurely(codeRoot + "/" + clazz + ".java", code);
        }
    }

    private void writeFileSecurely(String pathStr, String contents) throws IOException {
        if (contents == null || contents.trim().isEmpty()) return;
        Path path = Paths.get(pathStr);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }

    private void cleanWorkspaceSafely(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (IOException ignored) {}
    }

    private String getXmlRootElementSecurely(String xmlString) {
        if (xmlString == null || xmlString.trim().isEmpty()) return "";
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlString))).getDocumentElement().getNodeName();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isDockerEngineOnlineSecurely() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            return p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBinaryAvailable(String binary) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] probe = os.contains("win") ? new String[]{"where", binary} : new String[]{"which", binary};
            Process p = new ProcessBuilder(probe).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String stripComments(String code) {
        if (code == null) return "";
        return code.replaceAll("//.*|/\\*(?s:.*?)\\*/", "");
    }

    private String extractRegex(String source, String expression, String fallback) {
        if (source == null) return fallback;
        Pattern p = Pattern.compile(expression);
        Matcher m = p.matcher(source);
        return m.find() ? m.group(1) : fallback;
    }

    private boolean isCodiumInstalled(String platform) {
        try {
            String[] locator = platform.contains("win") ? new String[]{"where", "codium"} : new String[]{"which", "codium"};
            Process p = new ProcessBuilder(locator).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void terminateContainerForcibly(String targetLabel) {
        try {
            new ProcessBuilder("docker", "kill", targetLabel).start();
        } catch (Exception ignored) {}
    }

    private static class StreamCollector implements Runnable {
        private final java.io.InputStream stream;
        private final StringBuilder buffer;

        public StreamCollector(java.io.InputStream stream, StringBuilder buffer) {
            this.stream = stream;
            this.buffer = buffer;
        }

        @Override
        public void run() {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && buffer.length() < MAX_LOG_SIZE_CHARS) {
                    buffer.append(line).append('\n');
                }
            } catch (IOException ignored) {}
        }
    }
}
