# Documentación técnica: Conexión gRPC [ATOM].

> **Autor:** Emmanuel Suárez García.
> **Contenido:** Infraestructura de conexión.
> **Estado:** 🟢 Implementado.

---

Este documento busca dar a conocer el funcionamiento de la conexión creada por medio del cliente gRPC para conectar el backend de Java con el servicio de Python donde se crean las respuestas o interacciones enviadas por el usuario a través de la aplicación mobile. Se explicará la gestión del ciclo de vida de la conexión y el funcionamiento de sus métodos.

## 1. Creación o modificación del contrato proto:

Para crear nuestra conexión con gRPC necesitamos primero un contrato donde se definan las reglas principales para el funcionamiento del mismo, como lo serian:

- **Package** Este seria el encargado de gestionar la root para crear las clases del contrato
- **Configuración:** En este apartado se definen las dos funcionalidades más importantes del contrato, las cuales serian, la creación de múltiples clases para un mejor manejo de peticiones por partes del cliente (Java) hacia el servidor (servicio/Python); por último la creación de una clase contenedora de la metadata creada por gRPC.
- **Servicio:** Este apartado brinda la funcionalidad al contrato, donde se utilizarian dos métodos principales, los cuales serian ExecuteCommand y StreamChat, los que permitirian enviar y solicitar información al servidor(servicio/Python).
- **Clases/Parámetros:** Estos serian la parte vital del funcionamiento del contrato, donde definimos que información vamos a enviar y que posición del espacio binario va a utilizar cada campo.


### 1.2 Métodos y parámetros:

El contrato proto debe tener una funcionalidad definida por medio de métodos que puedan ser usados por el usuario al momento de hacer una petición, en el contrato proto, hay 2 métodos principales ExecuteCommand y StreamChat, los cuales estan destinados para una funcionalidad especifica.

**ExecuteCommand:** Este método sincrono, permite al usuario ejecutar un comando de ejecución, como lo seria generar una interacción en el dispositivo, este método utiliza dos clases obligatoria, la primera seria el parámetro de entrada **CommandRequest**, y este metodo retornaria la clase **CommandResponse**.

**StreamChat:** Este metodo asincrono, permite al usuario enviar un mensaje, el cual seria respondido por la AI; es asincrono, ya que permite mostrarle al usuario token por token la respuesta, para dar el efecto de que la AI esta respondiendo a la par que piensa. En este metodo se utilizan dos clases, el parámetro de entrada **MessageRequest**, y la clase de retorno asincrona **MessageResponse** la cual utiliza **stream** lo que permite mantener la conexión constantemente abierta para recibir información por parte del servidor(servicio/Python).

---
> **Acesso Directo:**
> [Contrato proto](../../../../../java/src/main/proto/ai.proto)


## 2. Ciclo de vida de la conexión gRPC:

Para mantener el rendimiento del backend se creo un ciclo de vida para la conexión, con el fin de evitar dejar abiertos sockets de conexión, dejando de consumir recursos en un proceso que no se esta utilizando. Este ciclo de vida esta gestionado por:

- **variables de entorno:** Para poder crear nuestra conexión debemos tener en cuenta que necesitamos un host y un puerto, los cuales son traidos desde las properties e inyectados por el constructor para mantener el encapsulamiento del adaptador.
-  **Conexión:** Para crear nuestra conexión primero instanciamos nuestro canal (Socket), el cual se encarga de mantener la conexión viva, cerrarla y crearla;  por otra parte nuestro stub (Cliente/Proxy) trabaja por debajo serealizando las clases del contrato como lo son **CommandRequest** o **MessageRequest**.
- **Ejecución:** Esta parte es donde nuestros métodos del contrato cobran vida, donde definimos su funcionalidad principal, como lo serian la construcción de la petición y la respuesta de esta misma. 
- **Cierre:** Para no gastar recursos en exceso del sistema se creo un método para el cierre del socket de conexión para evitar fugas.

### 2.1 Variables de entorno:
Para poder crear nuestra conexión con **ManagedChannel** debemos de tener nuestro puerto y nuestro host en nuestro properties para no hardcodear información, las variables necesarias en el pproperties son:

**grpc.agent.host:** esta seria nuestra llave en el properties para el host, pero el valor de esta seria **${GRPC_AGENT_HOST:localhost}**, permitiendo la fácil integración de la anotación @Value de spring boot, el cual busca el valor que tiene que inyectar en la variable donde se utiliza la anotación. para cambiar el host, debes cambiar el valor despues de los : de la variable.

**grpc.agent.port:** esta seria nuestra llave en el properties para el puerto, pero el valor de esta seria **${GRPC_AGENT_PORT:5000}**, permitiendo la fácil integración de la anotación @Value de spring boot, el cual busca el valor que tiene que inyectar en la variable donde se utiliza la anotación. Para cambiar el puerto, debes cambiar el valor despues de los : de la variable.

### 2.2 Creación y cierre de conexión:

Para la gestión de la creación y cierre de conexión se crearon dos métodos esenciales los cuales son:

**init():** Este método es el encargado de crear la conexión del socket por medio de variables de entorno ubicadas en el properties, aqui se utilizan nuestras instancias de **ManagedChannel** (channel) y nuestro servicio del contrato proto **AtomAgentServiceGrpc** (blockingStub). Primero creamos nuestro canal levantando nuestra red con **ManagedChannelBuilder**, donde añadimos nuestras variables de puerto y de host, después creamos nuestro stub con **newBlockingStub** el cual vinculamos a la conexión que creamos anteriormente.
**shutdown:** Este método es el encargado de asegurar un "cierre limpio" de los recursos del sistema, primero se genera una validación para verificar si se creo la conexión de nuestro canal al ejecutar el programa, después verificamos que la conexión no este cerrado ya, esto permite saber si se puede apagar la conexión o no; si estas validaciones se cumplen procedemos a cerrar la conexión con **channel.shutdown()**, dejando de recibir peticiones y terminando los procesos faltantes (Graceful Shutdown).

---

> **Acesso Directo:**
> [Integración de contrato](../../../../../java/src/main/java/com/atom/infrastructure/adapter/grpc/InteractionGrpcAdapter.java)

---
