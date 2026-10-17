<!--
SPDX-FileCopyrightText: The LineageOS Project
SPDX-License-Identifier: Apache-2.0
-->

Updater
=======
Simple application to download and apply OTA packages.


Server requirements
-------------------
The app sends `GET` requests to the URL defined by the `updater_server_url`
resource (or the `evolution.updater.uri` system property) and expects as response
a JSON with the following structure:
```json
{
  "response": [
    {
      "buildtype": "user",
      "download": "https://example.com/EvolutionX-17.0-20261010-marble-12.3-Official.zip",
      "filename": "EvolutionX-17.0-20261010-marble-12.3-Official.zip",
      "sha256": "fd2e25df5a8f49a87e82ca052f99ffac5187b145c1867ad0c02f500b575eedaf",
      "size": 3624880471,
      "timestamp": 1791622433,
      "version": "12.3"
    }
  ]
}
```

The `buildtype` optional attribute is the build type of the OTA update.  
The `download` attribute is the URL of the file to be downloaded.  
The `filename` attribute is the name of the file to be downloaded.  
The `os_patch_level` optional attribute is the security patch level of the OTA update.  
The `os_sdk_level` optional attribute is the SDK level of the OTA update. It defaults to the running one.  
The `ota_property_files` optional attribute is the `ota-property-files` value from `META-INF/com/android/metadata` of the OTA update.  
The `sha256` attribute is a sha256 of the OTA update.  
The `size` attribute is the size of the update expressed in bytes.  
The `timestamp` attribute is the build date expressed as UNIX timestamp.  
The `version` attribute is the Evolution X version of the OTA update.  

Additional attributes are ignored.


Build with Android Studio
-------------------------
Updater needs access to the system API, therefore it can't be built only using
the public SDK. You first need to generate the libraries with all the needed
classes. The application also needs elevated privileges, so you need to sign
it with the right key to update the one in the system partition. To do this:

 - Place this directory anywhere in the Android source tree
 - Generate a keystore and keystore.properties using `gen-keystore.sh`
 - Build the platform artifacts that provide the non-public classes used by
   the app. At minimum, the jars copied by `pull-system-libs.sh` must exist
   under `out/soong/.intermediates/`
 - Run `./pull-system-libs.sh` from this directory. By default it reads from
   `../../../out` relative to this repository path and will populate
   `system_libs/` with the jars Gradle expects:
   - `framework.jar`
   - `SettingsLib.jar`
   - `SpaLib.jar`
 - If your build output lives somewhere else, pass it explicitly:
   `./pull-system-libs.sh /path/to/out`

You need to do the above once, unless Android Studio can't find some symbol.
In that case, rebuild the relevant platform targets and rerun
`./pull-system-libs.sh`.
