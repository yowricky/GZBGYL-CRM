# CRM UI Design System Redesign

Date: 2026-06-24

## Objective

Rebuild the CRM frontend UI around a first-class enterprise design system, not page-by-page decoration. The result should feel like a serious business platform for sales, sales managers, operations, partners, finance, project managers, and executives.

The redesign must improve credibility, information density, accessibility, responsive behavior, and long-term maintainability while preserving the approved brand direction based on the Gezhouba Group Supply Chain Management logo.

## Current Problems

The current UI has a good visual direction but is still a high-fidelity shell rather than a mature enterprise CRM interface.

Key issues:

- The login page uses two `h1` elements, weakening semantic hierarchy.
- Icon-only rail navigation relies on visual cues and `title`, without complete accessible labels.
- The opportunity stage bars mix probability and visual length in a way that can mislead users.
- KPI cards show values without target, actual, gap, trend, owner, or update time.
- The workspace and right context panel duplicate opportunity stage and risk information.
- Mobile layout stacks desktop sections instead of applying mobile-specific information priority.
- Many business modules are placeholders, so navigation looks richer than the actual product surface.
- Admin pages still read as default Element Plus screens rather than part of the same CRM product.
- Component states are incomplete: hover, pressed, disabled, loading, focus, empty, and error states are not systematized.

## Design Direction

Use a disciplined Swiss enterprise interface anchored in the company logo:

- Surface: clean white and neutral gray backgrounds.
- Primary color: corporate blue derived from the logo.
- Risk color: controlled red derived from the logo.
- Structure: visible grid, 1px hairline rules, clear left alignment.
- Typography: professional sans-serif stack with tabular numbers for business metrics.
- Visual tone: restrained, sharp, credible, and operational.

The product should not look like a generic admin template. Its differentiator is an "operating cockpit" layout: a precise module rail, a measured business overview, and a contextual right panel for current work.

## Design System Architecture

Create a design system layer before continuing page-level work.

### Tokens

Define tokens in `frontend/src/styles.css` initially. If the system grows, extract them into a dedicated token file later.

Required token groups:

- Color: primary, primary-strong, primary-soft, danger, warning, success, info, ink, text, muted, border, page, surface.
- Typography: display, title, body, label, caption, numeric.
- Spacing: 4, 8, 12, 16, 24, 32, 40, 48, 64.
- Radius: 6, 8, 10, 12, 16.
- Shadow: none by default; one restrained elevation token for floating panels only.
- Motion: 150ms, 200ms, 250ms with ease-out; no decorative motion.
- Focus: one global visible focus ring.
- Z-index: rail, sticky panel, modal, toast.

### Base Components

Build reusable CSS/component patterns before deeper pages:

- `AppShell`: rail navigation, module navigation, main region, context panel.
- `PageHeader`: title, description, metadata, primary action.
- `MetricCard`: target, actual, completion rate, gap, trend, period.
- `StageFunnel`: stage name, count, weighted amount, probability, conversion meaning.
- `RiskNotice`: risk level, reason, owner, due date, action.
- `ModuleListPage`: filters, table/list, status chips, actions, empty state.
- `AdminPanel`: consistent admin header, filter card, table, form drawer/dialog.
- `EmptyState`: clear message and next action.
- `SkeletonState`: loading state for dashboards and tables.

## Workspace Redesign

The workspace becomes a real operating cockpit.

Main content:

- Header: "经营工作台", current period, role scope, last update time.
- Period selector: year, quarter, month.
- Scope selector: team and personal.
- KPI cards:
  - Annual contract target: target amount, completed amount, completion rate, gap.
  - Quarterly collection target: target amount, collected amount, completion rate, overdue risk.
  - Weighted pipeline reserve: weighted amount, coverage ratio, key stage contribution.
  - Risk workload: overdue follow-ups, collection risks, stalled opportunities.
- Opportunity stage funnel:
  - Initial contact: 0% probability.
  - Solution exchange: 40% probability.
  - Project approval / budget: 50% probability.
  - Tender / quotation: 60% probability.
  - Business negotiation: 85% probability.
  - Each stage must show probability as text and show amount/count separately.
