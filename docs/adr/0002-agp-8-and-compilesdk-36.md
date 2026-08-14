# ADR 0002 — Stay on AGP 8.13.2 / compileSdk 36

**Decision.** Pin Android Gradle Plugin 8.13.2, compileSdk 36, targetSdk 35,
Compose BOM 2026.06.01, core-ktx 1.17.0, activity-compose 1.11.0 — deliberately
*not* the newest releases.

**Why.** As of August 2026 the newest androidx artifacts (core 1.19,
compose-ui 1.12, activity 1.13) require compileSdk 37 **and** AGP 9.1+. AGP 9
needs a very recent Android Studio; three team members opening this project on
their own laptops is a bigger risk than missing two months of library patches.
The build was verified failing on the newer set and passing on this one.

**Revisit when.** Everyone on the team is on an Android Studio that ships AGP 9,
or a required API only exists in the newer libraries.
