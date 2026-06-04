# Fintech Web App Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the LotteryNet web admin panel into a modern fintech-style web app with light and dark modes.

**Architecture:** Keep the existing React/Vite structure and business logic. Change the visual system through global tokens and focused shell/dashboard markup updates, avoiding a broad component rewrite.

**Tech Stack:** React 19, Vite, TypeScript, CSS variables, lucide-react.

---

### Task 1: Fintech Design Tokens

**Files:**
- Modify: `src/index.css`

- [x] Replace the current heavy/dark panel styling with a light-first fintech palette, dark-mode tokens, compact radii, crisp borders, and app-specific utility classes.
- [x] Keep `.glass-panel` and `.glass-panel-premium` class names for compatibility, but make them clean app surfaces instead of glass cards.
- [x] Add responsive classes for dashboard toolbars, stat strips, and operational panels.
- [x] Run `npm run build` and confirm TypeScript/CSS build succeeds.

### Task 2: App Shell

**Files:**
- Modify: `src/components/AppShell.tsx`

- [x] Default to light theme when no saved preference exists.
- [x] Make the shell feel like a fintech workspace: compact sidebar, stable topbar, clear account context, and no decorative effects.
- [x] Preserve all current navigation and role behavior.
- [x] Run `npm run build`.

### Task 3: Dashboard Surface

**Files:**
- Modify: `src/views/Dashboard.tsx`

- [x] Convert the summary dashboard from stacked generic blocks into a fintech operations surface: compact filter toolbar, horizontal stat strip, table-first main panel, and right ledger panel.
- [x] Keep the existing `Normales / Picks` filter and closed-lottery hiding behavior.
- [x] Do not change Supabase/data logic.
- [x] Verify in browser at `http://localhost:5174/`.
