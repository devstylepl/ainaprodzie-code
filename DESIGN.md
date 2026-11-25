---
name: Retrofit
description: A support desk drawn in the course's own paper and ink, where every ticket's state, owner and clock are in plain sight.
colors:
  paper: "#f0eade"
  sheet: "#fbf8f1"
  card: "#f6f2e9"
  tone: "#e8e0cf"
  ink: "#1d1d1b"
  ink-2: "#57544d"
  hairline: "#cdc5b4"
  press-grey: "#9b927f"
  trouble-red: "#c4201b"
  trouble-red-fill: "#d9281f"
  trouble-red-wash: "#f7e4df"
  white: "#ffffff"
  amber-lamp: "#e0a100"
  amber-ink: "#7a5800"
  amber-wash: "#f7edcf"
  amber-edge: "#a87a00"
  green-lamp: "#2f9b57"
typography:
  display:
    fontFamily: "'Jersey 25', 'VT323', 'Courier New', monospace"
    fontSize: "48px"
    fontWeight: 400
    lineHeight: 0.95
    letterSpacing: "0.01em"
  display-count:
    fontFamily: "'Jersey 25', 'VT323', 'Courier New', monospace"
    fontSize: "44px"
    fontWeight: 400
    lineHeight: 0.9
  headline-dialog:
    fontFamily: "'Jersey 25', 'VT323', 'Courier New', monospace"
    fontSize: "30px"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "0.01em"
  title-panel:
    fontFamily: "'Jersey 25', 'VT323', 'Courier New', monospace"
    fontSize: "26px"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "0.01em"
  body:
    fontFamily: "'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.5
  body-reading:
    fontFamily: "'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.6
  body-compact:
    fontFamily: "'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
  caption:
    fontFamily: "'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "'IBM Plex Mono', ui-monospace, Menlo, Consolas, monospace"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: "16px"
    letterSpacing: "0.06em"
  label-button:
    fontFamily: "'IBM Plex Mono', ui-monospace, Menlo, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 600
    letterSpacing: "0.04em"
  label-window:
    fontFamily: "'IBM Plex Mono', ui-monospace, Menlo, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 600
    letterSpacing: "0.02em"
  reference:
    fontFamily: "'IBM Plex Mono', ui-monospace, Menlo, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 600
    letterSpacing: "0.02em"
  mono-meta:
    fontFamily: "'IBM Plex Mono', ui-monospace, Menlo, Consolas, monospace"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "18px"
rounded:
  stamp: "3px"
  control: "8px"
  panel: "12px"
  pill: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  2xl: "24px"
  3xl: "28px"
  4xl: "36px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.sheet}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    padding: "0 18px"
    height: "40px"
  button-outline:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    padding: "0 18px"
    height: "40px"
  button-ghost:
    textColor: "{colors.ink}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    padding: "0 18px"
    height: "40px"
  button-xs:
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 12px"
    height: "32px"
  input:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    height: "40px"
  field-label:
    textColor: "{colors.ink-2}"
    typography: "{typography.label}"
  stamp-neutral:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.stamp}"
    padding: "0 6px"
  stamp-ink:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.sheet}"
    typography: "{typography.label}"
    rounded: "{rounded.stamp}"
    padding: "0 6px"
  stamp-danger:
    backgroundColor: "{colors.trouble-red-fill}"
    textColor: "{colors.white}"
    typography: "{typography.label}"
    rounded: "{rounded.stamp}"
    padding: "0 6px"
  lamp-red:
    backgroundColor: "{colors.trouble-red-fill}"
    size: "9px"
  lamp-amber:
    backgroundColor: "{colors.amber-lamp}"
    size: "9px"
  lamp-green:
    backgroundColor: "{colors.green-lamp}"
    size: "9px"
  panel:
    backgroundColor: "{colors.sheet}"
    rounded: "{rounded.panel}"
    padding: "18px 20px"
  window-bar:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    typography: "{typography.label-window}"
    padding: "9px 20px"
  desk-cell:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink-2}"
    typography: "{typography.display-count}"
    padding: "14px 18px"
    height: "76px"
  app-header:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    padding: "10px 24px"
  problem-text:
    backgroundColor: "{colors.trouble-red-wash}"
    textColor: "{colors.trouble-red}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
  comment-support:
    backgroundColor: "{colors.sheet}"
    rounded: "{rounded.control}"
    padding: "12px 16px"
  comment-customer:
    backgroundColor: "{colors.card}"
    rounded: "{rounded.control}"
    padding: "12px 16px"
  comment-internal:
    backgroundColor: "{colors.amber-wash}"
    textColor: "{colors.amber-ink}"
    rounded: "{rounded.control}"
    padding: "12px 16px"
  segment-pressed:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.sheet}"
    typography: "{typography.label}"
  notice-inline:
    backgroundColor: "{colors.tone}"
    textColor: "{colors.ink}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
  notice-floating:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.panel}"
    padding: "12px 16px"
  chat-panel:
    backgroundColor: "{colors.sheet}"
    width: "400px"
  chat-bar:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    typography: "{typography.title-panel}"
    padding: "8px 8px 8px 16px"
  chat-question:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  chat-answer:
    textColor: "{colors.ink}"
    typography: "{typography.body-compact}"
  chat-send:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.sheet}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    width: "96px"
    height: "44px"
  suggestion:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.control}"
    padding: "10px 14px"
---

# Design System: Retrofit

## Overview

**Creative North Star: "Paper and Ink"**

