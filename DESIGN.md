---
name: "Financial Management AI"
description: "A precise investment research cockpit for watchlists, alerts, knowledge context, and AI report review."
colors:
  primary-blue: "#006be6"
  dark-surface: "#1c1e23"
  dark-surface-deep: "#14161a"
  dark-foreground: "#f2f2f2"
  dark-border: "#36363a"
  dark-accent: "#2e3033"
  success-green: "#57d188"
  warning-amber: "#efbd48"
  destructive-red: "#dc4446"
  light-surface: "#ffffff"
  light-surface-deep: "#f1f3f6"
  light-foreground: "#333639"
typography:
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: 1.2
  heading:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.3
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary-blue}"
    textColor: "{colors.dark-foreground}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    height: "36px"
  card-panel:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-foreground}"
    rounded: "{rounded.md}"
    padding: "16px 18px"
  status-chip:
    backgroundColor: "{colors.dark-accent}"
    textColor: "{colors.dark-foreground}"
    rounded: "{rounded.sm}"
    padding: "4px 8px"
---

# Design System: Financial Management AI

## 1. Overview

**Creative North Star: "研究驾驶舱"**

This interface should feel like a focused investment research cockpit: compact, precise, and ready for repeated daily scanning. It is a product UI, so the design serves comparison, triage, and drill-down rather than brand spectacle.

The current visual base is Vben Admin with Element Plus, a dark default theme, system sans typography, restrained blue primary actions, dense data tables, ECharts charts, and panel-based layouts. Future redesign work should preserve the useful density while replacing template-like dashboard patterns with clearer investment context.

It explicitly rejects traditional backend administration templates, noisy Eastmoney-style retail trading pages, and marketing-oriented AI SaaS patterns.

**Key Characteristics:**
- Dark, restrained working surface with blue used for current selection and primary action.
- Dense but organized market data, alert state, report state, and knowledge context.
- Flat or lightly layered panels, 6-8px practical radii, and consistent Element Plus control vocabulary.
- State-first color: red, green, and amber communicate market or workflow state, not decoration.

## 2. Colors

The palette is a restrained dark product system with one blue command color and semantic market/status colors.

### Primary
- **Command Blue** (`#006be6`, `--primary: 212 100% 45%`): Primary buttons, selected navigation, active tab emphasis, chart highlights, and links. Use sparingly so it remains an action and selection signal.

### Secondary
- **Research Surface** (`#1c1e23`, `--background` / `--card` in dark mode): Main app and card surface. It should carry most authenticated screens.
- **Deep Workspace** (`#14161a`, `--background-deep`): Page background and lower visual layers behind panels.
- **Control Accent** (`#2e3033`, `--accent`): Hover states, inactive chips, muted toolbar controls, and low-priority containers.

### Tertiary
- **Signal Green** (`#57d188`, `--success`): Positive market movement, successful workflow states, and confirmation indicators.
- **Alert Amber** (`#efbd48`, `--warning`): Near-threshold alerts, pending states, and attention-needed status.
- **Risk Red** (`#dc4446`, `--destructive`): Negative market movement, failure states, and destructive actions.

### Neutral
- **Primary Text** (`#f2f2f2`, `--foreground`): Body text and high-priority data on dark surfaces.
- **Dark Divider** (`#36363a`, `--border`): Panel borders, table separators, input borders, and chart grid lines.
- **Light Surface** (`#ffffff`, `--background` in light mode): Optional light-mode content surface.
- **Light Workspace** (`#f1f3f6`, `--background-deep` in light mode): Optional light-mode page background.
- **Light Text** (`#333639`, `--foreground` in light mode): Primary light-mode body text.

### Named Rules

**The Signal Rarity Rule.** Blue, red, green, and amber must indicate action, selection, market direction, or workflow state. They should not be used as decorative fills.

**The No Trading-Portal Noise Rule.** Do not create competing saturated zones, blinking-feeling alerts, or dense color-coded clusters that make the page resemble a retail trading portal.

## 3. Typography

**Display Font:** System sans stack, no separate display face.  
**Body Font:** System sans stack.  
**Label/Mono Font:** System sans for labels; use tabular numeric features where data alignment matters.

**Character:** The typography should be functional, compact, and confident. Hierarchy comes from weight, spacing, and table structure rather than oversized headings.

### Hierarchy
- **Display** (700, 28px, 1.2): Page-level domain titles such as an active target name or the Investment Workbench title. Use rarely.
- **Headline** (700, 18-20px, 1.25): Major page sections and cockpit summary headers.
- **Title** (700, 16px, 1.2): Panel headings, table-adjacent sections, and chart titles.
- **Body** (400, 14px, 1.5): General product UI text, descriptions, table cells, and notes.
- **Label** (500, 12-13px, 1.3): Metadata, timestamps, filter labels, secondary values, and compact status text.

