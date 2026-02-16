# Privacy Policy

**3x3 Blind Mice**
*Last updated: February 15, 2026*

## Overview

3x3 Blind Mice ("the App") is an open-source Android application for blindfolded Rubik's cube solving with optional Bluetooth smart cube support. The App is provided "as is" with no warranties of any kind.

The source code is publicly available at [GitHub](https://github.com/andrewlstewart/3x3_blind_mice) and can be independently audited.

## Information Collection

### Data Stored Locally on Your Device

The App stores the following data in a local database on your device to provide core functionality:

- Solve timestamps
- Scramble sequences and solve move sequences
- Solve times (memorization and execution)
- Solve mode and DNF (Did Not Finish) status

**This data never leaves your device.** The App does not transmit any data to external servers, cloud services, or third parties.

### Bluetooth Data

When you connect a compatible Bluetooth smart cube, the App receives:

- Cube move data (encrypted via AES-128 and decrypted locally on your device)
- Orientation/gyroscope sensor data (used for the 3D cube visualization, not stored)
- Battery level (displayed in the app, not stored)

All Bluetooth communication occurs directly between your device and the cube. No Bluetooth data is transmitted to any external service.

### What We Do NOT Collect

- Personal information (names, email addresses, phone numbers, etc.)
- Location data (Bluetooth scanning uses the `neverForLocation` flag on Android 12+; location permissions on older Android versions are required solely by the Android Bluetooth API and are not used for location tracking)
- Device identifiers or advertising IDs
- Usage analytics or telemetry
- Crash reports

## Third-Party Services

The App does not integrate any third-party services, including but not limited to:

- Analytics platforms
- Advertising networks
- Crash reporting services
- Cloud storage or sync services
- Social media SDKs

## Data Storage and Security

All data is stored locally in your device's private application directory, protected by Android's application sandboxing. The App does not use cloud backup. Uninstalling the App permanently deletes all stored data.

## Data Deletion

You can delete your solve history at any time through the App. Uninstalling the App removes all associated data from your device.

## Children's Privacy

The App does not knowingly collect any personal information from anyone, including children under the age of 13.

## Permissions

The App requests the following permissions:

| Permission | Purpose |
|---|---|
| `BLUETOOTH_SCAN` | Discover nearby Bluetooth smart cubes |
| `BLUETOOTH_CONNECT` | Connect to and communicate with your smart cube |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Bluetooth support on Android 11 and below |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | Required by Android's Bluetooth API on Android 11 and below (not used for location tracking) |

All permissions are optional. The App functions in manual mode without granting any permissions.

## Open Source

This App is open source. You are encouraged to review the source code to verify these privacy practices independently.

## Disclaimer of Liability

THE APP IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE DEVELOPER(S) OR CONTRIBUTOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT, OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE APP OR THE USE OR OTHER DEALINGS IN THE APP.

You use the App entirely at your own risk. The developer(s) assume no responsibility for any data loss, device damage, or any other consequence arising from the use of this App.

## Changes to This Policy

This privacy policy may be updated from time to time. Any changes will be reflected in the "Last updated" date above and published alongside the App's source code.

## Contact

If you have questions about this privacy policy, you may open an issue on the project's GitHub repository.
