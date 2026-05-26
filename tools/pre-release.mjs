import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const rootDir = process.cwd();
const isWindows = process.platform === "win32";
const gradleCmd = isWindows ? "gradlew.bat" : "./gradlew";
const gradleArgs = [":app:assembleRelease"];
const checkArgs = ["tools/check-project.mjs"];
const releaseApkDir = path.join(rootDir, "app", "build", "outputs", "apk", "release");

function fail(message) {
  console.error(`FAIL ${message}`);
  process.exit(1);
}

function ok(message) {
  console.log(`OK  ${message}`);
}

function warn(message) {
  console.warn(`WARN ${message}`);
}

function isUsableJavaHome(javaHome) {
  if (!javaHome) return false;
  const javaExe = path.join(javaHome, "bin", isWindows ? "java.exe" : "java");
  const jvmConfig = path.join(javaHome, "lib", "jvm.cfg");
  return fs.existsSync(javaExe) && fs.existsSync(jvmConfig);
}

function resolveJavaHome() {
  const currentJavaHome = process.env.JAVA_HOME || "";
  if (isUsableJavaHome(currentJavaHome)) {
    ok(`JAVA_HOME activo: ${currentJavaHome}`);
    return currentJavaHome;
  }

  const fallbackCandidates = [
    "C:\\Program Files\\Android\\Android Studio1\\jbr",
    "C:\\Program Files\\Android\\Android Studio\\jbr",
    "C:\\Program Files\\Android\\Android Studio\\jre",
    path.join(process.env.USERPROFILE || "", ".gradle", "jdks", "eclipse_adoptium-17-amd64-windows.2"),
  ];

  for (const candidate of fallbackCandidates) {
    if (isUsableJavaHome(candidate)) {
      warn(`JAVA_HOME invalido o vacio; usando JBR de Android Studio: ${candidate}`);
      return candidate;
    }
  }

  fail("No se encontro un JAVA_HOME util ni JBR de Android Studio");
}

function runStep(label, command, args, extraEnv = {}) {
  console.log("");
  console.log(`== ${label} ==`);
  const env = { ...process.env, ...extraEnv };
  const isBatch = isWindows && command.toLowerCase().endsWith(".bat");
  const executable = isBatch ? "powershell.exe" : command;
  const commandArgs = isBatch
    ? [
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        `& '${command.replace(/'/g, "''")}' ${args.map((arg) => `'${String(arg).replace(/'/g, "''")}'`).join(" ")}`,
      ]
    : args;
  const result = spawnSync(executable, commandArgs, {
    cwd: rootDir,
    env,
    stdio: "inherit",
    shell: false,
  });

  if (typeof result.status === "number" && result.status !== 0) {
    fail(`${label} fallo con codigo ${result.status}`);
  }
  if (result.error) {
    fail(`${label} no pudo ejecutarse: ${result.error.message}`);
  }
  ok(`${label} completado`);
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return "desconocido";
  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 2)} ${units[unitIndex]}`;
}

function reportApk() {
  const apkPath = fs.existsSync(releaseApkDir)
    ? fs.readdirSync(releaseApkDir)
        .filter((name) => name.endsWith(".apk"))
        .map((name) => path.join(releaseApkDir, name))
        .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)[0]
    : "";
  if (!fs.existsSync(apkPath)) {
    fail(`No existe APK release en ${releaseApkDir}`);
  }
  const stats = fs.statSync(apkPath);
  ok(`APK listo: ${apkPath}`);
  ok(`Tamano APK: ${formatBytes(stats.size)} (${stats.size} bytes)`);
  ok(`Fecha APK: ${stats.mtime.toLocaleString("es-DO")}`);
}

function main() {
  const javaHome = resolveJavaHome();
  runStep("Chequeo Node", process.execPath, checkArgs);
  runStep("Build release", path.join(rootDir, gradleCmd), gradleArgs, { JAVA_HOME: javaHome });
  reportApk();
}

main();
