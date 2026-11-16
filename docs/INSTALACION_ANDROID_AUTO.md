# Installing HomeCar on Android Auto / Instalación en Android Auto

> **This fork's v1.1.0-rc.1 is a prerelease, not an officially approved Android Auto application.** Actual phone, mobile-data and head-unit validation remains required. Installer origin tricks may help on some devices but do not remove all Android Auto validation restrictions. Avoid rooting a primary phone solely for this app.

## Español — Instalación de HomeCar 1.1.0-rc.1

### 1. Descarga correcta

Abre [Releases de este fork](https://github.com/Riftsan/HomeCar/releases) y selecciona **v1.1.0-rc.1**. En **Assets**, descarga `HomeCar-1.1.0-rc.1.apk`. **No descargues** "Source code (zip)", un archivo `.apks` ni el APK intermedio de CI para instalar en el teléfono.

Este APK usa una firma privada y persistente de publicación. Si una instalación anterior tiene el mismo identificador de paquete y otra firma, no se puede actualizar directamente: anota la dirección del servidor y prepara tus credenciales antes de desinstalarla. La desinstalación borra los datos locales. Los paquetes distintos, como `.auto.debug` y `.auto`, pueden coexistir.

### 2. Primer intento: instalación convencional

Instala el APK como cualquier aplicación Android. Abre HomeCar **en el teléfono**, pulsa el botón de ajustes e introduce la dirección completa de Home Assistant, empezando por `https://` o `http://`. Si el servidor HTTPS exige un certificado de cliente, selecciona el certificado ya instalado en el selector de Android. Pulsa **Save & open dashboard**, inicia sesión y verifica el acceso con Wi-Fi y datos móviles. La configuración de este fork está en inglés. Consulta la [guía de certificados](CLIENT_CERTIFICATES.md).

En Ajustes busca **Android Auto → Personalizar lanzador**. Si HomeCar aparece, actívalo. Si no aparece, sigue con KingInstaller.

### 3. Método alternativo: KingInstaller + Shizuku (sin root)

Descarga ambas herramientas desde sus repositorios originales:

- KingInstaller: https://github.com/fcaronte/KingInstaller/releases
- Shizuku: https://github.com/RikkaApps/Shizuku/releases

En Android 11 o posterior, con una red Wi-Fi disponible (puede ser un hotspot; no es necesario acceso a Internet para el emparejamiento local), abre Shizuku. En las **Opciones de desarrollador** de Android activa **Depuración inalámbrica**, elige **Emparejar dispositivo con código** e introduce el código mostrado por Android en la notificación de Shizuku. Después vuelve a Shizuku y pulsa **Iniciar**. Debe indicar que está en ejecución. Para Android 9 o 10, consulta el inicio mediante ordenador en el [manual oficial de Shizuku](https://shizuku.rikka.app/guide/setup/). En algunos dispositivos los pasos pueden variar.

Abre KingInstaller, concede autorización para usar Shizuku si se solicita, activa **Shizuku Trick**, selecciona `HomeCar-1.1.0-rc.1.apk` e instálalo. Los nombres de controles pueden variar entre versiones. KingInstaller recomienda intentar primero su instalación normal y usar Shizuku cuando falle la visibilidad.

Revisa **Android Auto → Ajustes de desarrollador → Fuentes desconocidas** si esta opción está disponible, y **Personalizar lanzador**. Reconecta Android Auto con el vehículo estacionado. En un entorno real, el mantenedor del proyecto original pudo abrir RC2 mediante este procedimiento; **ese resultado histórico no valida este fork ni significa compatibilidad universal**.

### 4. Problemas frecuentes

- **Instala, pero no aparece en el lanzador:** no insistas cambiando instaladores indefinidamente. Android Auto puede bloquear la categoría de proyección; consulta el [issue #9](https://github.com/RenanEspinar/HomeCar/issues/9). Registra modelo, sistema y versión de Android Auto.
- **Shizuku dejó de funcionar:** en Android sin root normalmente debes volver a iniciarlo tras reiniciar el teléfono.
- **No entra a Home Assistant:** comprueba conectividad, DNS, dirección del servidor, certificado HTTPS y sesión. No publiques tokens ni direcciones privadas al abrir un issue.
- **Error de actualización de APK:** versiones firmadas con claves distintas pueden requerir desinstalar. La desinstalación borra los datos locales de esa aplicación; prepara tus credenciales y configura de nuevo.
- **Dos iconos de HomeCar:** pueden corresponder a aplicaciones con identificadores distintos. Verifica paquete y versión desde los ajustes del sistema antes de borrar nada.
- **Fotos/vídeos:** el flavor Android Auto elimina los permisos heredados de fotos y vídeos. Si vuelven a pedirse, adjunta versión, captura y resultado sin datos sensibles.
- **mTLS:** elegir un certificado de cliente depende de KeyChain y de un certificado correctamente instalado; hay variaciones por servidor y dispositivo.

### 5. Seguridad y legalidad

HomeCar y KingInstaller son proyectos independientes de Google. Algunas instalaciones dependen de métodos no oficiales que pueden dejar de funcionar tras actualizar Android Auto/Play Services. No uses la aplicación para accionar puertas, portones, alarmas o cámaras mientras conduces. Prueba todo con el coche estacionado.

## English — Quick installation

HomeCar requires Android 9 (API 28) or newer. Menu names vary by Android version; on a Pixel, find the installed version under **Settings → About phone → Android version**. Meeting the minimum does not guarantee Android Auto compatibility.

1. Open this fork's [GitHub Releases](https://github.com/Riftsan/HomeCar/releases), select **v1.1.0-rc.1**, and download **`HomeCar-1.1.0-rc.1.apk`** from Assets. Use the signed release asset, not source ZIP, `.apks`, or CI's intermediate APK.
2. Open the APK on the phone. If prompted, tap **Settings → Allow from this source**, then return and install. On Pixel, this permission is also under **Settings → Apps → Special app access → Install unknown apps**, for the browser or Files app opening the APK. You can turn it off after installation. See [Google's Pixel installation instructions](https://support.google.com/pixelphone/answer/7391672?hl=en).
3. Open HomeCar on the **phone** and tap the settings button. Enter the full Home Assistant URL. If your HTTPS server requires mTLS, tap **Choose installed certificate** and select your existing certificate in Android's picker. Tap **Save & open dashboard**, sign in to Home Assistant, and test Wi-Fi and mobile data. See [certificate setup](CLIENT_CERTIFICATES.md).
4. While parked, check Android Auto's **Customize launcher** and enable HomeCar if listed. If missing, obtain [KingInstaller](https://github.com/fcaronte/KingInstaller/releases) and [Shizuku](https://github.com/RikkaApps/Shizuku/releases) from their official repositories.
5. On Android 11 or newer, start Shizuku using Wireless debugging pairing over Wi-Fi. Android 9 or 10 requires the computer-start method in [Shizuku's official manual](https://shizuku.rikka.app/guide/setup/). Authorize KingInstaller to access Shizuku, enable **Shizuku Trick**, select the same signed HomeCar APK and reinstall. Check Android Auto developer settings for **Unknown sources** if available, then reconnect while parked. Android Auto may still reject unsupported projection apps; the original project's [issue #9](https://github.com/RenanEspinar/HomeCar/issues/9) records that limitation.
6. If it remains absent, report device and Android Auto details on [this fork's GitHub Issues](https://github.com/Riftsan/HomeCar/issues) rather than repeatedly uninstalling. Protect your session data and credentials.

This fork's release APK uses a **private persistent signing identity**, not the upstream debug key. An installed app with the **same package but a different signing certificate** cannot be updated in place. Record its server address and prepare your Home Assistant login before uninstalling that older app; uninstalling clears its local settings and session. Upstream `.auto.debug` and release `.auto` packages can coexist. Future updates signed with this fork's identity can update the same package in place. There is no automatic installation of updates. See [signing and validation details](RELEASE_BUILD.md).
