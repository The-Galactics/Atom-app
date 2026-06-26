# Project Atom: Minimalist AI Assistant - Technical Documentation

> **See also:** [`Atom-Redesign.md`](./Atom-Redesign.md) — the refined design
> system, redesigned screens, in-app input bar, and floating overlay that build
> on the vision below.

## 1. Executive Summary
Atom is a high-fidelity mobile AI assistant designed with an emphasis on minimalist aesthetics and low cognitive load. The interface prioritizes voice-first interaction, represented by a central, pulsating "Atom" core, set against a deep-space background.

## 2. Design Vision
- **Core Principle:** Radical Minimalism. Eliminating all non-essential UI elements to focus on the conversation.
- **Visual Language:** Sophisticated, ethereal, and responsive.
- **Target Experience:** A serene and intelligent presence that feels more like a companion than a utility.

## 3. Design System: "Aether"
The visual identity is anchored by the Aether design system, optimized for dark-mode OLED displays.

### 3.1 Color Palette
- **Background:** `#0A0A0C` (Deep Onyx) - Used for primary surfaces.
- **Primary Accent:** `#BF94FF` (Purple Lavender) - Used for the animated core and highlights.
- **Neutral/Surface:** `#121214` - For cards and input backgrounds (as seen in the Membership section).

### 3.2 Typography
- **Primary Font:** **Lora** (Serif) - For headings and AI responses to evoke wisdom.
- **Secondary Font:** **Inter** (Sans-serif) - For UI labels and settings.

### 3.3 Components
- **The Atom Core:** A multi-layered, animated SVG/CSS circle with breathing animations.
- **Glassmorphism:** Sidebar and cards use subtle transparency and borders.
- **Modern Inputs:** Rounded fields with focus states in Purple Lavender.

## 4. Screen Architecture & Scope

### 4.1 Main Assistant View
- **Purpose:** Primary interaction.
- **Features:** "Listening" state, Awaiting Prompt, and quick access to mic/keyboard.

### 4.2 Settings & Profile
- **Purpose:** Personalization.
- **Features:** User Profile (Julian), Assistant Persona (Lumina/Atom), and Membership tiers (Free, Pro, Elite).

## 5. Technical Scope & Implementation (Native Java Stack)
- **Frontend Layer (The App):** **Android Native** developed in **Java 21**. This repository contains only the UI/UX and client-side logic.
- **API Consumption:** **gRPC** (server-streaming `StreamChat` over `ai.proto`) to reach the Python AI agent. _(Originally Retrofit 2 + OkHttp against the Spring Boot REST API; the chat transport was cut over to gRPC — see ADR-001 §6.)_
- **UI Framework:** **Material Design 3** with custom XML layouts to achieve the "Aether" aesthetics (Gradients, Glassmorphism, and the Atom Core animation).
- **Animations:** **Hand-drawn custom Android Views** (`AtomCoreView`, Canvas drawing with radial/sweep gradients and `BlurMaskFilter`) for the pulsating "Atom Core" animation, with no external libraries (no Lottie, no Glide). Icons are resolved as vector drawables/mipmaps.
- **Build Tool:** Gradle (Android-specific configuration).
- **Landing Page:** A separate, high-conversion entry point developed in **TypeScript** by the TS expert (Hosted independently).

## 6. Export & Handoff
This blueprint aligns the visual tokens with a robust Java-centric implementation, ensuring that the minimalist UI remains performant and easy to maintain.
