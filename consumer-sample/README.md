# Consumer Sample (mavenLocal verification)

This sample Android app consumes the producer artifact published to your local Maven (`~/.m2/repository`).

## Steps
1. Publish the Android producer artifact locally:
   ```bash
   tls-android-modules/scripts/publish-local.sh
   ```
2. Build the sample:
   ```bash
   # Reuse Gradle wrapper from tls-android-modules
   tls-android-modules/gradlew -p consumer-sample :app:assembleDebug
   ```
3. Edit `BuildConfig` values in `app/build.gradle` for endpoint/region/ak/sk/token/topicId if you want to run.
