# Pojlib | Minecraft Launcher Core
[![License: LGPL v3](https://img.shields.io/badge/License-LGPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Pojlib Build](https://github.com/QuestCraftPlusPlus/Pojlib/actions/workflows/gradle.yml/badge.svg)](https://github.com/QuestCraftPlusPlus/Pojlib/actions/workflows/gradle.yml)

A Minecraft: Java Edition launcher library partially made with elements from PojavLauncher.

This library was initially meant for use in QuestCraft but has turned into the perfect library for Minecraft: Java Edition launchers. This includes everything needed for a basic and (soon) even advanced MCJE launchers written in Java (or any other language with interop).

## Contributing

Contributions are always welcome!

Please ensure your code follows the language's naming conventions, here's a list of a few of the most common languages used in our projects:

- [Java's Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-namingconventions.html)
- [C++'s Conventions](https://google.github.io/styleguide/cppguide.html)

Make sure your pull request describes exactly what the code does and explains why you're making the pull request!


## Credits & Third Party Components
### Developers:

* [@TheJudge156](https://github.com/thejudge156) | Senior Maintainer

* [@CADIndie](https://github.com/CADIndie) | Jr. Maintainer

* [@MrNavaStar](https://github.com/MrNavaStar) | Previous Main Feature Implementor

### Components:
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) (Pojlib Base application): [GNU LGPLv3](https://github.com/khanhduytran0/PojavLauncher/blob/master/LICENSE).
- [LightThinWrapper](https://github.com/PojavLauncherTeam/BigTinyWrapper) (Main renderer/OpenGL Driver for QuestCraft): [PolyForm Shield](https://github.com/PojavLauncherTeam/BigTinyWrapper/blob/master/LICENSE) (Must be removed from forks of Pojlib IF said fork violates the guidelines set by this components  license).
- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
- [LWJGL3](https://github.com/PojavLauncherTeam/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [LWJGLX](https://github.com/PojavLauncherTeam/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license.
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
- [bhook](https://github.com/bytedance/bhook) (Used for exit code trapping): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE).
- [pro-grade](https://github.com/pro-grade/pro-grade) (Java sandboxing security manager): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).

