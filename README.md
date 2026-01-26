# TrackpadDS
AYN Thor Bottom Screen Power Tool (TrackPadDS) is an Android overlay app that turns the secondary display into configurable trackpads and floating navigation controls. It uses an accessibility service and overlay permissions to drive a cursor, clicks, scrolling, and global actions, with optional screen-mirror mode for touch passthrough. The app includes granular UI controls (visibility, opacity, size, haptics, sensitivity) plus backup/restore of settings and positions.

<img width="956" height="500" alt="image" src="https://github.com/user-attachments/assets/9fef6515-8840-4385-b210-03f8ec279cd4" />


## Features
- Two main feature sets:
  - Trackpad and action buttons: overlays for left/right trackpads plus floating navigation and control buttons.
  - Screen mirror mode: mirrors the primary screen to the bottom screen and forwards touch input.
- Overlay elements are togglable, rearrangeable, resizable, and support configurable opacity.

## How to use
### Trackpad and action buttons

<img width="980" height="1096" alt="image" src="https://github.com/user-attachments/assets/a4cf0e3c-78ef-4567-a84d-0aaf720340f1" />

- Enable the overlay to add:
  - Left/right trackpads for cursor control.
  - Floating navigation buttons: Back, Recent Apps, Home.
  - Show/hide layout button.
  - Screen mirror mode toggle button.
- To make an element press-through, hold the show/hide button briefly.

### Screen mirror mode

<img width="980" height="1092" alt="image" src="https://github.com/user-attachments/assets/f50b4e28-13bf-4fc9-ad33-5b0bea320f23" />

- Enable the mirror toggle to display the primary screen on the bottom screen.
- Touch input on the bottom screen is replicated to the primary screen.

## Installing the app
There are 2 ways you can get the app:
- You can get the pre-built APK in Release section and side-load it to your devices.
- You can build the app from source code using Android Studio.

## Known issues / to fix
- The code is still mostly just a functional PoC, more refactoring is required.
- Screen mirror mode does not work well with edge swipe-in
- Screen mirror mode does not support multi-touch yet.
- Cursor movable window are shifted to the left.
- Swap button sometimes swaps AYN system UI apps; it should only swap normal apps.
- Currently there are a couple of measure to optimize battery use with the app on while screen is closed, however no extended testing have been done.

[![ko-fi](https://www.ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/minxf1)
