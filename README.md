# GPS Picture
Open Source GPS Camera

Mark the location of activities by embedding data onto the image. Ensure things were done where they were done and when they were done!

Features:
- Custom UPI ID
- Static QR on Main Screen
- Dynamic QR with custom amount
- Split the Bill mode for multiple payees
- Quick Updater
- SMS based bill tracker
- Announce transaction amount sent/recieved through Voice when enabled
- Open Source

### Build Status:
[![Android CI](https://github.com/ZeusInstitute-OSS/gpspic/actions/workflows/main.yml/badge.svg)](https://github.com/ZeusInstitute-OSS/gpspic/actions/workflows/main.yml)

# Roadmap:
✅ Done

*️⃣ Being worked on

❌ Not Done



### Basic prototype:
1. Simple Camera Behavior: ✅
2. Flashlight: ✅
3. Back and Front Camera Support: ✅
4. Saving Image: ✅
5. Embedding Location onto image: ✅
6. Saving image in DCIM(Instead of Pictures folder): ️❌
7. Signed Builds: *️⃣
8. Website: ❌
9. Play Store/F-Droid Release: ❌
10. In-App Updater: ❌


### Versioning:

It's a small project.
#### Stable:
Naming Scheme:
- Major (M): Significant changes, new features, or any other updates that are considered substantial
- Minor (m): All other updates, including small improvements, tweaks, and bug fixes

##### Version Bump format for reference:
Title:
```
Bump to Version Major.Minor
```
Commit Message:
```
Changelog:
- Fixed SMS issue <github issue link>
- Small UI Improvements

[skip ci]
```
#### Unstable:
Naming Scheme:
- Bleeding Edge RunID DATE
Where RunID is replaced by the Run ID of github actions. 