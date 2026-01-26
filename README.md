# TrackpadDS
AYN Thor Bottom Screen Power Tool (TrackPadDS) is an Android overlay app that turns the secondary display into configurable trackpads and floating navigation controls. It uses an accessibility service and overlay permissions to drive a cursor, clicks, scrolling, and global actions, with optional screen-mirror mode for touch passthrough. The app includes granular UI controls (visibility, opacity, size, haptics, sensitivity) plus backup/restore of settings and positions.

## Features
- Two main feature sets:
  - Trackpad and action buttons: overlays for left/right trackpads plus floating navigation and control buttons.
  - Screen mirror mode: mirrors the primary screen to the bottom screen and forwards touch input.
- Overlay elements are togglable, rearrangeable, resizable, and support configurable opacity.

## How to use
### Trackpad and action buttons
- Enable the overlay to add:
  - Left/right trackpads for cursor control.
  - Floating navigation buttons: Back, Recent Apps, Home.
  - Show/hide layout button.
  - Screen mirror mode toggle button.
- To make an element press-through, hold the show/hide button briefly.

### Screen mirror mode
- Enable the mirror toggle to display the primary screen on the bottom screen.
- Touch input on the bottom screen is replicated to the primary screen.

## Known issues / to fix
- Screen mirror mode does not work well with edge swipe-in.
- Screen mirror mode does not support multi-touch yet.
- Cursor movable window are shifted to the left.
- Swap button sometimes swaps AYN system UI apps; it should only swap normal apps.
