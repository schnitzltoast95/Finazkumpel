---
name: apk-regression-test
description: Test new Android APK builds for Svetlanas Finanzkumpel by replacing or updating the installed test app, running the project's automated checks and matching Android instrumentation tests, and reporting the evidence. Use when a new APK is ready, attached, or the user asks to install, replace, or regression-test the Finanzkumpel APK.
---

# APK regression test

Use the existing deterministic runners in the Buchhaltungs App project. Prefer the helper at `scripts/run-latest-apk-test.ps1`; it selects an exact APK, runs the project checks, pairs an instrumentation APK only when it is demonstrably compatible, and then calls the device runner.

## Authorization and installation mode

- An explicit request in the current turn to test, install, update, or replace an APK authorizes the package-scoped device changes for that run.
- Otherwise, get confirmation immediately before uninstalling or installing anything.
- Default to a clean replacement. This removes only the target app package and its test package, resets that app's data, and installs the new APK.
- Use `-KeepData` only when the user explicitly wants an in-place update that preserves the installed app's data.
- Never delete unrelated apps, device files, or broad directories. Test artifacts go into a unique directory below `Download/Quartalsmappe-Testdaten`.

## Select and run

1. Use an explicitly supplied or attached APK path when available. Otherwise select the newest non-`androidTest` APK from the project's `dist` directory and state the exact path before mutation.
2. Confirm that ADB sees an authorized device. If several devices are connected, require or infer only an explicitly identified serial; do not guess.
3. Run the helper from this skill:

   ```powershell
   & "<skill-directory>\scripts\run-latest-apk-test.ps1" `
     -ProjectPath "C:\Users\maria\Documents\ChatGPT\Buchhaltungs App" `
     -ApkPath "<exact-apk-path>" `
     -DeviceSerial "<adb-serial>"
   ```

   Omit `-ApkPath` to select the latest APK in `dist`. Add `-KeepData` only for the preserve-data mode. Use `-DryRun` to validate selection and compatibility without changing the device.
4. The helper runs `tools/test-project.ps1 -Offline`, then compares the selected APK byte-for-byte with the current project build. It supplies the generated `androidTest` APK only on an exact match. A caller-provided `-TestApkPath` remains allowed and is validated by the device runner.
5. If the selected APK does not match the current project build, continue with installation, launch health checks, test-data transfer, screenshots, and logs, but clearly report that instrumentation tests were skipped rather than pairing a stale test APK.

## Diagnose and report

- A failing assertion is not automatically an app defect. Inspect the assertion, current UI text, screenshot, and logs. If the harness contains a demonstrably stale expectation, update only the harness, rebuild the test APK, and rerun the complete device suite. Never weaken a valid assertion merely to make a failure green.
- Stop and report on unauthorized/offline devices, package or signature mismatch, failed installation, app crash, or a real test failure. Do not repeatedly uninstall and retry without a specific diagnosis.
- Report the exact APK path, SHA-256, version, device, clean versus preserve-data mode, project-test counts, instrumentation-test counts, failures, and the absolute result directory.
- State whether the build is debug-signed and therefore test-only.
- Leave the tested app installed and launch it after a successful run when useful for immediate manual inspection.

This skill is automatically discoverable when a user mentions a new Finanzkumpel APK. It runs when invoked in a Codex turn; it is not a continuous filesystem watcher. A request for unattended background monitoring requires a separate, explicitly configured automation.
