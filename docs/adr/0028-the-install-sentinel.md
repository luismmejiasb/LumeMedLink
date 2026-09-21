# ADR-0028 — El sentinel de instalación: una reinstalación jamás hereda la sesión anterior

- **Status:** Accepted · 2026-09-07 (fortification slice F7)
- **Related:** §8.14 (la asimetría declarada), §8.13 (el contrato de logout), §8.17 (dispositivo
  compartido), ADR-0005/0009 (los almacenes), ADR-0025 (el host iOS, cuya build phase resultó estar
  rota), **ADR-0037 de LumeMed** (el precedente, del que esto hereda entero).

## Contexto

El Keychain de iOS **sobrevive a que se borre la app**; el contenedor no. Borrar la app y volver a
instalarla deja a la instalación nueva encontrando los secretos de la anterior — una sesión heredada
entre dos personas que usan el mismo teléfono, que es exactamente lo que §8.13 promete que un logout
impide, ocurriendo **sin logout**. §8.17 dice además que en esta app esa amenaza es *más* probable
que en LumeMed: el teléfono de un paciente lo usa la familia.

`ADR-0037 de LumeMed` ya resolvió esto para la app clínica, y esta ADR hereda su diseño entero.

## Decisión

**1. El marcador es un ARCHIVO en el contenedor, jamás una preferencia.** La *ausencia* del
contenedor es el hecho que se detecta; un archivo no tiene caché de plist ni ambigüedad de dominio.

**2. Tres propiedades del archivo, cada una sostenedora:**

- **Sin protección de datos** (`NSFileProtectionNone`). Es un booleano no sensible y **debe leerse
  antes del primer desbloqueo del teléfono**. Si no, un arranque en background con el device
  bloqueado no puede leerlo, concluye «instalación fresca» y **purga una sesión válida** — el modo
  de fallo mass-logout documentado de este patrón, y la razón por la que «proteger todo» sería aquí
  un defecto y no prudencia.
- **Excluido del backup.** Un restore aterriza con el Keychain repoblado y **sin** marcador, así que
  el guardián dispara y purga también el residuo restaurado.
- **En Application Support**, no en Caches (el sistema la desaloja bajo presión de disco, y un
  marcador desalojado se lee como instalación fresca) ni en Documents (superficie visible al
  usuario donde esta app no tiene por qué escribir).

**3. Purgar, después marcar.** Marcar primero haría **permanente** una purga interrumpida: el
contenedor parecería establecido mientras todavía guarda los secretos del install anterior, y
ningún arranque posterior volvería a mirar. En este orden una interrupción cuesta una purga extra, y
la purga es idempotente.

**4. Si algo falla, el contenedor queda SIN marcar.** Deslogueado es el estado honesto cuando los
secretos pueden haberse heredado. La alternativa tentadora —marcar igual para no molestar dos veces—
convierte un fallo transitorio en una herencia permanente.

**5. El alcance es el namespace propio y nada más.** Jamás las cinco `kSecClass`, jamás
`kSecAttrSynchronizableAny` — un barrido sincronizable propagaría el borrado a los **otros
dispositivos** del usuario por iCloud Keychain. Con gate, porque este código está en la posición
perfecta para romper esa regla.

**6. Android es un no-op DECLARADO, no una omisión.** Desinstalar borra el directorio de datos y las
entradas de Keystore: no hay nada que heredar. Y si el backup se re-habilitara, **el marcador se
respaldaría con todo lo demás**, así que un restore aterrizaría ya marcado y el sentinel se saltaría
la purga — un marcador no puede defender el caso que defiende F6. Esa es la razón real de no tenerlo
allá, no la mera redundancia.

## Consecuencias

**Verificado en un runtime real, con control positivo que funciona.**
`Scripts/verify-install-sentinel.sh` siembra un secreto sintético, borra la app, reinstala y observa:

| | con el sentinel | control en vivo (sin él) |
| --- | --- | --- |
| tras reinstalar (**la premisa**) | `PRESENT` | `PRESENT` |
| tras correr el guardián (**el arreglo**) | **`ABSENT`** | **`PRESENT`** |

La fila de la premisa es la que hace que el resto signifique algo: si el Keychain **no** sobreviviera
al borrado, el guardián no defendería nada. Y el control en vivo reproduce el agujero, así que el
guardián está probado como **sostenedor** y no supuesto.

**Es la primera propiedad de seguridad de iOS que este repositorio verifica de verdad.**

**Migración única, aceptada y declarada:** el primer arranque tras el update que introduce el
marcador es indistinguible de una reinstalación —no hay marcador en ninguno de los dos casos— así
que las instalaciones existentes re-autentican una vez.

**Lo que NO se hizo, a propósito:** una purga no emite evento de seguridad. Ni nuestro vocabulario ni
el de la plataforma tienen un nombre para «un contenedor fresco traía secretos», y el canal está sin
cablear (`SecurityEventKind` cerrado, ADR-0023). Agregar un kind que no se enviaría a ninguna parte
sería ceremonia. Queda como trabajo debido para el día que el canal se conecte.

**El marcador no es señal de integridad ni antifraude.** Distingue «este contenedor ya corrió» de
«este contenedor es nuevo» y nada más: quien pueda escribir en el contenedor ya está dentro del
sandbox.

## Y lo que esta slice encontró en el camino: el host enlazaba Kotlin viejo

El control en vivo **falló la primera vez**: sin el guardián, el secreto desaparecía igual. La causa
no era el guardián sino el **host**. La build phase corría
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` sin `KOTLIN_FRAMEWORK_BUILD_TYPE`, y sin
esa variable el plugin de Kotlin imprime *«Unable to detect Kotlin framework build type»* como
**warning**, no refresca `build/xcode-frameworks`, y el enlazado usa el framework que quedó ahí.

**El build queda verde mientras la app corre Kotlin viejo.** Medido: la fuente parcheada a las
10:37:43, el framework enlazado del 10:36:38.

Eso invalida **toda observación de iOS entre el 2026-08-25 y el 2026-09-07**, y en particular el
control de bitácora 0023 que «probó» algo parcheando Kotlin que nunca llegó al binario. La
conclusión de ADR-0025 sobre el cover de privacidad **se retira por no probada** — no se reemplaza
por otra, porque volver a medirla quedó inconcluso y afirmar lo contrario sería repetir el error en
espejo.

`Scripts/check-ios-host.sh` ahora exige la variable. Es la aserción de más valor de ese gate, porque
su ausencia **falla en silencio**.

---

## Nota — 2026-09-21

Lo que esta ADR cuenta del framework rancio es cierto y era **la mitad de la cadena**. Fijar
`KOTLIN_FRAMEWORK_BUILD_TYPE` volvió fresca la salida de Gradle; el **enlace** siguió sin
refrescarse, así que un cambio sólo-Kotlin seguía sin llegar al binario. Medido el 2026-09-21 y
cerrado en **ADR-0030**, que amplía la ventana de observaciones iOS invalidadas: la de aquí se
cerró el 2026-09-07, la otra nunca se había cerrado.
