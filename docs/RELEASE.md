# Release 0.28.0

Die APK wird mit einem eigenen PKCS#12-Schlüssel signiert. Schlüssel und Passwort
liegen ausschließlich im privaten Repository `schnitzltoast95/HaulaufDev` unter
`apps/Finanzkumpel/signing/`. Dieses öffentliche Repository enthält sie nicht.

## Auf einem neuen Rechner bauen

1. Das private Depot klonen und dessen `apps/Finanzkumpel/README.md` lesen.
2. Im Projektstamm eine nicht versionierte `keystore.properties` anlegen:

   ```properties
   storeFile=C:/Pfad/zum/privaten/HaulaufDev/apps/Finanzkumpel/signing/finanzkumpel-release.p12
   storePassword=<Inhalt von password.txt>
   keyAlias=finanzkumpel-release
   keyPassword=<Inhalt von password.txt>
   ```

3. `./gradlew.bat :app:assembleRelease` ausführen. Die APK liegt unter
   `app/build/outputs/apk/release/app-release.apk`.
4. Die APK mit `apksigner verify --verbose --print-certs` prüfen. Der
   Zertifikat-Fingerprint muss
   `7D8751434F02AC31F5F11881DBE6F5BA904E169CC8CD307A4F44F7170A77CA52`
   sein.

Der geprüfte Build 0.28.0 hat APK-SHA-256
`8CE9D2AF64CF881344757BFB0DB9FB907FF73C518A4529BCB0B8D80BEED0E809`.
Eine identische Kopie ist unter `releases/Finanzkumpel-0.28.0.apk`
versioniert.

`keystore.properties` und der private Schlüssel sind in `.gitignore` ausgeschlossen.
Ohne `keystore.properties` erzeugt Gradle nur eine unsignierte Release-Variante.
Die früheren `dist`-APKs tragen eine Debug-Signatur. Für den Wechsel zur ersten
Release-APK müssen solche Installationen nach einer Datensicherung neu installiert
werden; die Signatur erlaubt kein direktes Update.
