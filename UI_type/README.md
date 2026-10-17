# Aven UI Reference Templates

This folder contains the HTML UI templates that serve as the **visual reference and target design** for the Aven Android application.

These files are **reference designs only**. They must be reproduced in the existing Aven Jetpack Compose implementation.

## Purpose

The goal is to make the existing Aven app visually match these templates as closely and accurately as possible **without replacing or breaking the existing Aven architecture, functionality, or behavior**.

The HTML templates are the **visual source of truth** for the UI.

## Reference Files

* `01_main_chat.html` — Main Chat screen
* `02_navigation_drawer.html` — Navigation Drawer
* `03_settings.html` — Settings screen
* `04_incognito_chat.html` — Incognito Chat screen
* `05_model_selection.html` — Model Selection screen

## Important Rules

### 1. Do NOT replace the existing app

Do not replace the current Aven implementation with a new HTML/WebView-based UI or a separate application structure.

The templates must be **translated/recreated using the existing Jetpack Compose UI architecture**.

### 2. Do NOT redesign the templates

The templates are not inspiration.

Do not:

* redesign them
* simplify them
* modernize them
* create an alternative layout
* invent a different visual style
* remove UI elements just because they seem unnecessary

Reproduce the intended visual structure, spacing, sizing, colors, typography, shapes, icons, controls, and hierarchy as accurately as possible.

### 3. Preserve existing functionality

The following must continue working:

* Existing chat functionality
* Existing message handling
* Existing API/model integration
* Online models
* Local model functionality
* Model selection logic
* Navigation
* Settings
* Incognito mode
* Composer/input behavior
* Keyboard and IME handling
* Window/inset handling
* State management
* ViewModels
* Persistence
* Existing Aven-specific features

UI changes must not silently remove or break existing functionality.

### 4. Preserve Aven's identity

The templates may contain branding, names, icons, or model names belonging to another product.

Do **not** copy external branding or replace Aven's identity.

Use the templates for their:

* Layout
* Visual hierarchy
* Spacing
* Typography
* Colors
* Components
* Interaction patterns

Keep Aven's actual branding, models, functionality, and terminology where applicable.

### 5. Do NOT guess missing information

If something is unclear, missing, contradictory, or cannot be determined from the templates and existing Aven code:

**Do not guess.**

Identify it as a question and ask for clarification before implementation.

Use this priority:

1. Existing Aven code/evidence
2. UI reference templates
3. User clarification
4. Implementation

Never:

`Existing code → assumption → implementation`

## Implementation Philosophy

The workflow should be:

**Understand → Compare → Identify gaps → Ask questions → Clarify → Implement → Verify**

Before making changes, inspect both the existing Aven implementation and every reference template.

The implementation should modify only the UI/components necessary to reproduce the reference designs while preserving the existing architecture and behavior.

## Expected Result

The final Aven UI should closely reproduce the provided reference templates while remaining a fully functional native Jetpack Compose application.

The templates define **how Aven should look**.

The existing Aven code defines **how Aven works**.

Both must be preserved and integrated rather than replacing one with the other.