Retrofit belongs to the course that teaches with it: the "AI na Prodzie" masterclass page (https://ainaprodzie.netlify.app/). The desk is drawn as that page is: warm paper grounds, sheets and cards laid on them, ink lines and ink text, a pixel display face for headings and big numbers, and the course's log card (a window bar with three square lamps) framing what the page is about. It refuses the grey-and-one-blue default of support tools: the action colour is ink itself, so the page's decisive marks are the same marks its lines are drawn with.

The desk stays a working desk. Every ticket's state, owner and clock sit in plain sight, and colour is spent only where something needs a person: red for trouble, amber and green lamps for who owes a first response, amber dashed paper for what only support staff read. Controls in a row share one height and one label row, tables hold fixed column widths, and a change of state (a filter, a loading period, a disabled button) never moves a control the reader did not touch. The system is light-only (`colorScheme: 'light'`); every colour is a semantic token in `frontend/src/theme.ts`, and the three faces are self-hosted through `@fontsource` packages imported there.

Craft shows in states and in the press. Hover, keyboard focus, pressed, pending and invalid are always drawn, the same way everywhere; a button sits on a hard 3px shadow and slides into it when pressed, and the one card a page leads with is lifted on a 4px ink offset.

**Key Characteristics:**
- Paper ground, sheet panels, card bars; 1.5px ink lines on everything a reader acts on or reads as a unit, 1px hairlines and dashed hairlines inside.
- Ink is the action colour; red only for trouble; amber and green only as lamps and the internal note's paper.
- Three voices: Jersey 25 pixel headings and counts, IBM Plex Mono uppercase labels and stamps, IBM Plex Sans for everything people read.
- Square lamps beside words say who owes a first response.
- Hard offset shadows: 3px under buttons, 4px under the one raised card, dialogs, menus and floating notices.
- Fixed geometry: one 40px control height, fixed table layout, nothing shifts on state change.

## Colors

Warm paper and black-brown ink, with red, amber and green kept for signals that always come with words.

### Primary
- **Ink** (`fg`, also `accent.solid`, `accent.fg`, `accent.focusRing`, `border.control`): text, headings, every control's edge, every panel's line, the fill of primary buttons, the pressed half of a segmented toggle, the Critical stamp and role stamps, reference links, the keyboard focus ring and the selection highlight. 14:1 on Paper, 16:1 on a Sheet, so as an edge it is far past non-text contrast; Sheet text on an ink fill (`accent.contrast`) measures the same 16:1.
- **Press Grey** (`accent.shadow`): the hard shadow under a filled ink button only, a warm grey that still reads on Paper where an ink shadow would merge with the ink fill; decorative.

### Secondary
- **Trouble Red** (`red.fg`) with **Trouble Red Fill** (`red.solid`) and **Trouble Red Wash** (`red.subtle`): a live SLA breach (the filled SLA stamp, the breach clock in semibold red, the desk summary's breach count and the red lamp), field error text and invalid control edges, the problem banner. Red text measures 5.5:1 on a Sheet, 4.9:1 on Paper and 4.8:1 on its wash; white on the fill 4.9:1.

### Tertiary
- **Amber Lamp** (`amber.solid`): the lamp for a clock running out (due within the hour) or a customer's comment waiting for support.
- **Amber Wash** (`amber.subtle`) with **Amber Edge** (`amber.border`) and **Amber Ink** (`amber.fg`): internal notes and the composer in note mode, on a dashed amber edge (3.3:1 against the wash), the note's author line in Amber Ink (5.6:1 on the wash).
- **Green Lamp** (`green.solid`): the lamp for a ticket whose first response still has time left; the third square of the brand mark.

### Neutral
- **Paper** (`bg`): the page behind everything, and the header bar.
- **Sheet** (`bg.panel`): panels, the queue window's body, table rows, inputs, dialogs, menus, support staff's comments, unpressed toggle halves.
- **Card** (`bg.subtle`): window bars, the queue's filter area and pager footer, a customer's comments, hover on rows, outline buttons and links.
- **Tone** (`bg.muted`): inline notices (session ended, removed link with Undo, the closed-ticket notice, no first response yet, the new-ticket SLA promise).
- **Ink 2** (`fg.muted`): secondary text: table headers, field labels, definition terms, timestamps, hints, placeholders (overriding Chakra's 80% default, which measures 3.8:1), unpressed toggle halves, settled clocks, routine priorities, zero counts, the service line. 6.3:1 on Paper, 7.1:1 on a Sheet, 6.8:1 on a Card, 5.8:1 on Tone.
- **Hairline** (`border`): dividers that only group, never a control's edge: dashed row rules in the table and facts list, the composer's dashed top rule, the desk summary's cell dividers, a customer's comment edge, an unlit lamp's outline.

### Named Rules
**The Ink Acts Rule.** Ink fills the action and draws the focus. A filled ink button is the form's or panel's one action, a reference link is ink, the focus ring is a 2px ink ring. No other hue ever marks "act here"; a second action takes the outline face.

**The Red Means Trouble Rule.** Red marks a live SLA breach (one a person can still act on) or a failure, and never an action: no button is red. Closing a ticket is irreversible but is the ordinary end of a resolved ticket, so it is an ink action, and the Ink sentence in its dialog carries the weight. The SLA stamp and a live breach's clock are red; a breach already answered or closed reads in Ink 2 words. An internal note is not trouble: it is set apart by amber paper and a dashed edge, never by red.

**The Lamps Say Who Owes Rule.** A square lamp says who owes a first response: red past the response deadline, amber due within the hour or a customer's comment waiting, green time left, off (an empty hairline square) once the clock is settled. A lamp always stands beside the words that say the same thing and is hidden from assistive technology; it never stands alone, and lamps are support staff's: a customer's clock carries none.

## Typography

**Display Font:** Jersey 25 (with VT323, Courier New, monospace), one weight; `font-synthesis: none` on `html`, so it is never faked bold.
**Body Font:** IBM Plex Sans (with Helvetica Neue, Arial, sans-serif), 400 to 700.
**Label/Mono Font:** IBM Plex Mono (with ui-monospace, Menlo, Consolas, monospace), 400 and 600.

**Character:** the course's three voices: a pixel display face that says "this is the log", a technical mono that labels and stamps, and a calm humanist sans for whatever a person wrote or reads.

### Hierarchy
- **Display** (Jersey 400, 48px from `md`, 36px on phones, 0.95, uppercase, balanced, at most 26ch): the page's one `h1` in `PageHeader`: "Tickets", or a ticket's title of up to 40 characters. The login card's "Retrofit" is the same 48px.
- **Display, medium and long** (Jersey 400, 0.95, uppercase, balanced): the same `h1` for a title of 41 to 90 characters (36px from `md`, 30px on phones, at most 44ch) and of 91 to 200 (26px, at most 56ch), sizes the scale already has. See The Long Title Rule.
- **Reference line** (Plex Mono 600, 13px, 1.5, 0.02em, Ink 2, no case change): a ticket's reference on a line of its own above its title, inside the `h1`; a visually hidden "·" after it keeps the heading's name "RET-1001 · title". A headline empty state's h1 is 32px; the not-found mark is a 96px Jersey "404" in Ink.
- **Display count** (Jersey 400, 44px, 36px on phones, 0.9, tabular): the desk summary's four counts.
- **Headline, dialog and wordmark** (Jersey 400, 30px, 1, uppercase): dialog titles and the header's wordmark (26px on phones).
- **Title, panel** (Jersey 400, 26px, 1, uppercase): each panel's `h2` in `PanelHead`.
- **Body** (Plex Sans 400, 15px, 1.5): comment bodies, control text, facts. Set on `body` in `theme.ts`.
- **Body reading** (Plex Sans 400, 16px, 1.6, at most 68ch): the ticket's description.
- **Body compact** (Plex Sans 400, 14px): tables, history, notices, the problem banner, the header's user name.
- **Caption** (Plex Sans 400, 12px): field error lines, state notes ("Changed"), hints, the signed-in email.
- **Label** (Plex Mono 600, 11px, 16px line, 0.06em, uppercase): field labels, definition terms, table headers (0.07em), stamps, comment author lines, the description's "Description · author" line, the composer's shortcut hint, the window bar's trailing fact (0.08em). The floor: nothing is set smaller.
- **Label, button** (Plex Mono 600, uppercase, 0.04em): every button, from the recipe: 13px at `md`, 12px at `sm`, 11px at `xs`; segmented halves 12px (11px on phones).
- **Label, window** (Plex Mono 600, 13px, 0.02em, sentence case): the name in a window bar.
- **Reference** (Plex Mono 600, 13px, 0.02em, 1.5px ink underline 4px below): a reference number as a link, in the queue and among linked tickets.
- **Mono meta** (Plex Mono 400, 12px): times in the history, the pager's range (600), the service line under every page.

### Named Rules
**The Three Voices Rule.** Jersey 25 is for page, panel and dialog headings and the desk counts only; Plex Mono uppercase is for labels, stamps, buttons, reference numbers, table headers and times in history; Plex Sans is for everything people read. A sentence is never set in Jersey or in mono capitals.

**The Long Title Rule.** A ticket's title is a name set as the page's heading, so it takes the display voice (the one text people wrote that is set in Jersey), but a title may run to 200 characters and a sentence-length name must not push the page below the first screen. The `h1` therefore leads with the reference on its own mono line and sets the title by its length, never clamped or cut, so its full text stays the heading's name: up to 40 characters in Display, up to 90 in 36px (30px on phones), longer in 26px on a wider measure. The steps count the title alone, not the reference, on every page that uses `PageHeader`; the other pages' titles are short and stay in Display. At 1440×900 and 1280×800 the description starts in the first screen for any title up to 200 characters, and on a 390px phone it starts within the first screen too.

**Known drift.** Font sizes outside the button recipe are literal pixel values at call sites, and the mono label (11px, 600, 0.06em, uppercase) is restated in each primitive that uses it (`FieldLabel`, `Chip`, the table header, definition terms, comment author lines, the chat answer's table header and the question's "on RET-1042" link) rather than one text style in `theme.ts`.

## Layout

The signed-in shell fills the viewport height (`100dvh`): a full-width header on Paper with a 1.5px ink rule beneath it (10px vertical, 16px horizontal on phones and 24px from `sm` up) over a row of the main column and the chat panel. The main column scrolls on its own, so the header and the open chat keep their place while the page moves; it is positioned, so content placed absolutely inside it scrolls with it rather than stretching the page. Inside it sits a centred content column (max 1200px, wide enough for the queue's columns at 1440; 16px side gutters and 20px vertical padding on phones, 24px and 28px from `sm` up). From `lg` (1024px) an open chat panel takes a fixed 400px on the trailing edge and the content column re-centres in what is left; below `lg` the panel covers the whole area under the header. The header holds the wordmark (three lamps, red, amber, green, as the brand mark, then "Retrofit" in Jersey, flexing to fill and never cut), for support staff an outline Settings link (`sm`, `xs` on a phone, latched like the Chat toggle on `/settings`), the Chat toggle, the EN/PL switch, the user's name with ink role stamps, email and affiliation, and an outline Sign out (`sm`). The bar wraps: where its controls do not fit beside the whole wordmark they move to a row under it (on a phone, and on a tablet where the signed-in block is wide: support staff, with the Settings link, get two rows between 640px and about 870px), rather than cutting the name. On a phone the buttons drop to `xs` and the wordmark to the name alone. On a phone who is signed in drops to its own line under the bar (name and role stamps on the leading edge, affiliation on the trailing edge, no email). The wordmark links home. Under every page sits one quiet 12px mono Ink 2 line naming the service and API version (`ServiceFooter`), 20px below the last panel; the home page holds the page header, support staff's desk summary and the queue window.

Pages stack panels with 20px between them; a panel's padding is 16px horizontally on phones and 20px from `sm`. The ticket page opens with an action row above the h1 (24px below it): All tickets on the leading edge, and on the trailing edge Assign to me then Close ticket. The h1 under it leads with the ticket's reference on its own mono line, then the title (see The Long Title Rule). It then splits into a main column and a 300px details column once the page itself is 720px wide, so the main column keeps about 400px, and becomes one column below that. Like the ticket table's switch, it is a container query on the page's width, because the docked chat narrows the page without changing the window: at 1024px with the chat open the ticket reads in one column. Its source order is the reading order on a phone and for the keyboard: the description, a resolved or closed ticket's resolution and the conversation with its composer, then the details (an `aside` landmark named by its "Details" heading, holding support's editor), then linked tickets and history: with the details column beside it the details sit in that column beside the conversation, under the header's refusal alert when there is one, and the links and history continue the main column without a gap. Facts render as a definition list in the course's waybill style: the term column takes the longest term's width up to 140px (so "Response deadline" and "Pierwsza odpowiedź" hold one line and a date beside them does too), 14px column gap, each term and value on 8px of padding over a dashed Hairline, the last row open. Support staff's facts include the response deadline and the waiting time; a customer's leave the wait to the status line, in their words.

Support staff's desk summary leads the list, above the queue window, since it counts the whole desk rather than what the filters narrow the table to (see Desk Summary). The queue is a window (see Queue Window) whose card-filled top holds the filter bar. The filter bar has three rows. A full-width search row whose Search button aligns to the input's bottom edge; a row with the views (a segmented toggle on the leading edge) and, on the trailing edge, the order select with its visible "Sort" label and a Filters button of fixed 128px minimum width that counts the active filters ("Filters (2)"); on a phone too narrow for the label and the longest order beside Filters the order takes the row and Filters moves under it, and where even the label and the select cannot share a line (320px) the label sits above the select; and, folded until asked for or until the URL brings a filter, an auto-fill grid of 180px-minimum cells with a 12px gap under a Hairline, ending in Clear filters. In the open grid a field that narrows the list reads its value in semibold Ink, one left at its "Any …" choice in Ink 2, like a placeholder (`data-set` marks the set ones). Folded, the active filters stay in sight as a wrapping row of removable chips under the views row (outline `xs` buttons in mono capitals with an Ink 2 cross, 8px apart), followed once there are several by a ghost Clear all, unless a pressed view already names them; removing one moves focus to the chip that took its place, or to Filters when none is left, as Clear all does. Support staff's grid ends with Needs attention, the desk summary's counts as a filter. A customer's grid has no organization, team, assignee, SLA or attention field, and the list drops those parameters from a customer's URL. The ticket table uses `table-layout: fixed` with explicit widths for every column whose content varies by state (reference 120px, organization 130px, assignee 120px, status 190px, wide enough for the 159px "Waiting for customer" stamp, or 160px in a customer's table, where the 124px "Needs your answer" takes its place, priority 110px, response 220px, wide enough for the SLA stamp beside a Polish clock on one line, or 300px in a customer's table, where the clock is a sentence); title takes the rest and truncates. Where the table is narrower than 1080px the organization column folds away and its name sits under the title in 13px Ink 2, so the title keeps its room; narrower than 950px, where even that no longer fits, each row stacks into a grid instead: two lines down to 640px (half a desktop screen), four below that (see Ticket Table). Both switches are container queries on the table's own width rather than breakpoints, because the docked chat narrows the main column without changing the window. The pager sits in the window's footer on the trailing edge, its mono range text in a 110px minimum slot; with every ticket on one page it shows the range alone, without Previous and Next.

The login screen is a single 380px Sheet card, centred across and hung from a fixed offset (24% of the viewport height, at least 24px) rather than centred down, so the lines an empty submit adds under the fields grow downward and the lamps and heading above them stay put. It has 28px padding, an ink line and the raised shadow, and is headed by the three lamps and a 48px Jersey "Retrofit".

Spacing runs on 4px steps (4, 8, 12, 16, 20, 24, 28, 36); half-steps (6, 10, 14, 18) appear only inside components as control and card padding.

### Named Rules
**The Nothing Moves Rule.** A change of state never moves a control the reader did not touch: every state of a control draws the same geometry, state-dependent table columns have fixed widths, and clusters whose width varies reserve a fixed slot (`.maister/docs/standards/frontend/css.md`), and `html` keeps a stable scrollbar gutter, so a page too short to scroll sits where a long one does. A page-level notice (offline) never pushes the page down: it sticks to the viewport's bottom edge and sits after the page in the flow, so the end of the page scrolls clear of it instead of hiding under it. A button's press slides the button itself into its shadow and nothing around it. A page that stands alone (the login screen) anchors its card to the top instead of centring it, since a centred card moves everything above an error when the error adds a line; an error line unfolds under its field, so only what follows it moves.

**The One Row Height Rule.** Every control in a form row is 40px tall with the same label row above it: inputs, native selects, the multi-select and the combobox share one `CONTROL_STYLE`.

**The Touch Floor Rule.** On a coarse pointer every button, input, select, combobox and option is at least 44px tall (buttons also 44px wide) and control text is 16px. The height floor is set once in `globalCss` in `theme.ts`, for links drawn as buttons too (`Button asChild`, matched by the recipe's `chakra-button` class) but never for a text link; the text size lives in `CONTROL_STYLE`, because a recipe or a call site's `fontSize` outranks the global layer. A form row keeps one height on touch as it does on desktop. A control whose own `min-height` or `min-width` outranks the global rule (the multi-select's trigger, the combobox input, the segmented toggle's halves, a wrapping button with its own `minH` such as the comment composer's send button, every button size in the recipe, and every close button, whose size recipe sets its width) restates the floor on the control (`TOUCH_FLOOR`).

## Elevation & Depth

Depth is printed, not lit: hard ink offsets with no blur, as on the course page. Plain panels, table rows and the queue window lie flat on Paper, separated by their 1.5px ink line and the step from Paper to Sheet; inside them depth is tonal (a Card window bar over a Sheet body, a Card customer comment beside a Sheet support comment, amber paper for a note). The chat panel is docked, not floating: beside the page it is divided off by a 1.5px ink line on its leading edge, and where it covers the page below `lg` it fills the whole area, so it takes no shadow either.

### Shadow Vocabulary
- **Raised** (`shadows.raised`: `box-shadow: 4px 4px 0 #1d1d1b`): the one card a page leads with (the ticket's description window), the login card, dialogs, the multi-select and combobox menus, the offline notice, and the skip link while it is focused.
- **Button** (`box-shadow: 3px 3px 0` in the palette's shadow colour): every solid and outline button; ink under an outline button, Press Grey under the ink fill. Pressed, the button moves 3px right and down and the shadow goes (80ms ease-out, no transition under reduced motion), so it lands where its shadow was. Ghost buttons have neither shadow nor press.

### Named Rules
**The Hard Shadows Lift Rule.** A shadow is a hard ink offset and it means "lifted off the paper": 4px for the raised card, dialogs, menus, the offline notice and the skip link; 3px under a button, which the press slides into. Plain panels, rows and the queue window never take one, and no shadow is ever blurred.

## Shapes

Three radii and a pill: stamps at 3px (`radii.stamp`), controls (inputs, comments, inline alerts and notices, segmented toggles, menus, the skip link) at 8px (`radii.control`), panels, windows, the desk summary and dialogs at 12px (`radii.panel`), and every button a full pill. Lines that frame a control, panel, window, comment or stamp are 1.5px (`borderWidths.ink`); hairline dividers are 1px, dashed inside lists (table rows, facts, the composer's top rule) and solid between desk summary cells (1.5px Hairline). Lamps are squares with no radius. Segmented toggles clip their halves inside one rounded outline drawn over them, so the halves themselves are square.

## Components

Paper forms with an ink line, whose craft shows in states and in the press.

### Buttons
- **Shape and type:** a pill on a 1.5px line, mono 600 uppercase at 0.04em, all from the `button` recipe in `theme.ts`: call sites set only `variant`, `size` and `colorPalette`. Sizes: `md` 40px (18px padding, 13px), `sm` 36px (14px, 12px; Sign out, the pager, Reply), `xs` 32px (12px, 11px; Edit, Link a ticket, Undo, Try again, Answer instead, filter chips).
- **Primary:** Ink fill and edge, Sheet label, Press Grey shadow (`colorPalette="accent"`): the submit of a form (sign in, search, send, add note, create ticket), a panel's single primary action (New ticket, Reload), and the confirm in the close dialog, below its "Closing is final. After that, only support staff can add comments to the ticket." in Ink, not Ink 2: that sentence, not a colour, carries the one consequence that cannot be undone. An editor's Save changes (the details editor, title and description) fills with ink only once there is a change to save (`ReadyButton`): until then it draws the outline face, disabled, so a Save with nothing to save never reads as the page's action; both faces have the same line and shadow, so the switch moves nothing.
- **Outline:** Sheet fill, ink edge, label and shadow: navigation and secondary actions (All tickets, Assign to me, Close ticket, Sign out, Back home, Reload ticket after a conflict, the pager, the customer's Reply in the status line (`sm`), Link a ticket and Undo (`xs`), Link, Answer instead (`xs`) in the close dialog).
- **Suggestion (`SuggestionButton`):** a question or query sent with one tap is a sentence, so it keeps the outline face (Sheet, ink edge, 3px shadow and the press) but reads in Plex Sans 14px, start-aligned and wrapping in an 8px-radius box, full width, at least 44px tall with 10px × 14px padding: the chat's example questions.
- **Ghost:** no edge and no shadow, ink mono label, Card on hover: Discard in the details editor, Cancel in the description editor and the link form, Clear filters, row-level Remove, Clear all.
- **States:** hover takes the fill to 88% (solid) or Card (outline, ghost); focus-visible draws a 2px ink ring; a pending action disables the button and keeps its label and width.

### Stamps (`Chip`)
- **Style:** mono 600 11px capitals at 0.06em on a 16px line, a 1.5px line, 6px horizontal padding, 3px radius, never wrapping; 19px tall in all, so a stamp fits inside a line of 14px text and a table row with one is as tall as one without. Each tone carries its own fill onto any surface.
- **Wrapping:** a stamp never wraps, except where it must fit a lane narrower than its words (`wrap`, centred): the Data-flow window's stamp under the Phoenix edge.
- **Neutral:** Sheet fill, ink line and text: status, Support staff author, related-ticket status, Internal note.
- **Ink:** filled ink with Sheet text: role stamps in the header, and the Critical priority stamp, so the one priority that cannot wait is marked by ink, not red.
- **Danger:** Trouble Red Fill with white text: the live SLA breach only.
- Status stamps are all neutral: status is read from the word, not a colour.

### Lamps (signature)
A square signal light (9px, 10px in a window bar and on the login card, 8px in the header mark) on a 1.5px edge of its own colour; off is an empty Hairline square. Hidden from assistive technology, always beside words (see The Lamps Say Who Owes Rule). Lamps appear in the queue's Response column before each support clock (the live breach takes the SLA stamp instead) and before "Customer wrote …", in the status line likewise, beside each desk count (lit above zero: red for breaches, amber for due within the hour and for unanswered customer comments, off for unassigned), in window bars, and as the brand mark.

### Cards / Containers
- **Panel:** Sheet, 1.5px ink line, 12px radius, 18px vertical and 16px (phone) or 20px horizontal padding, 20px below. Its head is a 26px Jersey uppercase `h2` with an optional trailing action and an optional second line under the title in the Label style (Ink 2), 14px above the content; a title that wraps is balanced.
- **Window (`WindowPanel`, signature):** the course's log card: a panel with no padding of its own under a Card bar (9px × 16/20px, 1.5px ink rule under it) holding three lamps on the leading edge, the name in mono 13px, and an optional fact on the trailing edge in mono 11px Ink 2 capitals. It lights at most one lamp, the one that says who owes a first response; without one the three stay dark. `raised` gives it the 4px shadow, for the one card a page leads with. A window whose name is a sentence (the Data-flow window's) makes it the section's `h2` and lets it wrap instead of truncating; the other windows' names are references and truncate.
- **Description:** the ticket page's lead card, a raised window named "RET-1036 · Acme Corp" (reference and organization), lighting the ticket's clock lamp for support staff and none for a customer. Inside, a mono label line ("Description · Una User", the h2 and the author, with Edit on its trailing edge), then the description in Body reading.
- **Comment:** 8px radius, 12px × 16px padding, 1.5px line. Support staff's public comments are on a Sheet under an ink edge, the customer's on a Card under a Hairline, internal notes on Amber Wash under a dashed Amber Edge with an "Internal note" stamp. The author line is mono 11px capitals (Amber Ink in a note) carrying stamps and a trailing timestamp in sentence case, not bold: relative while the comment is under a day old ("20 minutes ago", `Intl.RelativeTimeFormat` in the page's language, refreshed once a minute), then the date and time, always a `<time>` with the exact time in `dateTime` and its tooltip. A comment by support staff carries a "Support staff" stamp after the name, with a visually hidden comma before it so a screen reader does not hear the role as part of the name.
- **Comment composer:** under a dashed Hairline 18px below the conversation. For support staff a Public comment / Internal note toggle over the field; every part names the reader: the label ("Public comment, visible to Acme Corp" / "Internal note, visible to support staff only"), the placeholder and the send button ("Send to Acme Corp" / "Add internal note"), whose label wraps onto a second, balanced line on a phone rather than truncating the reader's name ("Wyślij do zespołu wsparcia i Acme Corp"). In note mode the textarea takes the note's Amber Wash and dashed Amber Edge. The draft survives leaving the ticket (session storage per user and ticket); Ctrl+Enter or ⌘ Enter sends, hinted in a mono 11px Ink 2 line beside the button from `md` up.
- **Details editor:** support staff's Details panel is a form with Discard (ghost) and Save changes. The resolution field unfolds directly under Status only while Resolved is picked or the ticket is resolved, growing downward under the select the reader touched; its label says "(optional)" once the ticket already has a resolution, which a blank field keeps. Picking Resolved on a ticket with no first response unfolds, between Status and the resolution, an inline notice (a polite status) saying the customer has had no public comment yet; it advises and never holds Save, as ADR 0004 allows the move. On a resolved ticket a 12px hint under Status says Close ticket at the top of the page closes it (Closed is never offered in the select); the hint keeps its line while the draft moves the status on. With no team chosen, Assignee lists every support staff member under their team (`<optgroup>`), and picking one sets the team too. Each changed field says "Changed" on the trailing edge of its label row (12px Ink 2, read with the field), and the line under Save says "Unsaved changes" in semibold Ink where it otherwise says when the ticket was updated, and "Changes saved" in Ink for five seconds after a save (or until the next change), so neither moves anything. While there is a draft, Discard, Save changes and the line under them stay in view: the row sticks to the window's bottom edge on a Sheet across the panel's width (out to its 16px or 20px edges) and draws a Hairline along its top only while stuck; its padding and that edge's place are there in every state, so sticking moves nothing. Leaving the ticket with a draft asks first in a dialog naming the changed fields, Keep editing (primary, focused) beside Discard and leave (outline). The read-only facts leave out status and priority, which the status line says, and the editor's resolution is not repeated below it.
- **Resolution:** a resolved or closed ticket's answer is a panel of its own between the description and the conversation, so a customer coming back finds it first: headed Resolution, with who wrote it (13px semibold) and when (as a comment's time) on the head's trailing edge, from the latest resolution in the history, and the text as written in Body. A customer reads it there from Resolved on, support staff once the ticket is closed (on a resolved ticket their details editor holds it); the facts list keeps it only for a ticket reopened with one. A ticket withdrawn without a resolution has no panel.
- **New-ticket form:** the customer's one panel (at most 640px): an inline notice with their organization's SLA as a promise in words ("Support staff give Acme Corp a first response within 4 hours") that keeps its lines while the organization loads (an invisible stand-in sentence sizes it, at every width and in both languages), then Title, Description and Category, the last two marked optional, with Cancel (outline) beside Create ticket (primary). Under Description a 12px Ink 2 hint, read with the field, says what helps support: what the customer did, what they expected, what happened instead and in which project. Leaving with anything entered asks first in the details editor's dialog ("Leave without creating the ticket?", naming the fields entered); a created ticket opens without asking.
- **Dialog:** Sheet, 1.5px ink line, 12px radius, the raised shadow over Chakra's backdrop; the title in 30px Jersey, the close button with the touch floor restated.
- **Shadow Strategy:** see Elevation & Depth: only the description window, the login card, dialogs, menus and floating notices are raised.

### Inputs / Fields
- **Style:** Sheet, 1.5px ink edge, 8px radius, 15px Plex Sans text, 40px tall; the textarea grows downward only, from 120px (76px in the comment composer). Label above in mono 11px Ink 2 capitals (`FieldLabel`), 6px gap. Menus of the multi-select and combobox are Sheet with an ink line, 8px radius and the raised shadow.
- **Focus:** an ink ring.
- **State note:** a word about the field's state (`FormField`'s `note`, such as "Changed") sits on the trailing edge of the label row in 12px Ink 2, joins the field's description, and never changes the row's height.
- **Error:** Trouble Red edge and ring on `aria-invalid`, a 12px Trouble Red message under the field, announced with `role="alert"`.
- **Problem banner:** Trouble Red text on Trouble Red Wash inside a 1.5px red line, 8px radius, 10px × 12px padding, for a failure that belongs to the form rather than one field. It names the cause the reader can act on: a refusal in the domain's words, an unreachable server as "cannot be reached, check your connection", anything else as the generic sentence.
- **Try again:** a failed load (the ticket list, a ticket, the service panel, the filter options) adds Try again on the banner's trailing edge: an `xs` outline button on a Sheet, beside the alert rather than inside it so the alert stays plain text. It wraps under the sentence when the line is short, and while the retry runs it is disabled with its label and width kept. In the queue window the banner sits in the window's body with its padding.

### Notices
Information that is not a failure, in Ink on neutral surfaces, never in red.
- **Inline notice:** Tone fill, Ink 14px text, 8px radius, 10px × 12px padding, inside the panel it concerns. The login card's "Your session has ended" line sits here, above the form, until the reader signs in again (the closed-ticket notice under a conversation is its Ink 2 sibling, whose "create a new ticket" is an ink semibold underlined link to the new-ticket form, the customer's one way on).
- **Offline notice:** a floating card, Sheet with a 1.5px ink line, 12px radius, the raised shadow, 12px × 16px padding, 14px: a semibold Ink title ("You are offline.") over an Ink 2 sentence saying what still works. From `sm` up it holds the bottom-left corner (16px in, at most 420px wide); on a phone it spans the width inside 12px gutters. It lives in an always-mounted live region, so going offline and back is announced.

### Navigation
- **Header:** the full-width Paper bar over its ink rule is the only navigation chrome. Page-level movement is by buttons (All tickets, New ticket) and reference links (mono, ink, underlined).
- **Skip link:** the first Tab stop of every signed-in page, hidden until focused: "Skip to main content" as a floating card (Sheet, ink line, 8px radius, the raised shadow, Ink 14px semibold) 24px in from the top-left corner. It moves the focus to `main` (no ring, as the h1 on arrival) without changing the URL, so the next Tab reaches the page's first control past the header.
- **Settings link:** support staff only, an outline `sm` button in the header (`xs` on a phone) before the Chat toggle, to `/settings`. On that page it stays latched as the Chat toggle does when open, with `aria-current="page"`. A customer sees no link, `/settings` is the not-found page for them, and the API answers 403.
- **Chat toggle:** an outline `sm` button in the header (`xs` on a phone), before the language switch. Open, it stays pressed into its shadow, a switch latched on: moved 3px right and down with no shadow, on a Tone fill, and `aria-expanded`; it controls the panel (`aria-controls`), and closing the panel returns focus to it.
- **Arriving on a page:** every page names the tab after its headline ("RET-1036 · Board view blank for all users – Retrofit", "Tickets – Retrofit"; sign in and not found too), and arriving from another page moves the focus to the h1 (`tabIndex={-1}`, no ring; on a not-found page the empty state's title is that h1), unless the page has placed it itself (a field's `autoFocus`). Not on the document's first page, and not when only the query string changes, so filters, paging and sort keep the reader where they are.
- **Pager:** a page change brings the table's top back into view, 12px clear of the edge, and keeps the focus on the pager (on the first or last page, on the button still enabled).
- **Segmented toggle:** the EN/PL language switch, the list's views, the composer's Public comment / Internal note switch and the Settings page's toggles (all but the first through `SegmentedToggle`): a 1.5px ink outline at 8px radius over square halves (drawn over them, so each half is the control's full height), named as a group; halves are mono capitals in Ink 2 on a Sheet, and the pressed half is filled with ink, its label in Sheet, exposing `aria-pressed` (its focus ring turns Sheet, inset). Beside 40px fields it is 40px tall, 44px on touch. On a phone the halves share the full width, a long label wraps inside its half and a count sits under its label, centred (about 80px a half at 320, where "Przypisane do mnie" takes two lines and the count a third); from `sm` the halves sit in one row of their own width, the count after the label. The language switch is a smaller sibling (mono 12px, 10px × 5px halves). Disabled (`disabled`), every half is inoperable by mouse and keyboard and drawn dimmed at the same geometry, the pressed half still pressed, so the stored choice stays readable; the Settings page's grouping switch uses it. `equal` gives the halves of a row the width of the widest, for a pair of words such as On and Off (the Settings page's switches, whose halves are 51px each in English and 97px in Polish, "Włączone" and "Wyłączone" written out).
- **Views:** presets of the filters (support: All tickets, Assigned to me, My team; customer: All tickets, Needs my answer, Raised by me). A view reads as pressed only while the filters are exactly its own; any other combination presses none. Every view shows its count after the label (under it on a phone) in Ink 2, in a slot each half reserves (never broken across lines), so a count arriving or changing moves nothing; the half is named in one string, "All tickets, 50 tickets". Both roles have three views. What needs support staff now (live breaches, deadlines due within the hour, unanswered customer comments, open tickets without an assignee) is not a view but the desk summary's counts, each a link to the list narrowed to it, and the breach count is the one way in to the breaches. The Filters grid's SLA field still narrows to every SLA breach in the glossary's sense, settled ones included, so that list can be longer than the desk's live count. The customer's Needs my answer count is their work to do, so above zero it reads in semibold Ink; support staff's view counts stay muted, the desk summary carrying what needs them. Support staff's counts come from `GET /api/tickets/counts`, a customer's (over their organization) from `GET /api/tickets/customer-counts`. A customer's list still opens on All tickets: the count on Needs my answer says how many wait for them, and an empty URL keeps meaning all tickets, so neither All tickets nor the ticket page's All tickets link bounces elsewhere and the list never swaps under the reader when the counts arrive.

### Settings page
`Panel`s of 640px at most, one per setting, under the page header ("Settings", with a one-line subtitle that covers the model, what it does when the provider fails, whether the model reasons and privacy as well as traces). "Model" comes first, on its own, since it matters most, "Fallback model" follows it, since it says which model answers when that one's provider fails, and "Reasoning" follows that, since it too says how every question is asked; then two groups, each named by a mono label in the Label style (Ink 2). The Model panel holds a `SegmentedToggle` of the LiteLLM aliases in the order the server lists them (halves of their own width, not `equal`), the stored one pressed; a stored alias no longer configured is still offered, after the others. Until the aliases arrive the toggle's height is held, so nothing below moves. Under it one sentence says the alias is what every chat question goes to and that `litellm/config.yaml` maps it to a provider model. It saves as the switches do (below); the live region says "Saved: Model. Chat questions now go to chat-anthropic.", and a failed save names the alias it is still on. The Fallback model panel holds a `SegmentedToggle` of three choices, Off, After a retry and At once (halves of their own width; on a phone they share the full width and "Po ponowieniu" wraps inside its half), and under it the sentence of the pressed one, stacked with the other two as the switches' sentences are; it saves as they do, the live region reads the new sentence, and a failed save names the choice it is still on ("Not saved: \"Fallback model\" is still set to at once."). A stored choice this bundle does not know leaves no half pressed and no sentence shown. The Reasoning panel is built the same way from three choices, Off, Medium and High (chat ADR 0020): halves of their own width that share the full width on a phone, the sentence of the pressed one stacked with the other two, the sentence under High saying that the answer takes longer and costs more tokens, a save announced by the new sentence, a failed save naming the choice it is still on ("Not saved: \"Reasoning\" is still set to medium."), and an unknown stored choice leaving no half pressed. It changes no hop of the Data-flow window. A models list that fails to load shows its own problem with a retry inside the Model panel, under the stored alias alone, and the switches keep working. "Personal data" holds "Mask personal data in the app": the title says the outcome, and a second line under it in the Label style names the mechanism ("Presidio"). "Tracing in Phoenix" holds "App traces to Phoenix", "Group LiteLLM calls per chat message" and "App LLM traces to Phoenix". While app traces are On the grouping switch is disabled with its stored half still pressed, and its sentence says why (app traces already group each question into one trace) in place of the On or Off sentence; with app traces Off it works as before. The app LLM traces switch is the reverse: disabled while app traces are Off, with a sentence saying the app sends no spans then. Each panel holds a `SegmentedToggle` with On and Off (equal halves; they share the full width on a phone), and under it the sentence for the pressed half. Every sentence a switch can show is in the page, stacked in one grid cell with the shown one visible and the others `visibility: hidden` and `aria-hidden`, so the box is as tall as the longest in every language and width and switching moves nothing below (`text-wrap: pretty` keeps a lone word off the last line). While "Mask personal data in the app" is Off its sentence is an inline notice (Tone fill, Ink 14px, 8px radius, 10px × 12px padding, never red: Red Means Trouble) saying in plain words that names reach the model provider and Phoenix as written; On, the same cell shows the plain sentence. Pressing a half saves at once, with no Save button and no optimistic change: the pressed half follows the stored value. Each panel is a `section` named by its title, and its toggle group is described by the sentence it shows (`aria-describedby`), so the grouping switch's reason is read with it. Saving is said in the panel of the switch pressed: its group is `aria-busy`, and a reserved 16px line under the sentence reads "Saving…" in Ink 2, then "Saved" in Ink for five seconds (nothing moves; a press while a save runs is ignored, never disabled, so the focus stays on the half), while one visually hidden `role="status"` for the page announces the saved switch's name and its new sentence once. A failed save keeps the old half pressed and shows the problem inside the panel of the switch that failed, under the sentence, in red (Red Means Trouble) with `role="alert"`, naming the switch and the state it is still in ("Not saved: \"App traces to Phoenix\" is still off.") before the cause; it unfolds downward from the touched control, so only what follows it moves. A settings request that never loaded shows the problem banner with a retry in place of the panels. From a content column of 1020px (a container query on the page's own width, so the docked chat narrows it without changing the window: 1440 and 1280 without the chat) the panels keep their 640px on the leading side and the Data-flow window takes the rest and runs the panels' full height: the top of its card (the bar, the graph and the "In Phoenix" fact) sticks to the top of the scrolling main column until the panels end, and the hop rows scroll beneath it, so the picture and the fact the grouping switch changes stay in view while any switch is pressed (the whole window, 937px, outgrows the main column at 1440×900, and the panels, 1283px, outgrow the window; the top part is about 470px in English and 490px in Polish); narrower (1024 with the chat open, a tablet, a phone) it follows the panels in one column, at the panels' width at most. Source order is reading order: the switches, then what they do.

### Data-flow window (signature)
The Settings page's picture of where the chat's texts go and in what form, for the stored switches (never for a half that is pressed and not yet saved; Operate mode: a readout, not a control). A `WindowPanel` named "Where the chat's data goes" (the section's `h2`, wrapping on a phone), with the trailing fact "As saved" from `sm` up and the three lamps dark: lamps are for first responses (The Lamps Say Who Owes Rule), so no lamp marks masked or raw. Max 640px, one `section`. The `WindowPanel` holds the graph and the "In Phoenix" fact, square at its bottom corners, and the hop list continues the card under its ink edge (Sheet, ink sides, the panel radius at the bottom), so beside the panels the top part can stick while the hops scroll beneath it; a paper shadow there covers its rounded top corners, which the hops' side borders would otherwise show through.

- **Graph:** `aria-hidden`, max 520px and centred, in a grid of five columns (side node, gap, spine, gap, side node) that gives the nodes their width first and the gaps the rest (never under 14px, so it holds at 320px). Nodes are Sheet boxes on a 1.5px ink line, 8px radius, mono 13px semibold names, 36px tall: App, LiteLLM and Model provider down the spine, Presidio on the leading side and Phoenix on the trailing side, each reached by both the App and LiteLLM without a crossing (the App to Phoenix and LiteLLM to Presidio edges run as elbows). App, Presidio, LiteLLM and Phoenix sit inside a dashed Hairline boundary labelled "Runs on this machine" in the Label style; Model provider sits outside it, so the one edge that leaves the machine crosses the line. A "Model provider" or "Dostawca modelu" node wraps between its words where the width runs out, never inside one.
- **Edges:** a sent edge is a 1.5px ink line with a 9px ink arrowhead; a not-sent edge is a dashed Hairline without an arrowhead; while the settings load the app's edges are plain Hairlines. Each edge carries its state as a stamp laid over it, wider than the lane when its words need it: "Names as written" and "As written" are the ink-filled stamp (the heaviest state draws the eye), "Placeholders", "Masked" and "Sent" the neutral outline stamp, "Not sent" Ink 2 mono capitals on a Sheet. Never red (the unsafe state is not trouble: Red Means Trouble), never amber or green. The three stamps in the lane under the App sit at two heights (the middle one higher) so two wide ones never touch at phone widths; the App to Presidio edge is too short to carry a stamp (its gap is 14px at 320px), so its stamp sits in the free row above it, centred on the gap; the stamp under the LiteLLM to Phoenix edge may wrap its words and reserves its widest variant's height.
- **List:** under the graph, the same facts in words, and what a screen reader reads (the graph is hidden from it, so nothing is said twice). First, right under the graph, "In Phoenix" with how it groups (one trace per question with the tool steps, one per question with LiteLLM's calls only, one per model call), not a hop and so outside the list, closing the top part of the card with its ink edge. Then a `ul` in the order App to Presidio, App to LiteLLM, App to Phoenix, LiteLLM to Presidio, LiteLLM to Model provider, LiteLLM to Phoenix. A row is the hop's name in the Label style (Ink 2) with its stamp on the trailing edge, and under them its sentence in Body compact on dashed Hairlines, the last row open; the "In Phoenix" fact takes the same form without a stamp. Each row says what the hop carries for the stored switches: the App to Presidio hop is sent only while the app masks and exports its spans, and carries the texts of those spans, never a question or a tool result; the App to LiteLLM and LiteLLM to Model provider hops carry the people the app knows as placeholders while it masks and names as written otherwise, the guardrail masking e-mail addresses, phone numbers, IBANs and PESELs and a card number refusing the request in both states, and, unless the fallback model is Off, the LiteLLM to Model provider hop says that a request whose provider fails goes to the fallback model's provider too (chat ADR 0019); LiteLLM to Presidio is always sent; LiteLLM to Phoenix carries each model call as the provider received it; the App to Phoenix hop is not sent with app traces off, masked with them on and the app masking, as written with them on and the app not masking.
- **States:** every stamp and every sentence stacks all its variants in one cell (the shown one visible, the others `visibility: hidden` and `aria-hidden`), so a saved switch changes words and never the height. While the settings load the nodes and edges are drawn and the stamps and sentences are empty in their reserved slots. A settings request that never loaded replaces the whole page, the window included, with the problem banner.
- **Room for the knowledge base:** the knowledge base's hops (import and search through Presidio to the embedding model, always masked) are a second node outside the boundary on a branch that has them.

### Desk Summary (signature)
Above support staff's queue, whatever the search or filters, a strip of four cells across all tickets: a Sheet box with a 1.5px ink line and 12px radius, cells divided by 1.5px Hairlines, four across once the strip is 700px wide and two by two below that (a phone, or the page beside the open chat), a container query on the strip's own width. Each cell is at least 76px tall and holds a large Jersey count (44px, 36px on phones, tabular) beside its lamp and its words in 13px: "SLA breaches still unanswered" (the live breaches, the red rows, worded so it reads apart from the SLA filter's Breached, which also holds the settled ones), "due within an hour", "with an unanswered customer comment", "open and unassigned". A count above zero is lit: its lamp shines (red, amber, amber, off for unassigned), the breach count is Trouble Red and the others Ink, and the whole cell is a link (Card on hover, the words underlined) that opens the list narrowed to exactly its tickets (`attention`, the same SQL condition as the count), marked `aria-current` and filled with ink while the list is exactly that; a zero count is plain Ink 2 text with an unlit lamp. A visually hidden "Across all tickets:" names the list for a screen reader. The strip holds its height while the counts load.

### Queue Window (signature)
The home page's list is a window: its bar names the sort order ("Response deadline") and, on the trailing edge, the total ("50 tickets"), and for support staff lights one lamp for the desk, red while a breach waits unanswered, amber while a deadline is within the hour or a customer's comment waits, green otherwise; a customer's queue lights none. Under the bar a Card area (16px top padding, ink rule under it) holds the filter bar, then the table on Sheet, then a Card footer with an ink rule above it holding the pager.

### Ticket Table (signature)
The list's fixed-layout table: a Sheet header row of mono 11px Ink 2 capitals over a 1.5px ink rule, Sheet body rows separated by dashed Hairlines and Card on hover. The reference is a mono ink underlined link, the title truncates on one line, the assignee is Ink (Ink 2 "Unassigned"), status is a neutral stamp, and priority recedes when routine (Low, Normal in Ink 2), reads plain when High and semibold when Critical, and a Critical ticket's title is led by the filled ink Critical stamp (read aloud as "Critical priority"), so the one priority that cannot wait is marked where the eye starts the row, in the stacked layout too, and never in red. A customer's list says "Needs your answer" in semibold Ink in place of the Waiting for customer stamp. The Response column is the clock, read against the response deadline and kept current (the clock ticks every 30 s, the list refetches every minute while visible), each support clock led by its lamp set in the clock's first line so it stays beside the words when they wrap: "Due in 40 min" (green; amber and semibold within the last hour), "Due now", "4 h over" (semibold Trouble Red) for a ticket still awaiting a first response, then the settled ones in Ink 2 with an unlit lamp, "Answered in 2 h" or "Closed unanswered", and when the settled clock breached the SLA, "Answered 6 h late" or "Closed unanswered, late" (still Ink 2, no stamp; a screen reader hears the full sentence, "First response 6 h after the response deadline", instead of the short form). Only the live breach ("over") takes the danger SLA stamp before the clock, in place of a lamp; its visible "SLA" is `aria-hidden` beside a visually hidden "SLA breached". The stamp stays beside the clock, and a clock too long for its column wraps its own words. Durations are written with unit symbols in both languages ("2 d 4 h", "35 min"), and the Polish short forms stay short where the column is narrow ("Czeka na Ciebie" for Needs your answer, "Bez odpowiedzi, po terminie" for a late close, the status stamp in the same row saying Closed), so a row keeps one line at 1024 to 1440 in both languages; only a long settled Polish clock, hours with minutes or days with hours ("Odpowiedź 1 h 30 min po terminie"), still wraps its words. Once the first response has settled the clock, a ticket in progress whose latest public comment is a customer's (an unanswered customer comment, `unansweredCustomerCommentAt`) reads "Customer wrote 20 min ago" in semibold Ink after an amber lamp in its place, by the largest unit so it holds one line ("Klient napisał 6 d temu"): support's next piece of work, never red, as red stays for a live breach. A customer reads the clock in their own words instead, with no stamp, no lamp, no deadline and no weight that follows it: "Waiting for support for 3 hours" in Ink, and past the organization's SLA, calmly and still in Ink, "Waiting for support for 17 days, longer than the 4 hours we aim for" (the time the new-ticket form promised), then in Ink 2 "Answered after 2 hours", "Closed before support answered" and "Resolved before support answered"; a ticket waiting for the customer without a first response shows no clock, as "Needs your answer" already says whose turn it is. Support staff's queue opens on the response deadline order (awaiting a first response first, the earliest deadline first, then the other open tickets, then the closed ones); a customer's list on the latest update. With no rows the table is replaced by a centred empty state that names the search and each active filter that matched nothing, and offers Clear search and Clear filters, each only when it applies.

From 640px to 950px of table width (half a desktop screen, or the list beside the open chat on a wide screen) each row takes two lines in a grid of fixed tracks, so the values line up down the list as columns without the header row: the reference (100px), the title on one line and the clock (start-aligned, as in the table), then under the title, for support staff, the organization, the assignee (120px), the status stamp (160px, as wide as the "Waiting for customer" stamp) and the labelled priority (140px, a semibold "Priorytet Krytyczny" on one line), and for a customer the status under the title and the labelled priority under the clock (300px). Only a customer's clock past the SLA, a sentence, wraps to a second line.

Narrower than 640px (on a phone, a small tablet, or beside the open chat on a laptop) the same table stacks each row into a two-column grid with a 12px vertical rhythm and a dashed Hairline between rows: the reference with the clock on the trailing edge (the trailing column takes at most 60% of the row, where the clock wraps its own words), the title on up to two lines (`text-wrap: pretty`, so the second line is not one stray word), then for support staff the organization with the status stamp and the assignee with the priority (labelled, as the word alone is ambiguous), and for a customer the status stamp with the priority. Leading values truncate; a labelled value wraps between its words, never inside one. In both stacked layouts the header row becomes screen-reader-only, the table roles are explicit so stacking keeps the semantics, and the reference link stretches over the whole row as one tap target.

### Chat Panel (signature)
The read-only chat, docked in the shell (chat ADR 0006). It is drawn as the course's log card, docked: a window bar over a Sheet, from the desk's own tokens and parts; what sets it apart is that it shows its work.

- **Frame:** an `aside` on Sheet, divided from the page by a 1.5px ink line on its leading edge where it docks beside it, in three rows: a window bar (Card, over a 1.5px ink rule, 8px × 16px padding: three dark 10px lamps, as no clock is the chat's to light, the `h2` "Chat" in the panel title's 26px Jersey capitals, a ghost `xs` New conversation, disabled while the conversation is empty, and a small close button), the conversation (scrolling, 14px × 16px padding, 16px between turns, kept at its bottom as an answer grows), and the composer (a Card footer over a 1.5px ink rule, as the queue window's pager sits). It stays mounted while closed, so the conversation survives navigation; `data-state` carries the last turn's state.
- **Question:** a card on the trailing edge, at most 85% wide, Card under a 1.5px ink line, 14px Ink, 8px radius, 8px × 12px padding. It keeps the reader's line breaks and breaks long words anywhere. A question asked with a ticket open carries, 4px under the card on the trailing edge, a link to that ticket in the mono label (11px capitals, Ink 2, underlined: "ON RET-1042"), so an earlier "this ticket" still says which one after the page moves on (chat ADR 0013).
- **Trace (signature):** the work behind the answer, under the question and before the answer, as a timeline of 13px steps, one per tool the model called, in the order the calls arrived. Each step leads with what the chat does in Ink semibold, as a comment's author line leads its comment ("Searching tickets", "Reading ticket RET-1012"), with the arguments the model chose under it in Ink 2 ("status Waiting for customer, organization Acme Corp"), so the work reads from across a room while the 14px answer stays the peak; the steps sit 8px apart and 12px above the answer. Where the provider streams a call while the model writes it, the step appears as soon as the model names the tool, with what the chat does and no arguments yet ("Reading a ticket"), and takes its words in place when the call is complete ("Reading ticket RET-1012"), growing downward. The arguments are put into words, with ticket labels and dates in the reader's language, never shown as JSON. Under a ticket search the ticket list can repeat exactly (at most one category and one priority, no author, date or deadline criterion; chat ADR 0010), a 13px Ink semibold link underlined in ink, "Show these in the list", opens the list with that search's filters and order in its address, so the reader moves from the answer to the queue in one click; like every link in the conversation, it closes the panel where the panel covers the page. Each step has a 12px marker on a 1px Hairline rail: an Ink `xs` spinner on the step the chat is on and on a call the model is still writing, a drawn Ink check on a step it moved past, a hollow 7px ring on the last step of a turn stopped or failed before the answer (it may never have finished) and on every call the model never finished writing. Before the first call, and until the first piece of the model's reasoning, a single "Preparing the answer…" step holds the place. `data-step` carries each step's state.
- **Reasoning:** when the model returns reasoning before its answer (chat ADR 0020), a step "Thinking" takes the place of the preparing step on the first piece, drawn the same way. Under it, in line with its words, the reasoning flows in a window three lines tall (60px) that shows its latest lines: 13px Ink 2 at a 20px line height, through the answer's Markdown renderer and its word-by-word flow, with no space between blocks, so the window always holds whole lines. The window keeps its height, so nothing below it moves, and a screen reader does not see it. On the first event that is not reasoning the window folds at once and the step reads "Thought for 4 s" behind a drawn check; the browser counts the seconds from the first piece to that event, and says at least 1. After the words comes a ghost `xs` button "Show reasoning" with `aria-expanded`, which unfolds the whole text downward under the step, in the same 13px Ink 2 with the answer's 8px between blocks. The step stays with its turn, and a turn has one: reasoning that arrives after another event joins its folded text. A turn stopped or failed before the first word of its answer keeps the step as "Thinking" behind a hollow Ink 2 ring (7px), with the button for what arrived, above the stop line or the problem banner; Try again starts the turn without it. The step opts out of the conversation's live region (`aria-live="off"`), so the reasoning is never announced; unfolded, it reads as ordinary content. The text is the provider's, in English also under a Polish question; the step's words follow the interface language. A turn without reasoning shows the preparing step alone, as before. Where the turn also has tool steps, the step sits under them, one for the whole turn. `data-step` carries the step's state: `active`, `done` or `interrupted`.
- **Answer:** full width, no bubble, 14px Ink at a 1.55 line height, Markdown with 8px between blocks. It flows in word by word, catching up with the tokens that have arrived (and with the last ones after the stream ends), behind a 2px Ink caret that blinks at the end of the last block while the answer is being written. Every ticket reference becomes a link to the ticket in the queue's reference style (mono 13px, ink, underlined 4px below); the model's own links and images are dropped. Inline code is mono 13px on Tone at the stamp's 3px radius; a heading is 15px bold; a table is 13px with Hairline cells under the ticket table's header (mono 11px Ink 2 capitals on a Card), and scrolls sideways inside the answer rather than widening the panel. A speaker label ("You", "Chat") is visually hidden.
- **Ticket rows:** under a finished answer, the tickets the tools returned, under a 1.5px ink rule as the table's rows sit under its header, one 13px row each with 8px above and below and a dashed Hairline after it, the whole row one link target. The reference (the queue's reference style) leads, the status stamp and the list's own Response cell sit on the trailing edge (the SLA stamp of a live breach first, then the clock with its lamp, with the same words, weights and hidden full sentence as the list; a customer reads the clock in their own words, with no stamp), and the title takes a line of its own below, truncated. Where the stamp and the clock do not share the line (a long status on a phone), the clock moves under the stamp whole rather than breaking between a number and its unit. Past five rows a ghost `xs` Show all unfolds the rest. The clock ticks like the list's, so the two never show different figures for one ticket.
- **States:** a stopped answer ends with a 13px Ink 2 line saying so. A failed turn shows the problem banner, with Try again on its trailing edge on the last turn; a question the guardrail blocked for personal data has none, as the same question would be refused again. The conversation is a polite live region, busy while an answer streams and until it is fully on screen, so it is read once complete rather than word by word. It follows its growing end while the reader is at the end, and leaves a reader who scrolled up where they are.
- **Motion:** arrivals decelerate (`cubic-bezier(0.16, 1, 0.3, 1)`) and departures are instant. The panel enters from the trailing edge beside the page (24px, 320ms) and from below where it covers it (16px, 280ms); a question, a step, a stop line or an error rises 6px and fades in (260ms); the example questions follow each other 60ms apart; a step's new marker settles from 40% scale (220ms) and its check draws itself (320ms); the first words of the answer fade in (200ms). Under `prefers-reduced-motion` every movement becomes a 150ms fade, the check appears drawn and the caret stops blinking; the flowing answer shows whole. The motion lives in `features/chat/chat-motion.ts`, not in the theme.
- **Composer:** a 64px non-resizing textarea (14px) beside one 96px × 44px button slot, lifted 3px so the button's shadow ends with the box: Send (the ink pill) at rest, Stop (outline) while an answer streams, in the same slot so neither moves. The textarea stays editable while an answer streams. Enter sends, Shift+Enter breaks the line. Under it, a 12px Ink 2 line: "The chat only reads tickets."
- **Open ticket:** while the page beside the chat shows a loaded ticket, one 13px row above the textarea names it, as a group labelled "Asking about RET-1042": the reference (the queue's reference style, a link), an Ink 2 "·", the title in Ink 2 truncated to the row, and a `2xs` close button ("Stop asking about RET-1042") on the trailing edge. The placeholder becomes "Ask about RET-1042". The row grows the composer upward, so the textarea and its button keep their place; it is announced politely as the ticket changes. Removing it hands focus to the textarea and lasts until the reader leaves that ticket.
- **Empty conversation:** the empty state ("Ask about your tickets" and a hint), then three example questions for the reader's role as `SuggestionButton`s 10px apart; one tap asks it. With a ticket open the questions are about that ticket instead: "Summarise this ticket" and "What is the customer waiting for?" for support staff, "Where does this ticket stand?" for a customer.
- **Below `lg`:** the panel covers the area under the header at full width, and following a ticket link from an answer closes it to show the ticket.

**The Alive Not Loud Rule.** The chat is where the desk is allowed to impress, and it does so through motion and states, never through new colours, gradients or a persona: tool calls arriving step by step, the answer streaming in smoothly, the panel opening with intent. Everything stays on the desk's tokens, and every motion respects `prefers-reduced-motion`.

**Known drift.** The answer's 1.55 line height is a one-off.

### Status Line
Under the ticket page's headline, in place of a subtitle: the status stamp, "{priority} priority" (with the list's emphasis), "Assigned to {name}" or Ink 2 "Unassigned", and for support staff the clock as a sentence after its lamp ("First response due in 40 min", "First response after 2 h", or for a breach already settled, in Ink 2 beside an unlit lamp, "First response 6 h after the response deadline" or "Closed without a first response, after the response deadline"), or for a live breach "First response 4 h past the response deadline" after a danger "SLA breached" stamp in place of the lamp. That stamp is the page's only SLA mark, and only a live breach draws it. When a customer's public comment still awaits support's answer (an unanswered customer comment), support staff read after the clock, beside an amber lamp in semibold Ink and never red, how long it has waited ("Customer wrote 20 min ago"), as the list's Response column says it; a customer never sees it. A customer reads the clock in their own words, as in the list, with no stamp and no lamp. Items wrap with a 12px gap and no separators. A customer whose answer is awaited reads "Support is waiting for your answer" in semibold Ink in place of the status stamp, followed by an outline Reply (`sm`; the composer's Send stays the one filled action) that glides the comment composer to the middle of the view and focuses it (no glide under reduced motion); support staff keep the stamp. A customer's status line on an open ticket keeps Reply's height (36px, 44px on a coarse pointer) whether Reply is there or not, so answering, which takes it away, moves nothing under the header. The customer's close dialog then opens on "Support asked you a question on this ticket. Close it anyway?" in semibold Ink beside an outline Answer instead (`xs`, focused first), which closes the dialog and hands focus to the composer, and labels the field "How was it solved?" for every customer ("Resolution" for support staff). On a ticket that is not theirs and not closed, support staff get an outline Assign to me in the action row above the h1, leading the trailing actions (before Close ticket), so its going moves no other action. One write sets the reader's own team and the reader; while it runs the button is disabled with its label and width kept, a refusal is told at the top of the side column like Close's, and once taken focus moves to "Assigned to {name}" in the status line.

### Live Region
The ticket page holds one visually hidden polite live region (`useTicketWrites().status`) that announces each write the server took ("Comment sent.", "Details saved.", "Assigned to you.", "RET-1003 linked.", "Ticket closed."). A refused field takes the focus, the first one in form order.

### Empty State
Centred, 36px vertical padding, Ink 2: an optional icon, a bold Ink title, the hint, and one action 16px below. A not-found state (the 404 page, a ticket that does not exist or is not visible) sets a 96px Jersey "404" in Ink as its icon (`NotFoundMark`); no emoji stands in for an icon. A state that stands for the whole page (not found, the route error) is `headline`: its title is the page's h1 in 32px Jersey, names the tab and takes the focus on arrival.

### History
The audit history lists each change under a fixed 150px time column of mono 12px Ink 2 times (the time heads each entry on a phone): the actor in semibold, then the change as a sentence ("Status: New → In progress", "Assignee set to Sam Support"). A screen reader hears a pause after the actor (a visually hidden comma) and the arrow as a word: the arrow is `aria-hidden` beside a visually hidden "to" ("na" in Polish).

### Linked Tickets
Support staff's panel lists each link (its reference a mono ink underlined link, its status a neutral stamp) with a ghost Remove; removing one takes one click and leaves an inline notice under the list, "Removed the link to RET-1012." with an outline Undo (`xs`, on a Sheet) that links it again as it was (link writes are idempotent and a potential duplicate keeps its direction whichever end asks), until the next removal or Link a ticket. Below sits an outline Link a ticket (`xs`) that unfolds the form in its place (Ticket search and Link as, then Cancel and an outline Link); focus moves into Ticket on open and back to Link a ticket on Cancel or after a link. After a removal focus moves to the next Remove, else the previous one, else the notice's Undo. Text people wrote (a title, a resolution) is quoted (“…” in English, „…” in Polish) and previewed up to its first line break and about 80 characters, cut between words with an ellipsis; when the preview leaves something out, both versions follow in full under Show the change, as a description edit always does.

## Do's and Don'ts

### Do:
- **Do** read every colour, radius, border width, shadow and font from the tokens in `frontend/src/theme.ts`; components never hold a colour literal.
- **Do** let the button recipe draw every button: set only `variant`, `size` and `colorPalette`, and fill at most one ink button per form or panel action area.
- **Do** keep every control in a row at 40px with the same label row (`CONTROL_STYLE`, `FormField`).
- **Do** give state-dependent table columns fixed widths and keep a pending or disabled control's label and width.
- **Do** draw hover, a 2px ink focus-visible ring, `aria-pressed` and `aria-invalid` states on every new control.
- **Do** pair every colour signal with words: the SLA stamp says "SLA", a lamp sits beside its clock or count, status stamps say the status, notes say "Internal note".
- **Do** name the reader of anything written: a comment's label and send button say who will read it.
- **Do** give every failed load a Try again beside its problem banner, and keep a loaded view on screen when only a refresh of it failed.
- **Do** let text people write (titles, descriptions, comments, names) break anywhere (`overflow-wrap: anywhere`), so a pasted URL never widens a panel or a column.
- **Do** build from the shared primitives in `frontend/src/components/ui` (`Panel`, `WindowPanel`, `Chip`, `Lamp`, `SegmentedToggle`, `FieldLabel`, `SuggestionButton`) and extend one when a feature needs a variant.
- **Do** show every tool call an AI feature makes, in words, before the answer it fed; later AI surfaces keep this transparency.

### Don't:
- **Don't** bring back a blue or any second action hue; ink is the action and the focus.
- **Don't** set a sentence in Jersey 25 or in mono capitals, fake a bold Jersey, or set text below 11px.
- **Don't** blur a shadow or lift a plain panel, row or the queue window; only the raised card, dialogs, menus, floating notices and buttons take the hard offset.
- **Don't** spread red to anything that is not a live SLA breach or a failure, and never to a button.
- **Don't** show a lamp without words beside it, or give a customer a lamp.
- **Don't** let a filter, loading state or empty result move a control the reader did not touch.
- **Don't** colour a notice that is not a failure (offline, session ended, closed ticket) red; it takes Tone or a Sheet.
- **Don't** hide tool calls or show them as raw JSON.
