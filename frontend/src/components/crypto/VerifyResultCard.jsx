import React, { useEffect } from 'react';
import { motion } from 'framer-motion';
import { ShieldCheck, ShieldAlert, ShieldX, Clock, AlertTriangle, HelpCircle } from 'lucide-react';
import confetti from 'canvas-confetti';
import StatusBadge from '../ui/StatusBadge';

export default function VerifyResultCard({ result }) {
  const isValid = result?.result === 'VALID';
  const isTampered = result?.result === 'TAMPERED';
  const isRevoked = result?.result === 'REVOKED';
  const isExpired = result?.result === 'EXPIRED';

  useEffect(() => {
    if (isValid) {
      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 },
        colors: ['#10b981', '#6366f1', '#a855f7'],
      });
    }
  }, [isValid]);

  const config = {
    VALID: {
      color: 'emerald',
      bgGrad: 'from-emerald-950/40 via-slate-900 to-slate-950',
      border: 'border-emerald-500/40',
      glow: 'shadow-emerald-500/10',
      title: 'Digital Signature is Cryptographically VALID',
      desc: 'The document integrity is verified, and the certificate is fully trusted by KeyBridge Root CA.',
      Icon: ShieldCheck,
      iconColor: 'text-emerald-400',
    },
    TAMPERED: {
      color: 'rose',
      bgGrad: 'from-rose-950/40 via-slate-900 to-slate-950',
      border: 'border-rose-500/50',
      glow: 'shadow-rose-500/15',
      title: 'DOCUMENT INTEGRITY COMPROMISED (TAMPERED)',
      desc: 'The document payload has been modified after it was signed! Cryptographic hash does not match.',
      Icon: ShieldAlert,
      iconColor: 'text-rose-400',
    },
    REVOKED: {
      color: 'rose',
      bgGrad: 'from-rose-950/40 via-slate-900 to-slate-950',
      border: 'border-rose-500/50',
      glow: 'shadow-rose-500/15',
      title: 'Signer Certificate Has Been REVOKED',
      desc: 'This certificate was revoked in the KeyBridge CRL registry and is no longer trustworthy.',
      Icon: ShieldX,
      iconColor: 'text-rose-400',
    },
    EXPIRED: {
      color: 'amber',
      bgGrad: 'from-amber-950/40 via-slate-900 to-slate-950',
      border: 'border-amber-500/50',
      glow: 'shadow-amber-500/15',
      title: 'Signer Certificate is EXPIRED',
      desc: 'The certificate validity expiration threshold (notAfter) has passed.',
      Icon: Clock,
      iconColor: 'text-amber-400',
    },
    UNTRUSTED: {
      color: 'amber',
      bgGrad: 'from-amber-950/40 via-slate-900 to-slate-950',
      border: 'border-amber-500/50',
      glow: 'shadow-amber-500/15',
      title: 'Certificate Chain is UNTRUSTED',
      desc: 'The certificate was not issued or signed by the KeyBridge Root CA authority.',
      Icon: AlertTriangle,
      iconColor: 'text-amber-400',
    },
  }[result?.result] || {
    color: 'slate',
    bgGrad: 'from-slate-900 via-slate-900 to-slate-950',
    border: 'border-slate-700',
    glow: 'shadow-slate-500/10',
    title: result?.result || 'Verification Completed',
    desc: result?.reason || 'Verification check process has concluded.',
    Icon: HelpCircle,
    iconColor: 'text-slate-400',
  };

  const IconComponent = config.Icon;

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.4 }}
      className={`relative overflow-hidden rounded-2xl border p-6 bg-gradient-to-br ${config.bgGrad} ${config.border} shadow-2xl ${config.glow}`}
    >
      <div className="flex flex-col md:flex-row items-center gap-6">
        {/* Animated SVG Shield drawing */}
        <div className="relative shrink-0 flex items-center justify-center">
          <svg className="w-24 h-24" viewBox="0 0 100 100" fill="none">
            <motion.path
              d="M50 10 L85 25 L85 55 C85 75 50 90 50 90 C50 90 15 75 15 55 L15 25 Z"
              stroke={
                isValid ? '#10b981' : isTampered || isRevoked ? '#f43f5e' : isExpired ? '#f59e0b' : '#6366f1'
              }
              strokeWidth="4"
              strokeLinecap="round"
              strokeLinejoin="round"
              initial={{ pathLength: 0 }}
              animate={{ pathLength: 1 }}
              transition={{ duration: 1.2, ease: 'easeInOut' }}
            />
          </svg>
          <div className="absolute inset-0 flex items-center justify-center">
            <IconComponent className={`w-10 h-10 ${config.iconColor}`} />
          </div>
        </div>

        {/* Text Info */}
        <div className="flex-1 text-center md:text-left space-y-2">
          <div className="flex flex-wrap items-center justify-center md:justify-start gap-3">
            <h3 className="text-xl font-bold text-slate-100">{config.title}</h3>
            <StatusBadge status={result?.result} />
          </div>
          <p className="text-sm text-slate-400 max-w-xl">{config.desc}</p>
        </div>
      </div>

      {/* Meta Grid */}
      <div className="mt-6 pt-6 border-t border-slate-800/80 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-xs">
        <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800/60">
          <span className="text-[10px] uppercase font-semibold text-slate-500 block">Signer Subject</span>
          <span className="font-semibold text-slate-200 truncate block mt-0.5">
            {result?.signerSubject || 'Unknown Subject'}
          </span>
        </div>
        <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800/60">
          <span className="text-[10px] uppercase font-semibold text-slate-500 block">Issuer Authority</span>
          <span className="font-semibold text-slate-200 truncate block mt-0.5">
            {result?.issuerSubject || 'KeyBridge Root CA'}
          </span>
        </div>
        <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800/60">
          <span className="text-[10px] uppercase font-semibold text-slate-500 block">Verification Timestamp</span>
          <span className="font-mono text-slate-300 block mt-0.5">
            {result?.verifiedAt ? new Date(result.verifiedAt).toLocaleString() : new Date().toLocaleString()}
          </span>
        </div>
        <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800/60">
          <span className="text-[10px] uppercase font-semibold text-slate-500 block">Certificate Serial</span>
          <span className="font-mono text-indigo-300 truncate block mt-0.5">
            {result?.certificateSerialNumber || '—'}
          </span>
        </div>
      </div>

      {result?.documentHash && (
        <div className="mt-3 bg-slate-950/80 p-3 rounded-xl border border-slate-800/80 flex items-center justify-between gap-4">
          <div className="min-w-0">
            <span className="text-[10px] uppercase font-semibold text-slate-500 block">Verified SHA-256 Digest</span>
            <span className="font-mono text-xs text-indigo-300 break-all">{result.documentHash}</span>
          </div>
        </div>
      )}
    </motion.div>
  );
}
