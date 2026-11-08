# Build and sign TuneHub

Use JDK 17, Gradle 8.11.1, and Android SDK Platform 35. The release in this package uses `com.tunehub.app`, version name `1.1`, and version code `2`. Keep the package ID and signing key for updates. Increase `BUILD_NUMBER` for every new APK you distribute.

Set the following environment variables before running `gradle assembleRelease` for distribution:

- `TUNEHUB_KEYSTORE` with the absolute path to the release keystore.
- `TUNEHUB_KEYSTORE_PASSWORD`, `TUNEHUB_KEY_ALIAS`, and `TUNEHUB_KEY_PASSWORD` with the keystore credentials.
- `TUNEHUB_APPLICATION_ID` if overriding the default package ID.
- `BUILD_NUMBER` with the next Android version code.
- `TUNEHUB_RELEASES_URL` optionally sets the Settings release link. GitHub Actions defaults to the current repository's Releases page.

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`. Run Android SDK `apksigner verify --print-certs` and compare the certificate SHA-256 to the expected release certificate before distributing it. Keep the keystore and credentials outside the source repository. A local build without signing credentials uses the debug key for testing.

## GitHub Releases

Put the project in a repository and set the Actions variables `TUNEHUB_APPLICATION_ID` and `RELEASE_CERT_SHA256`. The certificate digest uses 64 hexadecimal characters without separators. Store the base64-encoded keystore in the `KEYSTORE_BASE64` secret and set the `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets. The Build APK workflow checks the signing certificate and runs the deterministic unit tests before publishing on `main`.
