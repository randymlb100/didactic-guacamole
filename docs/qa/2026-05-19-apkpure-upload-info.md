# AppPure upload verification info

Fecha: 2026-05-19

## APK correcto para subir

Archivo:

`C:\Users\Randy Cordero\Desktop\lotterynet_android\app\build\outputs\apk\release\lotterynet-kotlin-v1.0.13-kotlin-release.apk`

## Datos que debe tener AppPure

App name:

`LotteryNet Pro`

Package name:

`com.lotterynet.pro`

Version code:

`14`

Version name:

`1.0.13-kotlin`

Certificate SHA-1:

`CD:FF:11:D8:0F:68:34:95:98:3F:24:BF:19:5D:E1:E6:6A:DD:3B:39`

Certificate SHA-256:

`FB:F4:4A:F7:70:41:CF:63:E0:B7:E0:C3:76:59:93:FD:C0:23:9B:8D:48:EB:EF:0F:FF:E3:22:86:0A:5B:BA:5F`

Certificate DN:

`CN=LotteryNet Pro, OU=Mobile, O=LotteryNet, L=Santo Domingo, ST=Distrito Nacional, C=DO`

## Causa probable del error

El mensaje de AppPure:

`The version your uploaded didn't pass the upload verification. Please check if your package name or signature matches the information you provided.`

significa que en AppPure hay uno de estos datos distinto:

- Package name diferente a `com.lotterynet.pro`.
- Firma SHA diferente a la del APK.
- Se subio otro APK que no es el release correcto.
- La app en AppPure fue creada antes con otro paquete o con otra firma.

## Que llenar en AppPure

Usar exactamente:

- Package name: `com.lotterynet.pro`
- App name: `LotteryNet Pro`
- Version: `1.0.13-kotlin`
- Version code: `14`
- APK: `lotterynet-kotlin-v1.0.13-kotlin-release.apk`

Si AppPure pide firma/certificate:

- SHA-1: `CD:FF:11:D8:0F:68:34:95:98:3F:24:BF:19:5D:E1:E6:6A:DD:3B:39`
- SHA-256: `FB:F4:4A:F7:70:41:CF:63:E0:B7:E0:C3:76:59:93:FD:C0:23:9B:8D:48:EB:EF:0F:FF:E3:22:86:0A:5B:BA:5F`

## Si vuelve a fallar

Crear una app nueva en AppPure con package name `com.lotterynet.pro`, o cambiar la informacion de firma/package en AppPure para que coincida con este APK.

