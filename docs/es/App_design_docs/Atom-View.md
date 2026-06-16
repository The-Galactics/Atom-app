# Proyecto Atom: Asistente de IA Minimalista - Documentación Técnica

> **Ver también:** [`Atom-Redesign.md`](./Atom-Redesign.md) — el sistema de
> diseño refinado, las pantallas rediseñadas, la barra de entrada in-app y el
> overlay flotante que se construyen sobre la visión siguiente.

## 1. Resumen ejecutivo
Atom es un asistente de IA móvil de alta fidelidad, diseñado con énfasis en la estética minimalista y la baja carga cognitiva. La interfaz prioriza la interacción por voz, representada por un núcleo "Atom" central y pulsante, sobre un fondo de espacio profundo.

## 2. Visión de diseño
- **Principio fundamental:** Minimalismo radical. Eliminar todos los elementos de UI no esenciales para centrarse en la conversación.
- **Lenguaje visual:** Sofisticado, etéreo y responsivo.
- **Experiencia objetivo:** Una presencia serena e inteligente que se siente más como un compañero que como una utilidad.

## 3. Sistema de diseño: "Aether"
La identidad visual se ancla en el sistema de diseño Aether, optimizado para pantallas OLED en modo oscuro.

### 3.1 Paleta de colores
- **Fondo:** `#0A0A0C` (Ónix profundo) - Usado para superficies primarias.
- **Acento primario:** `#BF94FF` (Lavanda púrpura) - Usado para el núcleo animado y los resaltes.
- **Neutro/Superficie:** `#121214` - Para tarjetas y fondos de entrada (como se ve en la sección de Membresía).

### 3.2 Tipografía
- **Fuente primaria:** **Lora** (Serif) - Para títulos y respuestas de la IA, para evocar sabiduría.
- **Fuente secundaria:** **Inter** (Sans-serif) - Para etiquetas de UI y ajustes.

### 3.3 Componentes
- **El núcleo Atom:** Un círculo SVG/CSS animado y multicapa con animaciones de respiración.
- **Glassmorphism:** La barra lateral y las tarjetas usan transparencia y bordes sutiles.
- **Entradas modernas:** Campos redondeados con estados de foco en lavanda púrpura.

## 4. Arquitectura de pantallas y alcance

### 4.1 Vista principal del asistente
- **Propósito:** Interacción primaria.
- **Características:** Estado "Escuchando", Esperando instrucción, y acceso rápido a micrófono/teclado.

### 4.2 Ajustes y perfil
- **Propósito:** Personalización.
- **Características:** Perfil de usuario (Julian), Persona del asistente (Lumina/Atom), y niveles de membresía (Free, Pro, Elite).

## 5. Alcance técnico e implementación (stack nativo Java)
- **Capa frontend (la app):** **Android nativo** desarrollado en **Java 21**. Este repositorio contiene solo la UI/UX y la lógica del lado del cliente.
- **Consumo de API:** **gRPC** (server-streaming `StreamChat` sobre `ai.proto`) para alcanzar el agente de IA en Python. _(Originalmente Retrofit 2 + OkHttp contra la API REST de Spring Boot; el transporte del chat se migró a gRPC — ver ADR-001 §6.)_
- **Framework de UI:** **Material Design 3** con layouts XML personalizados para lograr la estética "Aether" (gradientes, glassmorphism y la animación del núcleo Atom).
- **Carga de imágenes/animaciones:** **Lottie** para la animación pulsante del "núcleo Atom" y **Glide** para el procesamiento de imágenes.
- **Herramienta de build:** Gradle (configuración específica de Android).
- **Landing page:** Un punto de entrada separado y de alta conversión desarrollado en **TypeScript** por el experto en TS (hospedado de forma independiente).

## 6. Exportación y entrega (handoff)
Este blueprint alinea los tokens visuales con una implementación robusta centrada en Java, asegurando que la UI minimalista permanezca performante y fácil de mantener.
