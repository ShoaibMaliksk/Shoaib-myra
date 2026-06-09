import React, { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { Key, Eye, EyeOff, Save, Trash2, X, ExternalLink, Sparkles } from 'lucide-react';

interface Props {
  onClose: () => void;
  onSave: (key: string) => void;
}

export default function SettingsModal({ onClose, onSave }: Props) {
  const [apiKey, setApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');

  useEffect(() => {
    const savedKey = localStorage.getItem('zoya_gemini_api_key') || '';
    setApiKey(savedKey);
  }, []);

  const handleSave = () => {
    onSave(apiKey);
    setStatusMessage('Key saved successfully!');
    setTimeout(() => {
      setStatusMessage('');
      onClose();
    }, 1500);
  };

  const handleClear = () => {
    localStorage.removeItem('zoya_gemini_api_key');
    setApiKey('');
    onSave('');
    setStatusMessage('Custom key cleared! Using default key (if available).');
    setTimeout(() => {
      setStatusMessage('');
    }, 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        className="w-full max-w-lg bg-[#0d0d0d] border border-white/10 rounded-3xl p-6 md:p-8 shadow-2xl flex flex-col relative overflow-hidden"
      >
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-violet-500 via-pink-500 to-cyan-500 animate-pulse" />
        
        {/* Close Button */}
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors border border-white/5 text-white/70 hover:text-white"
        >
          <X size={18} />
        </button>

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="w-12 h-12 rounded-full bg-violet-500/20 flex items-center justify-center text-violet-400">
            <Sparkles size={24} />
          </div>
          <div>
            <h2 className="text-xl md:text-2xl font-serif font-medium text-white">Gemini API Settings</h2>
            <p className="text-xs text-white/50">Configure your personal Google AI Studio Key</p>
          </div>
        </div>

        {/* Description */}
        <p className="text-xs md:text-sm text-white/70 mb-5 leading-relaxed">
          Put your own **Gemini API Key** inside the app. This is perfect for custom deployments (like **Vercel** or **GitHub Pages**) so the voice assistant works directly with your personal quota.
        </p>

        {/* Key input box */}
        <div className="space-y-2 mb-6">
          <label className="text-xs font-semibold uppercase tracking-wider text-white/40 block">Gemini API Key</label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-white/30">
              <Key size={16} />
            </span>
            <input 
              type={showKey ? "text" : "password"}
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="AIzaSy..."
              className="w-full py-3.5 pl-11 pr-12 bg-white/5 border border-white/10 rounded-xl text-white text-sm outline-none focus:border-violet-500/80 focus:bg-white/10 transition-all font-mono"
            />
            <button
              type="button"
              onClick={() => setShowKey(!showKey)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-white/40 hover:text-white/80 transition-colors"
            >
              {showKey ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {statusMessage && (
            <p className="text-xs text-cyan-400/90 font-medium pl-1 animate-pulse">{statusMessage}</p>
          )}
        </div>

        {/* Guide box */}
        <div className="bg-white/5 border border-white/10 rounded-xl p-4 text-xs text-white/60 space-y-3 mb-6">
          <div className="flex items-center justify-between text-white/80 font-medium">
            <span>Don't have a Gemini API Key?</span>
            <a 
              href="https://aistudio.google.com/app/apikey" 
              target="_blank" 
              rel="noopener noreferrer"
              className="text-violet-400 hover:text-violet-300 flex items-center gap-1 hover:underline"
            >
              Get Free Key <ExternalLink size={12} />
            </a>
          </div>
          <ol className="list-decimal pl-4 space-y-1.5 leading-relaxed">
            <li>Go to the Google AI Studio page linked above.</li>
            <li>Click <strong>Create API Key</strong>.</li>
            <li>Copy the generated key and paste it into the field above.</li>
          </ol>
        </div>

        {/* Call to action buttons */}
        <div className="flex flex-col sm:flex-row gap-3">
          <button 
            type="button"
            onClick={handleSave}
            className="flex-1 py-3 px-4 bg-gradient-to-r from-violet-500 to-pink-500 text-white font-medium rounded-xl hover:from-violet-600 hover:to-pink-600 transition-all flex items-center justify-center gap-2 shadow-lg shadow-violet-500/20 hover:scale-[1.01]"
          >
            <Save size={16} />
            Save Key
          </button>
          
          {apiKey && (
            <button 
              type="button"
              onClick={handleClear}
              className="py-3 px-4 bg-red-500/10 hover:bg-red-500/20 text-red-400 font-medium rounded-xl border border-red-500/20 transition-all flex items-center justify-center gap-2"
              title="Clear Saved Key"
            >
              <Trash2 size={16} />
              Clear Key
            </button>
          )}

          <button 
            type="button"
            onClick={onClose}
            className="py-3 px-4 bg-white/5 text-white/70 font-medium rounded-xl hover:bg-white/10 transition-colors border border-white/5"
          >
            Cancel
          </button>
        </div>
      </motion.div>
    </div>
  );
}
