# Gestión de Impedimentos [Atom]:

Para llevar un flujo de trabajo ordenado, se creó este documento con la finalidad de llevar registro de los stopers y blockers que se presentaron en medio del desarrollo del asistente de AI llamado Atom.

## Documentación y Seguimiento:

Para llevar un registro organizado de los stopers y blockers de cada sprint, se sugiere tener un orden para gestionar y registrarlos, siendo identificados por un, id (ej. Imp-01), una fecha para llevar un seguimiento exacto, el tipo de impedimento, ya sea un stopper o blocker, una breve descripción, como impacta esto en el desarrollo, la persona que se va a ser responsable de estos y el estado en el que se encuentra la tarea, con el fin de terminarla de raíz y ser conscientes de los puntos a mejorar. Permitiendo asi, tener una retroalimentación para nuestra retrospective al final del sprint.

### Estados del impedimento:
Los estados nos permiten llevar un seguimiento exacto de sí este problema se está solucionando o no; estos se dividirán en tres categorías y colores.

- **🔴 Pendiente:** Cuando apenas se detecta el stopper o blocker, y se está buscando al responsable correspondiente de la tarea para solucionarla.
- **🟡 En proceso:** Cuando ya se le asignó el stoper o blocker al responsable de su solución y se está trabajando en ello.
- **🟢 Finalizado:** Cuando se solucionó el impedimento de raíz.

### Impacto del impedimento:
El impacto del impedimento se determina por la prioridad y dificultad del proceso para la App, siendo categorizadas desde Alto hasta bajo, seguido del requisito funcional afectado por el impedimento.

- **Alto:** Cuando es una funcionalidad muy importante y prioritaria para el desarrollo de la App y a la larga generaria perdida de rendimiento en el flujo de trabajo.
- **Medio:** Cuando es algo importante, pero que no afectaría en gran medida al desarrollo y puede tener una solución más llevadera.
- **Bajo:** Cuando el impedimento directamente no afecta al funcionamiento de la App, pero debería ser tomado en cuenta.

### Detalle de Impedimentos y Soluciones:

Para cada impedimento debe de haber una estructura a seguir para saber cual fue el problema de raíz, que acciones se realizaron para solucionar el impedimento y por último la resolución del problema, donde se determina si el impedimento se terminó o se necesita validación del PO o Scrum Master para proceder.

### Ejemplo del flujo de trabajo y resolución de impedimentos:

|   ID   |   Fecha    |  Tipo   |                           Descripción                           |       Impacto        |       Responsable       |    Estado    |
|:------:|:----------:|:-------:|:---------------------------------------------------------------:|:--------------------:|:-----------------------:|:------------:|
| Imp-01 | 09-05-2026 | Stopper | Error de compatibilidad entre la burbuja flotante y Android 14. | Alto (Bloquea RF-04) | Scrum Master / Dev Team | 🔴 Pendiente |

---
#### [IMP-01] Compatibilidad Android 14
- **Causa raíz:** Los permisos de "Mostrar sobre otras aplicaciones" cambiaron en la última API.
- **Acción:** Investigar los nuevos requerimientos de seguridad de Google.
- **Resolución:** *Pendiente de validación.*

---

# Impedimentos-Sprint 1:

|   ID    |    Fecha    |  Tipo   |                        Descripción                        |         Impacto         | Responsable  |     Estado      |
|:-------:|:-----------:|:-------:|:---------------------------------------------------------:|:-----------------------:|:------------:|:---------------:|
| Imp-0 1 | 04-06- 2026 | Stopper |       Prob lema en la creación de ramas desde Jira        | Alto(Bloquea git-flow)  | Scrum Master |  🟢 Finalizado  |
| Imp-02  | 04-06-2026  | Blocker | Falta de encarpetado por eliminación automatica de GitHub | Alto(Bloquea work-flow) | Scrum Master |  🟢 Finalizado  |

---
####  [Imp-01] Creación de ramas desde Jira

- **Causa raíz:** Al momento de intentar crear la rama directamente desde Jira, el repositorio no era visible como opción para la creación del mismo.
- **Acción:** Se creó la vinculación del repositorio de la organización para facilitar la creación de las ramas.
-  **Resolución:** *Finalizado.*

#### [Imp-02] Estructura principal

- **Causa raíz:** Al momento de crear el encarpetado, por el desconocimiento de la función automática de GitHub de eliminar las carpetas sin archivos, no se subió la estructura principal del proyecto.
- **Acción:** Con el nuevo conocimiento adquirido, se volvió a crear el encarpetado, pero esta vez con archivos plantillas para la permanencia de las carpetas.
- **Resolución:** *Finalizado.*

---

# Impedimentos-Sprint 2:

|    ID    |   Fecha    |   Tipo   |                      Descripción                      |            Impacto            | Responsable |     Estado     |
|:--------:|:----------:|:--------:|:-----------------------------------------------------:|:-----------------------------:|:-----------:|:--------------:|
| Impl-01  | 11-06-2026 | blocker  | Falta de asistencia a las dailys por parte del equipo | Medio(desconocimiento de HUs) | Scrum Team  | 🟢 Finalizado  |
| Impl-02  | 12-06-2026 | stopper  |    Error al momento de compilar el backend de java    |      Alto(Bloquea HU-11)      | Developers  | 🟢 Finalizado  |  

---

### [Impl-01] Inasistencia del equipo a la daily

- **Causa raíz:** Al momento de la daily ocurrieron inconvenientes en la comunicación.
- **Acción:** Recordar la hora de la daily y buscar más compromiso por parte de todos los integrantes.
- **Resolución:** *Finalizado*.

### [Impl-02] Problemas de typo

- **Causa raíz:** Al momento de probar el codigo de Java este no compiló.
- **Acción:** Crear ramas para solucionar los problemas.
- **Resolución:** *Finalizado*
