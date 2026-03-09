// ==UserScript==
// @name         YTMNT (YouTube Music Ninja Tools)
// @namespace    https://github.com/hanenashi/ytmnt
// @version      5.4
// @description  Cowabunga! Stream Cycler, Ad Skip, Mobile Drag, Audio Clicks & Animations.
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
    const CUSTOM_ICON = "https://raw.githubusercontent.com/hanenashi/ytmnt/main/ytmnt.ico";
    const CUSTOM_CLICK_SOUND = "https://raw.githubusercontent.com/hanenashi/ytmnt/main/click.wav";

    // Pre-load the sound effect
    const clickAudio = new Audio(CUSTOM_CLICK_SOUND);
    clickAudio.volume = 0.5; // 50% volume so it's not ear-piercing

    const STATE = {
        mode: 0, // 0=Audio, 1=Low, 2=HD
        lastInteraction: Date.now(),
        lastToggle: 0,
        isDragging: false,
        badgeId: 'ytmnt-badge-v5',
        toastId: 'ytmnt-toast-v5',
        pos: JSON.parse(localStorage.getItem('ytmnt-pos-v5')) || { x: 20, y: 120 }
    };

    // ==========================================
    // 2. CORE LOGIC
    // ==========================================
    const Logic = {
        applyMode: () => {
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
                case 0:
                    document.documentElement.classList.add('daemon-audio-mode');
                    Logic.forceQuality('tiny');
                    UI.updateBadge(0);
                    break;
                case 1:
                    document.documentElement.classList.remove('daemon-audio-mode');
                    Logic.forceQuality('large');
                    UI.updateBadge(1);
                    break;
                case 2:
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
                opacity: 0; transform: translateY(-20px); transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                z-index: 2147483647; pointer-events: none; box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            `;
            document.documentElement.appendChild(toast);
        },

        showToast: (msg) => {
            const toast = document.getElementById(STATE.toastId);
            if (!toast) return;
            toast.textContent = msg;
            toast.style.opacity = '1';
            toast.style.transform = 'translateY(0)';
            if (UI.timeout) clearTimeout(UI.timeout);
            UI.timeout = setTimeout(() => {
                toast.style.opacity = '0';
                toast.style.transform = 'translateY(-20px)';
            }, 2000);
        },

        renderBadge: () => {
            if (document.getElementById(STATE.badgeId)) return;

            const badge = document.createElement('div');
            badge.id = STATE.badgeId;
            
            // Added -webkit-tap-highlight-color and improved transition
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
                transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.1s linear;
                cursor: pointer;
                -webkit-tap-highlight-color: transparent;
                outline: none;
            `;

            const icon = document.createElement('img');
            icon.src = CUSTOM_ICON;
            icon.style.cssText = `
                width: 20px; height: 20px; border-radius: 50%;
                object-fit: contain; transition: filter 0.3s;
            `;

            const text = document.createElement('div');
            text.textContent = 'INIT';
            text.style.cssText = `
                color: #fff; font-size: 11px; font-weight: 800; 
                letter-spacing: 0.5px; text-transform: uppercase;
                margin-left: 2px;
            `;

            badge.appendChild(icon);
            badge.appendChild(text);

            const handleStart = (e) => {
                fixAudioContext();
                if (e.pointerType === 'mouse' && e.button !== 0) return;
                if (e.cancelable) e.preventDefault();

                STATE.isDragging = true;
                const startX = e.clientX;
                const startY = e.clientY;
                const initialPos = { ...STATE.pos };

                // Press visual (Scale down)
                badge.style.transform = `translate(${initialPos.x}px, ${initialPos.y}px) scale(0.92)`;
                badge.style.background = 'rgba(40, 40, 40, 1)';
                
                let hasMoved = false;

                const handleMove = (moveEvent) => {
                    if (moveEvent.cancelable) moveEvent.preventDefault();
                    const dx = moveEvent.clientX - startX;
                    const dy = moveEvent.clientY - startY;

                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        hasMoved = true;
                        STATE.pos.x = initialPos.x + dx;
                        STATE.pos.y = initialPos.y + dy;
                        // Keep the scale down while dragging
                        badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(0.92)`;
                    }
                };

                const handleEnd = () => {
                    window.removeEventListener('pointermove', handleMove);
                    window.removeEventListener('pointerup', handleEnd);
                    window.removeEventListener('pointercancel', handleEnd);
                    
                    STATE.isDragging = false;
                    // Release visual (Spring back up to scale 1.0)
                    badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.0)`;
                    badge.style.background = 'rgba(10, 10, 10, 0.95)';

                    if (hasMoved) {
                        localStorage.setItem('ytmnt-pos-v5', JSON.stringify(STATE.pos));
                    } else {
                        const now = Date.now();
                        if (now - STATE.lastToggle > 300) {
                            STATE.lastToggle = now;
                            
                            // Play the click sound
                            clickAudio.currentTime = 0; // Reset in case of rapid clicks
                            clickAudio.play().catch(()=>{});

                            STATE.mode = (STATE.mode + 1) % 3;
                            Logic.applyMode();
                            const msgs = ['🎧 Audio Only', '🟡 Low Video', '🔴 HD Video'];
                            UI.showToast(msgs[STATE.mode]);
                        }
                    }
                };
                
                window.addEventListener('pointermove', handleMove, { passive: false });
                window.addEventListener('pointerup', handleEnd, { passive: false });
                window.addEventListener('pointercancel', handleEnd, { passive: false });
            };

            badge.addEventListener('pointerdown', handleStart);

            badge.iconRef = icon;
            badge.textRef = text;

            document.documentElement.appendChild(badge);
            Logic.applyMode();
        },

        updateBadge: (mode) => {
            const badge = document.getElementById(STATE.badgeId);
            if (!badge) return;

            if (mode === 0) badge.iconRef.style.filter = 'drop-shadow(0 0 4px #0f0)';
            else if (mode === 1) badge.iconRef.style.filter = 'drop-shadow(0 0 4px #ffd700)';
            else badge.iconRef.style.filter = 'drop-shadow(0 0 4px #ff4444)';

            if (mode === 0) badge.textRef.textContent = 'AUDIO';
            else if (mode === 1) badge.textRef.textContent = 'LOW RES';
            else badge.textRef.textContent = 'HD VIDEO';
        },

        persistenceLoop: () => {
            if (!document.getElementById(STATE.badgeId)) {
                UI.renderBadge();
            } else {
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
    const recordInteraction = () => { STATE.lastInteraction = Date.now(); };
    ['pointerdown', 'keydown', 'scroll'].forEach(evt => {
        window.addEventListener(evt, recordInteraction, { capture: true, passive: true });
    });

    ['pointerdown', 'keydown'].forEach(evt => {
        window.addEventListener(evt, () => { if (!audioFixed) fixAudioContext(); }, { capture: true, passive: true });
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
        if (STATE.mode === 0) Logic.forceQuality('tiny');
        if (STATE.mode === 1) Logic.forceQuality('large');
        if (STATE.mode === 2) Logic.forceQuality('highres');

        const skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
        if (skip) { 
            skip.click(); 
            UI.showToast('⏩ Ad Skipped'); 
        }

        const idle = document.querySelector('ytmusic-you-there-renderer button');
        if (idle) { 
            idle.click(); 
            UI.showToast('👋 Idle Skipped'); 
            
            setTimeout(() => {
                const player = document.getElementById('movie_player');
                if (player && typeof player.playVideo === 'function') {
                    player.playVideo();
                }
                const vid = document.querySelector('video');
                if (vid && vid.paused) {
                    vid.play().catch(err => console.warn('[YTMNT] Background play rejected:', err));
                }
            }, 100);
        }
    }, 2000);

    if (document.documentElement) UI.init();
    else window.addEventListener('DOMContentLoaded', UI.init);

    console.log('[YTMNT] v5.4 Audio & Animation Patch Loaded');
})();
