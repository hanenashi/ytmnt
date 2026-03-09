// ==UserScript==
// @name         YTMNT (YouTube Music Ninja Tools)
// @namespace    https://github.com/hanenashi/ytmnt
// @version      5.8
// @description  Cowabunga! Stream Cycler, Ad Skip, Mobile Drag, Audio Clicks & True Screen Bounds.
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
    const CUSTOM_CLICK_SOUND = "https://raw.githubusercontent.com/hanenashi/ytmnt/main/click.wav";
    const EASTER_EGG_SOUND = "https://raw.githubusercontent.com/hanenashi/ytmnt/main/cowabunga.mp3";

    const clickAudio = new Audio(CUSTOM_CLICK_SOUND);
    clickAudio.volume = 0.5;

    const cowabungaAudio = new Audio(EASTER_EGG_SOUND);
    cowabungaAudio.volume = 0.7; 

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
            
            // --- FIXED: ADDED TOP: 0; LEFT: 0; ---
            badge.style.cssText = `
                position: fixed; top: 0; left: 0; z-index: 2147483647;
                display: flex; align-items: center; gap: 8px;
                padding: 6px 12px; border-radius: 30px;
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

            const dot = document.createElement('div');
            dot.style.cssText = `
                width: 12px; height: 12px; border-radius: 50%;
                background: #666; transition: background 0.3s, box-shadow 0.3s;
            `;

            const text = document.createElement('div');
            text.textContent = 'INIT';
            text.style.cssText = `
                color: #fff; font-size: 11px; font-weight: 800; 
                letter-spacing: 0.5px; text-transform: uppercase;
                margin-left: 2px;
            `;

            badge.appendChild(dot);
            badge.appendChild(text);

            badge.addEventListener('contextmenu', e => e.preventDefault());

            let longPressTimer;
            let isLongPress = false;

            const handleStart = (e) => {
                fixAudioContext();
                
                if (e.pointerType === 'mouse' && e.button === 2) {
                    e.preventDefault();
                    cowabungaAudio.currentTime = 0;
                    cowabungaAudio.play().catch(()=>{});
                    UI.showToast('🐢🍕 COWABUNGA!');
                    
                    badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.1)`;
                    setTimeout(() => badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.0)`, 150);
                    return;
                }

                if (e.pointerType === 'mouse' && e.button !== 0) return;
                if (e.cancelable) e.preventDefault();

                STATE.isDragging = true;
                isLongPress = false;
                const startX = e.clientX;
                const startY = e.clientY;
                const initialPos = { ...STATE.pos };

                badge.style.transform = `translate(${initialPos.x}px, ${initialPos.y}px) scale(0.92)`;
                badge.style.background = 'rgba(40, 40, 40, 1)';
                
                let hasMoved = false;

                longPressTimer = setTimeout(() => {
                    isLongPress = true;
                    cowabungaAudio.currentTime = 0;
                    cowabungaAudio.play().catch(()=>{});
                    UI.showToast('🐢🍕 COWABUNGA!');
                    
                    badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.1)`;
                    setTimeout(() => {
                        if (STATE.isDragging) {
                            badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(0.92)`;
                        }
                    }, 150);
                }, 600); 

                const handleMove = (moveEvent) => {
                    if (moveEvent.cancelable) moveEvent.preventDefault();
                    const dx = moveEvent.clientX - startX;
                    const dy = moveEvent.clientY - startY;

                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        hasMoved = true;
                        clearTimeout(longPressTimer); 
                        
                        let nextX = initialPos.x + dx;
                        let nextY = initialPos.y + dy;
                        
                        const rect = badge.getBoundingClientRect();
                        const maxX = window.innerWidth - rect.width;
                        const maxY = window.innerHeight - rect.height;

                        STATE.pos.x = Math.max(5, Math.min(nextX, maxX - 5));
                        STATE.pos.y = Math.max(5, Math.min(nextY, maxY - 5));

                        badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(0.92)`;
                    }
                };

                const handleEnd = () => {
                    clearTimeout(longPressTimer);
                    window.removeEventListener('pointermove', handleMove);
                    window.removeEventListener('pointerup', handleEnd);
                    window.removeEventListener('pointercancel', handleEnd);
                    
                    STATE.isDragging = false;
                    badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.0)`;
                    badge.style.background = 'rgba(10, 10, 10, 0.95)';

                    if (hasMoved) {
                        localStorage.setItem('ytmnt-pos-v5', JSON.stringify(STATE.pos));
                    } else if (!isLongPress) {
                        const now = Date.now();
                        if (now - STATE.lastToggle > 300) {
                            STATE.lastToggle = now;
                            
                            clickAudio.currentTime = 0; 
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

            badge.dotRef = dot;
            badge.textRef = text;

            document.documentElement.appendChild(badge);
            Logic.applyMode();
        },

        updateBadge: (mode) => {
            const badge = document.getElementById(STATE.badgeId);
            if (!badge) return;

            if (mode === 0) {
                badge.dotRef.style.background = '#0f0';
                badge.dotRef.style.boxShadow = '0 0 8px #0f0';
                badge.textRef.textContent = 'AUDIO';
            } else if (mode === 1) {
                badge.dotRef.style.background = '#ffd700';
                badge.dotRef.style.boxShadow = 'none';
                badge.textRef.textContent = 'LOW RES';
            } else {
                badge.dotRef.style.background = '#ff4444';
                badge.dotRef.style.boxShadow = '0 0 8px #ff4444';
                badge.textRef.textContent = 'HD VIDEO';
            }
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

    // --- AUTO-RECOVERY ON RESIZE / ROTATION ---
    window.addEventListener('resize', () => {
        const badge = document.getElementById(STATE.badgeId);
        if (badge && !STATE.isDragging) {
            const rect = badge.getBoundingClientRect();
            const maxX = window.innerWidth - rect.width;
            const maxY = window.innerHeight - rect.height;
            
            let clampedX = Math.max(5, Math.min(STATE.pos.x, maxX - 5));
            let clampedY = Math.max(5, Math.min(STATE.pos.y, maxY - 5));
            
            if (clampedX !== STATE.pos.x || clampedY !== STATE.pos.y) {
                STATE.pos.x = clampedX;
                STATE.pos.y = clampedY;
                badge.style.transform = `translate(${STATE.pos.x}px, ${STATE.pos.y}px) scale(1.0)`;
                localStorage.setItem('ytmnt-pos-v5', JSON.stringify(STATE.pos));
            }
        }
    }, { passive: true });

    if (document.documentElement) UI.init();
    else window.addEventListener('DOMContentLoaded', UI.init);

    console.log('[YTMNT] v5.8 True Screen Bounds Loaded');
})();
