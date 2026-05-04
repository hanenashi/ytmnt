<div align="center">
  <img src="https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.ico" width="200" alt="YTMNT Logo">
  <h1>YTMNT (YouTube Music Ninja Tools)</h1>
</div>

<div align="center">

### [⬇️ **CLICK TO INSTALL SCRIPT (v5.9)**](https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.user.js)
*(Requires Violentmonkey / Tampermonkey)*

</div>

---

### **TL;DR**
The ultimate "God Mode" for YouTube Music on Web, Mobile, and Smart TVs. It forces background playback, saves massive amounts of data, obliterates ads, and gives you a fully draggable universal control panel.

### **Features**
* **3-Mode Stream Cycler:** Click the colored badge to cycle:
    * 🟢 **AUDIO:** Video blacked out, Quality forced to 144p (Max battery/data saving).
    * 🟡 **LOW:** Video visible, Quality ~480p.
    * 🔴 **HD:** Video visible, Max Resolution.
* **📱 Mobile & Touch Optimized:**
    * **Universal Pointer Drag:** Move the badge anywhere using a mouse, touch, or TV air mouse (position automatically saves).
    * **Bumper Car Bounds:** The badge mathematically cannot be dragged off-screen, auto-recovering on device rotation.
    * **Background Play:** Prevents Android and Android TV from killing the audio engine when the screen is off or you go to the home screen.
* **🛡️ Robust Auto-Ninja:**
    * **Ad Skipper:** Auto-clicks "Skip Ad" instantly.
    * **Anti-Idle:** Auto-clicks "Are you still there?" popups and uses a "Wake-Up Hammer" to keep audio playing.
    * **Search Proof:** UI survives single-page navigation and search wipes.
* **🔊 Audio & Easter Eggs:**
    * Custom spring animations and audio feedback (`click.wav`) on mode toggle.
    * Right-click (or mobile long-press) for the `cowabunga.mp3` easter egg!

### **How to Use (Standard)**
1.  **Install** a userscript manager (Violentmonkey is highly recommended).
2.  **Click** the [Install Link](https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.user.js) above.
3.  **Open** [music.youtube.com](https://music.youtube.com).
4.  **Cowabunga!** The badge will appear in the top-left (default). Click it to cycle modes, drag it to move it anywhere.

---

### **Advanced Setups**

#### **Android TV / Google TV God Mode (No Cast Required)**
You can run this directly on your living room TV to create the ultimate ad-free jukebox.
1. Download the **Kiwi Browser** APK to your TV (via a USB drive or the "Send Files to TV" app).
2. Open Kiwi, click the **three-dot menu (⋮)**, and check **Desktop site**.
3. Install the **uBlock Origin** and **Violentmonkey** extensions directly from the Chrome Web Store.
4. Open the YTMNT install link above.
5. *Pro-Tip:* Use a Bluetooth mouse or an Air Mouse remote to navigate. Go to your TV's main settings -> Apps -> Kiwi Browser, and disable **Energy/Battery optimization** so the music keeps playing even when you press the TV Home button!

#### **Pixel / Mobile Background Play**
Using Kiwi Browser on Android? You don't need YouTube Premium. Once YTMNT is running, you can turn your screen off. If Android puts the browser to sleep, the script's `AudioContext` loop will automatically shock it back awake.

---

### **🚀 Roadmap: v6.0 (PeerJS TV Remote)**
The next big idea is a phone remote for a TV running YTMNT in Kiwi Browser. The chosen direction is **Option 1: a separate remote web page** for the phone, instead of requiring the phone to run YouTube Music or the userscript too.

#### **Target Experience**
1. Open YouTube Music on the Android TV / Google TV browser with YTMNT installed.
2. Long-press the YTMNT badge on the TV to enter **Remote Mode**.
3. The TV shows a short pairing code, and later possibly a QR code.
4. Open a lightweight YTMNT remote page on the phone.
5. Enter or scan the TV code.
6. Use the phone to control the TV instantly:
    * Play / pause
    * Next / previous track
    * Volume up / down, eventually a slider
    * Switch YTMNT stream mode: Audio / Low / HD
    * See basic connection and mode status

#### **Architecture**
* **TV side:** `ytmnt.user.js` becomes the host. It creates a PeerJS peer, shows the pairing code, accepts one trusted phone connection, receives commands, and translates them into YouTube Music actions.
* **Phone side:** a new `remote.html` page acts as the controller. It loads PeerJS, connects to the TV peer ID, and sends small JSON commands over the data connection.
* **Transport:** PeerJS data connections wrap WebRTC data channels, so control messages should be low-latency once connected.
* **Signaling note:** WebRTC still needs signaling before the peer-to-peer connection exists. For v1, use PeerJS Cloud because it is the fastest path. Later, we can support a self-hosted PeerServer for people who want more control. The actual remote commands should flow peer-to-peer after connection.

#### **MVP Scope**
* Add Remote Mode to the badge long-press flow.
* Generate a readable TV peer ID, ideally with a random short suffix.
* Show a compact TV overlay with:
    * Pairing code
    * Connection state
    * Exit Remote Mode action
* Add `remote.html` with:
    * Pairing code input
    * Connection status
    * Large TV-friendly remote buttons
    * Mode selector for Audio / Low / HD
* Define a tiny command protocol:
    * `{ "type": "playPause" }`
    * `{ "type": "next" }`
    * `{ "type": "previous" }`
    * `{ "type": "volume", "delta": 0.05 }`
    * `{ "type": "setMode", "mode": 0 }`
    * `{ "type": "ping" }`
* Keep the first version single-controller only. If a second phone connects, reject it or replace the previous connection deliberately.

#### **Security / Pairing**
* Do not accept arbitrary remote commands just because someone guessed a peer ID.
* Generate a per-session pairing token on the TV.
* Require the phone to send the token before commands are accepted.
* Remember trusted pairings only after the MVP works. Silent reconnect can come later.
* Add a visible TV indicator while a phone is connected, so the user always knows remote control is active.

#### **Implementation Order**
1. Extract the current player actions into small reusable helpers: play/pause, next, previous, volume, set mode.
2. Build the TV Remote Mode state machine: off, pairing, connected, error.
3. Load PeerJS on demand only when Remote Mode starts.
4. Build the PeerJS host and command receiver in `ytmnt.user.js`.
5. Add `remote.html` as the phone controller.
6. Test on desktop browser first with two tabs.
7. Test on Kiwi Android TV + phone.
8. Add polish: QR pairing, reconnect, status sync, better error messages.

#### **Open Questions**
* Where should `remote.html` live: GitHub Pages, raw GitHub, or embedded as a generated data URL from the TV?
* Should the TV peer ID be completely random, or human-readable like `ytmnt-4821`?
* Should volume control use the YouTube player API, the media element volume, or UI button clicks as fallback?
* Should long-press always play the easter egg, or should Remote Mode become the primary long-press action?
