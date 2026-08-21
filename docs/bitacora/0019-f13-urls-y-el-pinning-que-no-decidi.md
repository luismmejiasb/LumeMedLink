# 0019 · F13: las URLs, y el pinning que NO decidí

**Fecha:** 2026-08-21 · **Encargo:** F13, la mitad de Fase D que quedaba.

## Parte 1 — Nada personal en una URL (esto sí lo decidí)

**Por qué una URL no es como un cuerpo.** Un cuerpo de request lo lee el servidor y nadie más. Una
URL se copia, por defecto y sin que nadie lo elija, al log de acceso del servidor **con su query**,
a cada proxy/balanceador/CDN del camino y sus logs, a nuestra propia línea de log, y a los mensajes
de excepción — que viajan a reportes de crash, fuga que este repo **ya tuvo**.

O sea: un RUT en un query string es un RUT escrito en media docena de lugares que nadie auditó. El
mismo RUT en el cuerpo se escribe en uno. El backend ya tomó esa decisión para su búsqueda por RUT
(va en el body, nunca en la URL); el gate mantiene a la app del mismo lado.

Los identificadores en el **path** sí se permiten: son ids opacos del servidor, y el redactor los
reemplaza igual.

Gate `check-url-hygiene.sh`: 4 formas malas rojas, y **2 casos legítimos verdes**
(`professionalId`, `from`, y una ruta con id opaco) — porque un gate con falsos positivos se
desactiva solo.

**Los tests quedan fuera del gate, a propósito y dicho:** un test que prueba que el redactor quita
un RUT de una URL **tiene que construir** una URL con un RUT. Un gate que se pone rojo con la
evidencia de su propia defensa es un gate que el próximo apurado apaga.

## Parte 2 — El pinning: lo re-evalué y NO lo re-decidí

ADR-0004 difirió el pinning porque «ataca sólo T5, el nivel menos probable de este perfil».

**Dos entradas de esa decisión cambiaron:**

1. **F12 estableció que iOS confía en las CAs que instala el usuario y Android ya no.** Entonces en
   iOS esto dejó de ser sólo T5 (atacante de red): es **T1/T2**, los niveles que esta app pone
   *primero* — alguien con acceso al teléfono instala un perfil de configuración y el TLS es
   legible. El pinning es lo único que lo cierra.
2. **Una quinta asimetría** (reportada por la investigación, **no verificada por mí**): Apple exige
   Certificate Transparency a nivel de plataforma y Android no — y eso corre al revés, haciendo a
   Android el lado débil contra quien consiga un certificado mal emitido y escale.

**Lo que NO cambió: hoy el pinning es imposible.** No hay certificado de producción (sin GCP, sin
staging) y no hay host iOS donde poner un pin — y iOS es donde estaría todo el beneficio.

**La opción intermedia que encontró la investigación:** iOS *puede* distinguir una cadena validada
contra un ancla del sistema de una validada contra una raíz del usuario
(`kSecTrustResultUnspecified` vs `Proceed`). Eso le daría a iOS la propiedad que Android ya tiene,
**sin** el costo de rotación del pinning y **sin** necesitar un certificado.

**Por qué no lo implementé:** la evidencia es de simulador y la produjo una investigación, no yo.
Nadie confirmó que una CA instalada por perfil en un dispositivo físico reporte `Proceed`. Embarcar
una decisión de confianza TLS con evidencia sin verificar, en un repo que no tiene host donde
verificarla, sería exactamente el defecto que este programa lleva doce slices encontrando: un
control que se ve correcto y nunca se mide. Y un handler equivocado o rompe todo el tráfico o lo
acepta todo.

**Lo dejé como decisión abierta tuya**, con las cuatro recomendaciones en ADR-0017 Parte 2 — la más
urgente por plazo: preguntarle al backend **ahora** cómo va a manejar sus certificados, porque eso
decide si el pinning es siquiera operable.

## Lo que este slice NO puede afirmar

- Nada del mecanismo de anclas de iOS está verificado por esta sesión.
- La quinta asimetría está marcada como reportada, en el threat model, con esa marca.
- El gate ve **nombres** de parámetro, jamás valores: un dato personal pasado bajo un nombre neutro
  le es invisible. Por eso la doctrina se escribe además de gatearse.
