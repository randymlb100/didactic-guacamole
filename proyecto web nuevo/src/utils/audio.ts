let sharedAudioCtx: AudioContext | null = null;
let audioUnlocked = false;

type AudioContextConstructor = typeof AudioContext;

interface BrowserAudioWindow extends Window {
  webkitAudioContext?: AudioContextConstructor;
}

const getSharedAudioContext = (): AudioContext | null => {
  try {
    if (!sharedAudioCtx) {
      const AudioContextClass = window.AudioContext || (window as BrowserAudioWindow).webkitAudioContext;
      if (AudioContextClass) {
        sharedAudioCtx = new AudioContextClass();
      }
    }
    if (audioUnlocked && sharedAudioCtx && sharedAudioCtx.state === 'suspended') {
      sharedAudioCtx.resume().catch(() => {});
    }
    return sharedAudioCtx;
  } catch {
    return null;
  }
};

export const unlockUiAudio = () => {
  audioUnlocked = true;
  const ctx = getSharedAudioContext();
  if (ctx?.state === 'suspended') {
    ctx.resume().catch(() => {});
  }
};

export const playTapSound = () => {
  try {
    if (!audioUnlocked) return;
    const ctx = getSharedAudioContext();
    if (!ctx) return;
    
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    
    osc.type = 'sine';
    // Digital micro-chirp sound (pleasant UI touch feedback)
    osc.frequency.setValueAtTime(550, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(900, ctx.currentTime + 0.045);
    
    gain.gain.setValueAtTime(0.007, ctx.currentTime); // Very soft background volume
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.05);
    
    osc.connect(gain);
    gain.connect(ctx.destination);
    
    osc.start();
    osc.stop(ctx.currentTime + 0.06);
  } catch {
    // ignore
  }
};

export const playClickSound = () => {
  try {
    if (!audioUnlocked) return;
    const ctx = getSharedAudioContext();
    if (!ctx) return;
    
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    
    osc.type = 'sine';
    // Modulate pitch slightly around a pleasant high frequency (cherry mechanical keyboard click sound)
    const freq = 1300 + Math.random() * 300;
    osc.frequency.setValueAtTime(freq, ctx.currentTime);
    
    // Very rapid envelope (amplitude sweep)
    gain.gain.setValueAtTime(0.012, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.025);
    
    osc.connect(gain);
    gain.connect(ctx.destination);
    
    osc.start();
    osc.stop(ctx.currentTime + 0.035);
  } catch {
    // ignore
  }
};
