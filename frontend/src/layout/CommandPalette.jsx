import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, Shield, KeyRound, FileSignature, FileCheck2, Users, ArrowRight, CornerDownLeft } from 'lucide-react';
import api from '../api/axios';

export default function CommandPalette({ isOpen, onClose }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const inputRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setResults([]);
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  // Debounced search
  useEffect(() => {
    if (!query.trim()) {
      setResults([
        { id: 'nav-dash', type: 'page', title: 'Go to Dashboard', path: '/dashboard', icon: Shield },
        { id: 'nav-cert', type: 'page', title: 'Manage Certificates', path: '/certificates', icon: Shield },
        { id: 'nav-tkn', type: 'page', title: 'View Cryptographic Tokens', path: '/tokens', icon: KeyRound },
        { id: 'nav-sign', type: 'page', title: 'Sign a Document', path: '/sign', icon: FileSignature },
        { id: 'nav-verify', type: 'page', title: 'Verify Digital Signature', path: '/verify', icon: FileCheck2 },
      ]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await api.get(`/search?q=${encodeURIComponent(query)}`);
        const { certificates = [], tokens = [], users = [] } = res.data || {};
        const serverResults = [...certificates, ...tokens, ...users].map((item) => ({
          id: `${item.type}-${item.id}`,
          type: item.type,
          title: item.title,
          subtitle: item.subtitle,
          // There is no per-certificate route; open the registry instead.
          path: item.type === 'Certificate' ? '/certificates' : item.url,
          icon: item.type === 'Certificate' ? Shield : item.type === 'Token' ? KeyRound : Users,
        }));
        setResults(serverResults);
        setSelectedIndex(0);
      } catch (err) {
        console.error('Search error', err);
      } finally {
        setLoading(false);
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [query]);

  // Keyboard navigation
  const handleKeyDown = (e) => {
    if (e.key === 'Escape') {
      onClose();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % (results.length || 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + results.length) % (results.length || 1));
    } else if (e.key === 'Enter' && results[selectedIndex]) {
      e.preventDefault();
      handleSelect(results[selectedIndex]);
    }
  };

  const handleSelect = (item) => {
    onClose();
    if (item.path) {
      navigate(item.path);
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-start justify-center pt-24 px-4 bg-slate-950/70 backdrop-blur-md">
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: -20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: -20 }}
            transition={{ duration: 0.2 }}
            className="w-full max-w-xl glass-dropdown border-indigo-500/30 overflow-hidden shadow-2xl"
          >
            {/* Search Input bar */}
            <div className="flex items-center gap-3 px-4 py-3.5 border-b border-slate-800">
              <Search className="w-5 h-5 text-indigo-400 shrink-0" />
              <input
                ref={inputRef}
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Search certificates, tokens, documents, or navigation..."
                className="w-full bg-transparent text-sm text-slate-100 placeholder-slate-500 focus:outline-none"
              />
              <span className="text-[10px] font-mono bg-slate-800 px-1.5 py-0.5 rounded text-slate-400">
                ESC to close
              </span>
            </div>

            {/* Results list */}
            <div className="max-h-80 overflow-y-auto p-2 space-y-1">
              {results.length === 0 ? (
                <div className="p-6 text-center text-xs text-slate-500">
                  {loading ? 'Searching cryptographic registry...' : 'No matching items found.'}
                </div>
              ) : (
                results.map((item, idx) => {
                  const Icon = item.icon || Shield;
                  const isSelected = idx === selectedIndex;
                  return (
                    <div
                      key={item.id}
                      onClick={() => handleSelect(item)}
                      onMouseEnter={() => setSelectedIndex(idx)}
                      className={`flex items-center justify-between p-2.5 rounded-xl cursor-pointer text-xs transition-colors ${
                        isSelected ? 'bg-indigo-600/30 text-white' : 'hover:bg-slate-800/40 text-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div
                          className={`w-7 h-7 rounded-lg flex items-center justify-center border ${
                            isSelected
                              ? 'bg-indigo-500 text-white border-indigo-400'
                              : 'bg-slate-800 text-slate-400 border-slate-700'
                          }`}
                        >
                          <Icon className="w-3.5 h-3.5" />
                        </div>
                        <div className="min-w-0">
                          <p className="font-medium truncate">{item.title}</p>
                          {item.subtitle && <p className="text-[10px] text-slate-400 truncate">{item.subtitle}</p>}
                        </div>
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0 pl-2">
                        {item.type && (
                          <span className="text-[9px] uppercase font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
                            {item.type}
                          </span>
                        )}
                        {isSelected && <CornerDownLeft className="w-3.5 h-3.5 text-indigo-400" />}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
