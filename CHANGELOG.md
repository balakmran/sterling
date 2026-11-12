# Changelog

## [0.1.0-alpha.4](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.3...v0.1.0-alpha.4) (2026-11-12)


### Features

* ship one plugin ID, dev.balakmran.sterling ([182021e](https://github.com/balakmran/sterling/commit/182021ee0504a2c3aa9e1141ef7cb4c59afc6675))

## [0.1.0-alpha.3](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.2...v0.1.0-alpha.3) (2026-11-11)


### Bug Fixes

* apply coverage settings after build scripts run, and allow disabling NullAway while it's off ([29f4644](https://github.com/balakmran/sterling/commit/29f4644b560a1ab19d79dedd6f2b5f8c293b6983))

## [0.1.0-alpha.2](https://github.com/balakmran/sterling/compare/v0.1.0-alpha.1...v0.1.0-alpha.2) (2026-11-10)


### Bug Fixes

* declare configuration-cache support to the Plugin Portal ([ca1860c](https://github.com/balakmran/sterling/commit/ca1860c8e77ea53026eb4a2f23aa4ef9671bb068))

## 0.1.0-alpha.1 (2026-11-10)


### Features

* add the base plugin with the sterling extension and stl tasks ([9725f6f](https://github.com/balakmran/sterling/commit/9725f6f20d6fa13ba02675c720e73f70809fc28e))
* add the Java plugin with the JDK 21 daemon and toolchain checks ([18458b2](https://github.com/balakmran/sterling/commit/18458b28c494ab2984ad99b9778362458d6e87cd))
* analyse bytecode with SpotBugs behind spotbugs.enabled ([b0845ce](https://github.com/balakmran/sterling/commit/b0845ce7398fac52e35175bc9cf25030d5739353))
* check @NullMarked code with NullAway behind nullAway.enabled ([ed24745](https://github.com/balakmran/sterling/commit/ed24745f5cc70e000edfcd84dbdb37d57fdb1712))
* format Java sources with google-java-format via Spotless ([acf3f85](https://github.com/balakmran/sterling/commit/acf3f8536ab7015f2e3dfdd3f0666ad2477ba92f))
* run Error Prone in compilation behind errorProne.enabled ([76f0423](https://github.com/balakmran/sterling/commit/76f0423ee9e9d523eb9a61cafcd53cb44573814d))
* test across Gradle 9.0–9.9 and Java 21–25, and skip generated classes in SpotBugs ([1d048e3](https://github.com/balakmran/sterling/commit/1d048e37864504355fa22e45f89847fbfba19357))
* verify test coverage with JaCoCo behind coverage.enabled ([4c0601a](https://github.com/balakmran/sterling/commit/4c0601a0e051f724fb64c27a023fc6f2db9f2661))
* wire check to stlCheck through attachToCheck ([4c8e17a](https://github.com/balakmran/sterling/commit/4c8e17ac4554fcfe4d56cb83341c3b517069680a))
