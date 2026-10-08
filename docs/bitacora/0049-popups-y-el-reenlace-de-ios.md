# 0049 — Los popups del kit, medidos y arreglados; y el reenlace de iOS sin borrar nada

**Tipo:** `medición` + `fix` · 2026-10-08

## 0014 · Los popups del kit frente al endurecimiento de ventana

Un verificador nuevo, `Scripts/verify-popup-hardening.sh`, con la alerta del flujo abierta y una superposición
semitransparente de **otra** app (el APK de test, con `SYSTEM_ALERT_WINDOW` sólo ahí). Resultado:

- FLAG_SECURE **sí** llega al popup: la captura con la alerta abierta sale negra (control: el launcher, no).
- `filterTouchesWhenObscured` **no** llegaba: un toque a través de la superposición cerraba la alerta, mientras la
  ventana principal lo rechazaba. Un `Popup` de Compose es otra ventana.
- El autofill no aplica: ningún popup del kit tiene un campo.

El kit lo arregló el mismo día (sus popups rechazan el toque obscurecido, siempre); re-medido, verde.

Dos golpes del instrumento, atrapados por controles: un argumento con espacios se parte en `adb shell`, y el primer
blanco de la ventana principal abría una pantalla titulada con las mismas palabras — sin el control «sin superposición»,
la protección de la ventana principal se habría dado por medida sin estarlo.

## 0025 · El primer build tras un cambio de Kotlin fallaba al firmar

El mecanismo de ADR-0030 borraba el producto enlazado desde el build phase, después de que Xcode planificó el build sin
paso de enlace. Ahora un archivo Swift generado lleva el sello del framework, declarado como salida y compilado desde
Sources (una salida sola no se compila — medido). Un cambio sólo de Kotlin llega al binario en un build firmado.

Y por qué nadie lo vio: `verify-ios-link-freshness.sh` compilaba con `CODE_SIGNING_ALLOWED=NO`, sin paso de firma — el
único lugar donde fallaba. Ahora firma, y su control congela el sello.
