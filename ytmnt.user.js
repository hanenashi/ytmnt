// ==UserScript==
// @name         YTMNT (YouTube Music Ninja Tools)
// @namespace    https://github.com/hanenashi/ytmnt
// @version      5.0
// @description  Cowabunga! Stream Cycler, Ad Skip, Search-Proof UI & Mobile Drag.
// @author       Hanenashi & Gemini
// @homepage     https://github.com/hanenashi/ytmnt
// @updateURL    https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.user.js
// @downloadURL  https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.user.js
// @match        https://music.youtube.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(() => {
    'use strict';

    // ==========================================
    // 1. CONFIGURATION
    // ==========================================
    
    // Pulls icon directly from your GitHub 'main' branch
    const CUSTOM_ICON = "https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.ico";

    const STATE = {
        mode: 0, // 0=Audio, 1=Low, 2=HD
        lastInteraction: Date.now(),
        lastToggle: 0,
        isDragging: false,
        badgeId: 'ytmnt-badge-v5',
        toastId: 'ytmnt-toast-v5',
        // Load saved position or default to Top-Left area
        pos: JSON.parse(localStorage.getItem('ytmnt-pos-v5')) || { x: 20, y: 120 }
    };

    // ==========================================
    // 2. CORE LOGIC
    // ==========================================
    const Logic = {
        applyMode: () => {
            // Re-inject Nuke CSS
            if (!document.getElementById('ytmnt-style-v5')) {
                const style = document.createElement('style');
                style.id = 'ytmnt-style-v5';
                style.textContent = `
                    html.daemon-audio-mode #movie_player video,
                    html.daemon-audio-mode .html5-video-container {
                        opacity: 0 !important;
                        visibility: hidden !important;
                        pointer-events: none !important;
                        display: none !important;
                    }
                    html.daemon-audio-mode #player-canvas {
                        background: #000 !important;
                    }
                `;
                document.head.appendChild(style);
            }

            switch (STATE.mode) {
                case 0: // AUDIO
                    document.documentElement.classList.add('daemon-audio-mode');
                    Logic.forceQuality('tiny');
                    UI.updateBadge(0);
                    break;
                case 1: // LOW
                    document.documentElement.classList.remove('daemon-audio-mode');
                    Logic.forceQuality('large');
                    UI.updateBadge(1);
                    break;
                case 2: // HD
                    document.documentElement.classList.remove('daemon-audio-mode');
                    Logic.forceQuality('highres');
                    UI.updateBadge(2);
                    break;
            }
        },

        forceQuality: (quality) => {
            try {
                const player = document.getElementById('movie_player');
                if (player && player.setPlaybackQualityRange) {
                    player.setPlaybackQualityRange(quality, quality);
                }
            } catch (e) {}
        },

        handlePause: function(originalFn, args) {
            const isManual = (Date.now() - STATE.lastInteraction) < 2000;
            if (isManual) return originalFn.apply(this, args);
        }
    };

    // ==========================================
    // 3. VISIBILITY & AUDIO FIXES
    // ==========================================
    Object.defineProperties(document, {
        'visibilityState': { get: () => 'visible', configurable: true },
        'hidden': { get: () => false, configurable: true },
        'hasFocus': { value: () => true, configurable: true }
    });

    const blockEvent = (e) => { e.stopImmediatePropagation(); e.stopPropagation(); };
    ['visibilitychange', 'webkitvisibilitychange', 'blur'].forEach(evt => {
        window.addEventListener(evt, blockEvent, true);
        document.addEventListener(evt, blockEvent, true);
    });

    let audioFixed = false;
    const fixAudioContext = () => {
        if (audioFixed) return;
        const Ctx = window.AudioContext || window.webkitAudioContext;
        if (Ctx) {
            const ctx = new Ctx();
            setInterval(() => { if (ctx.state === 'suspended') ctx.resume(); }, 10000);
            audioFixed = true;
        }
    };

    // ==========================================
    // 4. UI BUILDER
    // ==========================================
    const UI = {
        init: () => {
            UI.ensureToast();
            UI.renderBadge();
            // Aggressive persistence loop (60fps check)
            requestAnimationFrame(UI.persistenceLoop);
        },

        ensureToast: () => {
            if (document.getElementById(STATE.toastId)) return;
            const toast = document.createElement('div');
            toast.id = STATE.toastId;
            toast.style.cssText = `
                position: fixed; top: 20px; right: 20px;
                background: #fff; color: #000; padding: 10px 20px;
                border-radius: 8px; font-family: sans-serif; font-weight: bold; font-size: 14px;
                opacity: 0; transform: translateY(-20px); transition: all 0.3s ease;
                z-index: 2147483647; pointer-events: none; box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            `;
            document.documentElement.appendChild(toast);
        },

        renderBadge: () => {
            if (document.getElementById(STATE.badgeId)) return;

            const badge = document.createElement('div');
            badge.id = STATE.badgeId;
            
            badge.style.cssText = `
                position: fixed; z-index: 2147483647;
                display: flex; align-items: center; gap: 8px;
                padding: 6px 10px; border-radius: 30px;
                background: rgba(10, 10, 10, 0.95);
                border: 1px solid rgba(255, 255, 255, 0.2);
                backdrop-filter: blur(12px);
                box-shadow: 0 4px 15px rgba(0,0,0,0.5);
                transform: translate(${STATE.pos.x}px, ${STATE.pos.y}px);
                touch-action: none; user-select: none;
                font-family: Roboto, sans-serif;
                transition: transform 0.05s linear;
                cursor: pointer;
            `;

            // --- ICON ---
            const icon = document.createElement('img');
            icon.src = CUSTOM_ICON;
            icon.style.cssText = `
                width: 20px; height: 20px; border-radius: 50%;
                object-fit: contain; transition: filter 0.3s;
            `;

            // --- TEXT LABEL ---
            const text = document.createElement('div');
            text.textContent = 'INIT';
            text.style.cssText = `
                color: #fff; font-size: 11px; font-weight: 800; 
                letter-spacing: 0.5px; text-transform: uppercase;
                margin-left: 2px;
            `;

            badge.appendChild(icon);
            badge.appendChild(text);

            // --- DRAG & TOUCH LOGIC ---
            const handleStart = (e) => {
                fixAudioContext();
                if (e.type === 'mousedown' && e.button !== 0) return;
                if (e.cancelable) e.preventDefault();

                STATE.isDragging = true;
                const isTouch = e.type === 'touchstart';
                const clientX = isTouch ? e.touches[0].clientX : e.clientX;
                const clientY = isTouch ? e.touches[0].clientY : e.clientY;
                const startX = clientX;
                const startY = clientY;
                const initialPos = { ...STATE.pos };

                // Press visual
                badge.style.transform = `translate(${initialPos.x}px, ${initialPos.y}px) scale(0.95)`;
                badge.style.background = 'rgba(40, 40, 40, 1)';
                
                let hasMoved = false;

                const handleMove = (moveEvent) => {
                    if (moveEvent.cancelable) moveEvent.preventDefault();
                    const moveX = isTouch ? moveEvent.touches[0].clientX : moveEvent.clientX;
                    const moveY = isTouch ? moveEvent.touches[0].clientY : moveEvent.clientY;
                    const dx = moveX - startX;
                    const dy = moveY - startY;

                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        hasMoved = true;
                        STATE.pos.x = initialPos.x + dx;
                        STATE.pos.y = initialPos.y + dy;
                        badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.0)`;
                    }
                };

                const handleEnd = () => {
                    window.removeEventListener(isTouch ? 'touchmove' : 'mousemove', handleMove);
                    window.removeEventListener(isTouch ? 'touchend' : 'mouseup', handleEnd);
                    
                    STATE.isDragging = false;
                    badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.0)`;
                    badge.style.background = 'rgba(10, 10, 10, 0.95)';

                    if (hasMoved) {
                        localStorage.setItem('ytmnt-pos-v5', JSON.stringify(STATE.pos));
                    } else {
                        // Click Toggle
                        const now = Date.now();
                        if (now - STATE.lastToggle > 300) {
                            STATE.lastToggle = now;
                            STATE.mode = (STATE.mode + 1) % 3;
                            Logic.applyMode();
                            const msgs = ['🎧 Audio Only', '🟡 Low Video', '🔴 HD Video'];
                            UI.showToast(msgs[STATE.mode]);
                        }
                    }
                };
                window.addEventListener(isTouch ? 'touchmove' : 'mousemove', handleMove, { passive: false });
                window.addEventListener(isTouch ? 'touchend' : 'mouseup', handleEnd, { passive: false });
            };

            badge.addEventListener('mousedown', handleStart);
            badge.addEventListener('touchstart', handleStart, { passive: false });

            badge.iconRef = icon;
            badge.textRef = text;

            document.documentElement.appendChild(badge);
            Logic.applyMode();
        },

        updateBadge: (mode) => {
            const badge = document.getElementById(STATE.badgeId);
            if (!badge) return;

            // Visual State (Glow on Icon)
            if (mode === 0) badge.iconRef.style.filter = 'drop-shadow(0 0 4px #0f0)'; // Green
            else if (mode === 1) badge.iconRef.style.filter = 'drop-shadow(0 0 4px #ffd700)'; // Yellow
            else badge.iconRef.style.filter = 'drop-shadow(0 0 4px #ff4444)'; // Red

            // Text Update
            if (mode === 0) badge.textRef.textContent = 'AUDIO';
            else if (mode === 1) badge.textRef.textContent = 'LOW RES';
            else badge.textRef.textContent = 'HD VIDEO';
        },

        // --- PERSISTENCE LOOP ---
        persistenceLoop: () => {
            // 1. Recreate if missing
            if (!document.getElementById(STATE.badgeId)) {
                UI.renderBadge();
            } else {
                // 2. Force Top (unless dragging)
                const badge = document.getElementById(STATE.badgeId);
                if (!STATE.isDragging && document.documentElement.lastElementChild !== badge) {
                    document.documentElement.appendChild(badge);
                }
            }
            requestAnimationFrame(UI.persistenceLoop);
        }
    };

    // ==========================================
    // 5. LISTENERS & BOOTSTRAP
    // ==========================================
    const recordInteraction = () => { STATE.lastInteraction = Date.now(); fixAudioContext(); };
    ['touchstart', 'touchmove', 'mousedown', 'keydown', 'scroll'].forEach(evt => {
        window.addEventListener(evt, recordInteraction, { capture: true, passive: true });
    });

    if ('mediaSession' in navigator) {
        const origSetAction = navigator.mediaSession.setActionHandler.bind(navigator.mediaSession);
        navigator.mediaSession.setActionHandler = (action, handler) => {
            origSetAction(action, (...args) => {
                recordInteraction();
                if (handler) handler(...args);
            });
        };
    }

    const origPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function() {
        return Logic.handlePause.call(this, origPause, arguments);
    };

    setInterval(() => {
        // Enforce Quality
        if (STATE.mode === 0) Logic.forceQuality('tiny');
        if (STATE.mode === 1) Logic.forceQuality('large');
        if (STATE.mode === 2) Logic.forceQuality('highres');

        const skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
        if (skip) { skip.click(); UI.ensureToast(); }
        const idle = document.querySelector('ytmusic-you-there-renderer button');
        if (idle) { idle.click(); UI.ensureToast(); }
    }, 2000);

    if (document.documentElement) UI.init();
    else window.addEventListener('DOMContentLoaded', UI.init);

    console.log('[YTMNT] v5.0 GitHub Edition Loaded');
})();