- Risk and action section:
  - Stalled opportunities.
  - Collection deadline risk.
  - Missing customer contacts.
  - Next recommended action.

Right context panel:

- Global search entry.
- Today tasks.
- Upcoming risks.
- User profile and logout.

Do not repeat the full opportunity stage visualization in the right panel.

## Business Module Pages

Replace generic placeholders with credible first-pass module pages. These do not need full backend integration immediately, but they must look and behave like real product surfaces.

Modules:

- Reports: period filters, KPI summary, report cards, export affordance.
- Leads: filter bar, source/status chips, lead list/table, empty state.
- Accounts: customer list, contact completeness indicator, owner, last activity.
- Opportunities: pipeline table, stage, probability, weighted amount, next action.
- Quotes: quote status, amount, expiry date, related opportunity.
- Contracts: contract amount, collection progress, invoice/payment risk.
- Activities: task owner, due date, related customer/opportunity, status.

Each placeholder replacement must include:

- Page title and short description.
- At least one meaningful filter group.
- Structured list/table shell.
- Empty/loading state pattern.
- Clear primary action.

## Admin Pages

Unify `UserView.vue` and `OrganizationView.vue` with the design system.

Requirements:

- Use the same page header, card, table, filter, and form treatment as business pages.
- Avoid raw form stacking where a drawer/dialog is more appropriate.
- Convert role code entry into a select/tag pattern where possible.
- Keep dangerous operations visually separated and confirmed.
- Preserve existing backend APIs and permissions.

## Accessibility Requirements

The redesign must meet the following baseline:

- One `h1` per page.
- Sequential heading hierarchy.
- Skip link to main content.
- Icon-only navigation has `aria-label`.
- Active navigation state is visually and semantically clear.
- All interactive elements have visible `:focus-visible` states.
- Form labels are visible.
- Error messages appear near the related field when possible.
- Color is not the only way to communicate status.
- Meaningful images have alt text.
- Logo images reserve layout space with width/height or aspect ratio.
- Keyboard navigation reaches all controls in visual order.

## Responsive Requirements

Breakpoints:

- 390px: small mobile.
- 768px: tablet.
- 1024px: small desktop.
- 1440px: standard desktop.

Behavior:

- No horizontal page overflow.
- Mobile layout prioritizes summary, actions, and current tasks.
- Rail navigation becomes a compact top navigation or scrollable module strip.
- Context panel becomes a collapsible section below the main content.
- Large display typography scales down earlier on mobile.
- Tables either become card lists or support controlled horizontal scrolling inside the table region only.

## Interaction Requirements

All reusable interactive elements must define:

- Default state.
- Hover state.
- Pressed state.
- Focus-visible state.
- Disabled state.
- Loading state when applicable.
- Empty state when data is unavailable.
- Error state when loading fails.

Motion must be restrained and useful:

- 150-250ms transitions.
- Transform and opacity only where possible.
- Respect `prefers-reduced-motion`.
- No decorative animation that distracts from business data.

## Implementation Boundaries

This redesign focuses on frontend UI and interaction structure.

In scope:

- Vue components.
- Router placeholder replacement.
- CSS design tokens.
- Responsive layout.
- Frontend tests for layout/content/accessibility expectations.
- Existing API usage in admin pages.

Out of scope for this phase:

- New backend CRM domain APIs.
- Database schema changes.
- Real report calculations.
- Authentication flow redesign beyond visual and accessibility fixes.

## Testing and Verification

Required checks before completion:

- `pnpm test:run`
- `pnpm build`
- Playwright login/workspace smoke test.
- Visual inspection at 390px and 1440px.
- Verify login page has one `h1`.
- Verify rail icon links have accessible names.
- Verify workspace no longer duplicates stage/risk content in main and right panel.
- Verify business module routes render credible module pages instead of generic placeholders.

## Acceptance Criteria

The redesign is complete when:

- The CRM has a coherent design system instead of scattered page styling.
- The workspace reads as an enterprise operating cockpit.
- Navigation is accessible and clear.
- KPI and stage information cannot be misread.
- Mobile layout is intentionally prioritized, not merely stacked.
- All module routes have credible first-pass pages.
- Admin pages visually belong to the same product.
- Automated tests and build pass.