### Named Rules

**The Data Before Drama Rule.** Do not use hero-scale typography inside app screens. Headings should help scanning, not dominate the working surface.

**The Numeric Clarity Rule.** Financial values, percentages, prices, and counts should align cleanly and use consistent precision.

## 4. Elevation

The system is flat by default and uses tonal separation, borders, and density before shadow. Existing Vben tokens include `--shadow-float`, but investment screens should use it only for overlays, dropdowns, popovers, and temporary floating UI.

### Shadow Vocabulary
- **Float Shadow** (`0 6px 16px 0 rgb(0 0 0 / 8%), 0 3px 6px -4px rgb(0 0 0 / 12%), 0 9px 28px 8px rgb(0 0 0 / 5%)`): Popovers, menus, and transient overlays.
- **Panel Border** (`1px solid var(--el-border-color-light)` or `1px solid hsl(var(--border))`): Default separation for cards, dashboards, tables, and cockpit panels.

### Named Rules

**The Flat Working Surface Rule.** Persistent panels should not depend on soft shadows. Use background contrast, border, and layout rhythm first.

## 5. Components

### Buttons

- **Shape:** Practical rounded rectangles, usually 8px via `--radius: 0.5rem`; icon buttons may use 4-6px.
- **Primary:** Command Blue background with high-contrast foreground, 36px default height, compact horizontal padding.
- **Hover / Focus:** Slight color shift and visible focus ring. Do not add decorative glow.
- **Secondary / Ghost / Tertiary:** Use muted or transparent backgrounds with clear hover states. Reserve primary buttons for real forward actions.

### Chips

- **Style:** Compact tags using Element Plus tags or token-based muted backgrounds. Use green/red/amber only for state or market direction.
- **State:** Selected filters should be blue or clearly bordered. Passive labels should stay neutral.

### Cards / Containers

- **Corner Style:** 6-8px for most business panels; 12px is acceptable for reusable framework cards. Avoid 24px+ radii.
- **Background:** Use `--el-bg-color`, `--card`, or `--el-fill-color-lighter` depending on nesting depth.
- **Shadow Strategy:** Persistent cards are flat with borders. Floating UI may use Float Shadow.
- **Border:** Use token borders, not decorative side stripes.
- **Internal Padding:** 16-24px for page-level panels; 12-14px for dense metric items.

### Inputs / Fields

- **Style:** Element Plus controls are the baseline. Use dark input backgrounds and token borders.
- **Focus:** Ring or border shift must be visible and consistent.
- **Error / Disabled:** Use framework error and disabled states; never encode state by color alone.

### Navigation

- **Style:** Sidebar navigation with rounded active items and icon labels. The first Dashboard item should be the primary daily entry when the Investment Workbench ships.
- **Tabbar:** Chrome-style tabs are enabled. Only the default home page should be affixed by default.
- **Mobile:** Collapse dense grids and tables structurally; do not rely on fluid heading sizes.

### Charts and Tables

- **Charts:** ECharts should use restrained line/bar styles, visible tooltips, and semantic colors. Avoid overloaded legends and decorative gradients.
- **Tables:** Tables are allowed to be dense. Prioritize sticky affordances, readable numeric alignment, and useful empty/loading states.

## 6. Do's and Don'ts

### Do:

- **Do** lead app home screens with investment context: watchlist movement, configured alerts, report state, and evidence links.
- **Do** use `#006be6` / `--primary` for primary action and selection, not broad decoration.
- **Do** keep panel radii in the 6-12px range and use 16px grid gaps for dense dashboard layouts.
- **Do** preserve familiar Element Plus controls unless a replacement improves the workflow clearly.
- **Do** provide loading, empty, error, and disabled states for every workbench module.
- **Do** make charts and market-state panels readable without relying on color alone.

### Don't:

- **Don't** resemble a traditional backend administration template with generic dashboard cards and low-context metrics.
- **Don't** resemble noisy retail trading portals such as Eastmoney-style pages with dense ads, saturated market colors everywhere, visual clutter, and competing attention zones.
- **Don't** resemble a marketing-oriented AI SaaS page with oversized hero content, decorative gradients, vague AI copy, or feature-showcase layout patterns.
- **Don't** use colored side-stripe borders, gradient text, glassmorphism, decorative glow, or repeated identical card grids.
- **Don't** imply that AI makes investment decisions for the user. AI reports are reviewable evidence and workflow context.
- **Don't** add page-load choreography. Product screens should load into the task.
