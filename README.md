<div align="center">
  <img src="https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.ico" width="200" alt="YTMNT Logo">
  <h1>YTMNT (YouTube Music Ninja Tools)</h1>
</div>

<div align="center">

### [⬇️ **CLICK TO INSTALL SCRIPT (v5.8)**](https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.user.js)
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

### **🚀 Roadmap: v6.0 (The PeerJS Remote)**
*Coming soon to a dojo near you...*
* **WebRTC TV Remote:** A built-in Peer-to-Peer bridge using PeerJS.
* **How it will work:** Open YouTube Music on your phone, long-press the badge to enter "Remote Mode," and it will silently connect directly to the YTMNT instance running on your Android TV. 
* **The Goal:** Skip tracks, adjust volume, and change Stream Cycler modes on the TV instantly using your phone as a low-latency touch controller—zero backend servers required.
* 
