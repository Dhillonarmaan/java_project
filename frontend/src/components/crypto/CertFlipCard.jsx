import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Shield, Key, Calendar, RotateCw, Copy, Check, Download, AlertTriangle } from 'lucide-react';
import StatusBadge from '../ui/StatusBadge';
import Button from '../ui/Button';
import { toast } from 'sonner';
import { subjectCn } from '../../lib/dn';

export default function CertFlipCard({ cert, onRevoke, onDownload }) {
  const [isFlipped, setIsFlipped] = useState(false);
  const [copied, setCopied] = useState(false);

  // Calculate validity percentage
  const notBefore = new Date(cert.validFrom).getTime();
  const notAfter = new Date(cert.validTo).getTime();
  const now = Date.now();
  const total = notAfter - notBefore;
  const elapsed = now - notBefore;
  const percentElapsed = Math.min(Math.max((elapsed / total) * 100, 0), 100);
  const remainingDays = Math.ceil((notAfter - now) / (1000 * 60 * 60 * 24));

  const copyFingerprint = (e) => {
    e.stopPropagation();
    navigator.clipboard.writeText(cert.fingerprintSha256 || '');
    setCopied(true);
    toast.success('SHA-256 fingerprint copied to clipboard');
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div
      className="relative w-full h-[320px] [perspective:1200px] cursor-pointer group"
      onClick={() => setIsFlipped((prev) => !prev)}
    >
      <motion.div
        initial={false}
        animate={{ rotateY: isFlipped ? 180 : 0 }}
        transition={{ duration: 0.6, ease: [0.23, 1, 0.32, 1] }}
        className="w-full h-full relative [transform-style:preserve-3d]"
      >
        {/* FRONT FACE */}
        <div className="absolute inset-0 w-full h-full [backface-visibility:hidden] glass p-6 flex flex-col justify-between border-slate-800/80 hover:border-indigo-500/50 transition-colors shadow-xl">
          {/* Top Row: Icon, Serial, Status */}
          <div>
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 group-hover:scale-105 transition-transform">
                  <Shield className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-semibold text-slate-100 truncate max-w-[200px]">
                    {subjectCn(cert.subjectDn) || 'Unnamed Certificate'}
                  </h3>
                  <p className="text-xs font-mono text-slate-400">
                    SN: {cert.serialNumber ? cert.serialNumber.slice(0, 16) + '...' : '—'}
                  </p>
                </div>
              </div>
              <StatusBadge status={cert.status} />
            </div>

            {/* Issuer */}
            <div className="text-xs text-slate-400 mb-4 bg-slate-900/50 p-2.5 rounded-lg border border-slate-800/60">
              <span className="text-slate-500 block text-[10px] uppercase tracking-wider font-semibold">Issuer</span>
              <span className="font-mono truncate block text-slate-300">{cert.issuerDn || 'KeyBridge Root CA'}</span>
            </div>
          </div>

          {/* Bottom Area: Validity Progress & Dates */}
          <div>
            <div className="flex justify-between items-center text-xs text-slate-400 mb-1.5 font-medium">
              <span>Validity Life</span>
              <span className={remainingDays <= 30 ? 'text-amber-400 font-semibold' : 'text-slate-300'}>
                {remainingDays > 0 ? `${remainingDays} days left` : 'Expired'}
              </span>
            </div>
            <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden mb-3">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  cert.status === 'REVOKED'
                    ? 'bg-rose-500'
                    : remainingDays <= 30
                    ? 'bg-gradient-to-r from-amber-500 to-rose-500'
                    : 'bg-gradient-to-r from-indigo-500 to-violet-500'
                }`}
                style={{ width: `${percentElapsed}%` }}
              />
            </div>

            <div className="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800/80">
              <div className="flex items-center gap-1">
                <Calendar className="w-3 h-3 text-slate-500" />
                <span>Exp: {new Date(cert.validTo).toLocaleDateString()}</span>
              </div>
              <div className="flex items-center gap-1 text-indigo-400 group-hover:translate-x-0.5 transition-transform">
                <RotateCw className="w-3 h-3" />
                <span>Click to flip</span>
              </div>
            </div>
          </div>
        </div>

        {/* BACK FACE */}
        <div className="absolute inset-0 w-full h-full [backface-visibility:hidden] [transform:rotateY(180deg)] glass p-6 flex flex-col justify-between border-indigo-500/40 bg-slate-900/90 shadow-2xl">
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2 text-indigo-400 text-xs font-semibold uppercase tracking-wider">
                <Key className="w-4 h-4" />
                <span>Security Fingerprint</span>
              </div>
              <StatusBadge status={cert.status} size="sm" />
            </div>

            {/* Fingerprint block */}
            <div className="mt-4">
              <label className="text-[10px] uppercase font-semibold text-slate-400 block mb-1">
                SHA-256 Fingerprint
              </label>
              <div className="bg-slate-950/80 p-2.5 rounded-lg border border-slate-800 flex items-center justify-between gap-2">
                <span className="text-[11px] font-mono text-indigo-300 break-all leading-tight select-all">
                  {cert.fingerprintSha256 || 'None'}
                </span>
                <button
                  onClick={copyFingerprint}
                  className="p-1.5 hover:bg-slate-800 text-slate-400 hover:text-white rounded transition-colors shrink-0"
                  title="Copy Fingerprint"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>

            {/* Algorithm info */}
            <div className="grid grid-cols-2 gap-2 mt-3 text-xs">
              <div className="bg-slate-950/40 p-2 rounded border border-slate-800/60">
                <span className="text-[10px] text-slate-500 block uppercase">Algorithm</span>
                <span className="font-mono text-slate-300">RSA 2048-bit</span>
              </div>
              <div className="bg-slate-950/40 p-2 rounded border border-slate-800/60">
                <span className="text-[10px] text-slate-500 block uppercase">Hash</span>
                <span className="font-mono text-slate-300">SHA-256</span>
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2 pt-3 border-t border-slate-800/80" onClick={(e) => e.stopPropagation()}>
            {onDownload && (
              <Button
                variant="secondary"
                size="sm"
                icon={Download}
                onClick={() => onDownload(cert.id)}
                className="flex-1"
              >
                PEM
              </Button>
            )}
            {cert.status !== 'REVOKED' && onRevoke && (
              <Button
                variant="danger"
                size="sm"
                icon={AlertTriangle}
                onClick={() => onRevoke(cert)}
                className="flex-1"
              >
                Revoke
              </Button>
            )}
            <button
              onClick={() => setIsFlipped(false)}
              className="px-2.5 py-1.5 text-xs text-slate-400 hover:text-white transition-colors"
            >
              Flip Back
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
