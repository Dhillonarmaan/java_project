import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  FileCheck2,
  FileText,
  Shield,
  Key,
  Upload,
  ArrowRight,
  Bug,
  RotateCcw,
} from 'lucide-react';
import FileDrop from '../components/ui/FileDrop';
import Button from '../components/ui/Button';
import PipelineSteps from '../components/crypto/PipelineSteps';
import VerifyResultCard from '../components/crypto/VerifyResultCard';
import TamperDemoButton from '../components/crypto/TamperDemoButton';
import DataTable from '../components/ui/DataTable';
import StatusBadge from '../components/ui/StatusBadge';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';
import { subjectCn } from '../lib/dn';

export default function VerifyDocument() {
  const [docFile, setDocFile] = useState(null);
  const [sigFile, setSigFile] = useState(null);
  const [certFile, setCertFile] = useState(null);
  const [existingCerts, setExistingCerts] = useState([]);
  const [selectedCertId, setSelectedCertId] = useState('');
  const [useExistingCert, setUseExistingCert] = useState(true);
  // 'detached': document + .sig + certificate. 'pdf': a PDF that carries its own signature and certificate.
  const [mode, setMode] = useState('detached');

  // Verification logs are ADMIN/AUDITOR only (GET /verifications), so a USER doesn't request or see them.
  const { user } = useAuth();
  const canSeeLogs = user?.role === 'ADMIN' || user?.role === 'AUDITOR';

  // Tamper demo state
  const [isTampered, setIsTampered] = useState(false);
  const [originalFileContent, setOriginalFileContent] = useState(null);

  // Execution state
  const [isVerifying, setIsVerifying] = useState(false);
  const [activeStepIndex, setActiveStepIndex] = useState(-1);
  const [verifyResult, setVerifyResult] = useState(null);
  const [history, setHistory] = useState([]);

  useEffect(() => {
    loadPrerequisites();
  }, []);

  const loadPrerequisites = async () => {
    try {
      const [certRes, histRes] = await Promise.allSettled([
        api.get('/certs'),
        canSeeLogs ? api.get('/verifications') : Promise.resolve({ data: [] }),
      ]);
      const certList = certRes.status === 'fulfilled' ? certRes.value.data || [] : [];
      setExistingCerts(certList);
      if (certList.length > 0 && !selectedCertId) {
        setSelectedCertId(certList[0].id);
      }
      setHistory(histRes.status === 'fulfilled' ? histRes.value.data || [] : []);
    } catch (err) {
      console.error('Failed to load verification prerequisites', err);
    }
  };

  const handleTamperToggle = () => {
    if (!docFile) {
      toast.error('Please upload a document first to simulate tampering');
      return;
    }

    if (!isTampered) {
      // Append bytes to the file as-is (re-reading it as text would corrupt binary files such as PDFs)
      setOriginalFileContent(docFile);
      const tamperedFile = new File([docFile, ' [TAMPERED_BYTE_MODIFICATION]'], docFile.name, { type: docFile.type });
      setDocFile(tamperedFile);
      setIsTampered(true);
      toast.warning('Document payload altered: bytes appended to test tamper detection.');
    } else {
      if (originalFileContent) {
        setDocFile(originalFileContent);
      }
      setIsTampered(false);
      toast.info('Document reverted to original untampered state.');
    }
  };

  const handleExecuteVerification = async (e) => {
    e.preventDefault();
    if (!docFile) {
      toast.error('Please select or upload the document');
      return;
    }
    if (mode === 'detached' && !sigFile) {
      toast.error('Please select or upload the .sig signature file');
      return;
    }

    setIsVerifying(true);
    setVerifyResult(null);
    setActiveStepIndex(0);

    // Visual sequence animation across stages
    setTimeout(() => setActiveStepIndex(1), 300);
    setTimeout(() => setActiveStepIndex(2), 600);
    setTimeout(() => setActiveStepIndex(3), 900);
    setTimeout(() => setActiveStepIndex(4), 1200);

    const formData = new FormData();
    if (mode === 'pdf') {
      formData.append('file', docFile);
    } else {
      formData.append('document', docFile);
      formData.append('signature', sigFile);
    }

    if (mode === 'pdf') {
      // The signer certificate is read from the PDF signature.
    } else if (useExistingCert && selectedCertId) {
      formData.append('certificateId', selectedCertId);
    } else if (certFile) {
      formData.append('certificate', certFile);
    } else {
      toast.error('Please select or upload the signer certificate');
      setIsVerifying(false);
      setActiveStepIndex(-1);
      return;
    }

    try {
      const res = await api.post(mode === 'pdf' ? '/verify/pdf' : '/verify', formData);

      setActiveStepIndex(5);
      setVerifyResult(res.data);
      if (res.data?.result === 'VALID') {
        toast.success('Signature verified! Cryptographic match confirmed.');
      } else {
        toast.warning(`Verification result: ${res.data?.result}`);
      }
      loadPrerequisites();
    } catch (err) {
      setActiveStepIndex(-1);
      toast.error(err.response?.data?.message || 'Verification process encountered an error');
    } finally {
      setIsVerifying(false);
    }
  };

  const handleReset = () => {
    setDocFile(null);
    setSigFile(null);
    setCertFile(null);
    setIsTampered(false);
    setOriginalFileContent(null);
    setVerifyResult(null);
    setActiveStepIndex(-1);
  };

  const historyColumns = [
    {
      header: 'Outcome',
      field: 'result',
      render: (val) => <StatusBadge status={val} size="sm" />,
    },
    {
      header: 'Document',
      field: 'fileName',
      render: (val) => <span className="font-semibold text-slate-100 truncate max-w-[200px] block">{val || '—'}</span>,
    },
    {
      header: 'Verified By',
      field: 'verifiedByName',
      render: (val) => <span className="text-xs text-slate-300 truncate max-w-[160px] block">{val || '—'}</span>,
    },
    {
      header: 'Reason',
      field: 'reason',
      render: (val) => <span className="text-xs text-slate-400 truncate max-w-[220px] block">{val || '—'}</span>,
    },
    {
      header: 'Verified At',
      field: 'verifiedAt',
      render: (val) => new Date(val).toLocaleString(),
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-100">
          5-Stage Cryptographic Verification Engine
        </h2>
        <p className="text-sm text-slate-400 mt-1">
          Rigorous pipeline testing certificate format, Root CA chain-of-trust, CRL revocation status, time validity, and RSA mathematical signature match.
        </p>
      </div>

      {/* Main Verification Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Verification Inputs Form (7 Cols) */}
        <div className="lg:col-span-7 glass p-6 space-y-6">
          <form onSubmit={handleExecuteVerification} className="space-y-6">
            <div className="flex items-center gap-1 bg-slate-900/60 p-1 rounded-xl border border-slate-800 w-fit text-xs">
              {[
                ['detached', 'Document + .sig'],
                ['pdf', 'Signed PDF'],
              ].map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => {
                    setMode(value);
                    setVerifyResult(null);
                  }}
                  className={`px-3 py-1.5 rounded-lg transition-colors ${
                    mode === value ? 'bg-indigo-600 text-white font-semibold' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            {/* 1. Document Upload */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                {mode === 'pdf' ? '1. Signed PDF' : '1. Original Document'}
              </label>
              <FileDrop
                onFileSelect={(f) => {
                  setDocFile(f);
                  setIsTampered(false);
                  setOriginalFileContent(null);
                }}
                accept={mode === 'pdf' ? '.pdf,application/pdf' : '*/*'}
                maxSizeMb={10}
                helperText={
                  mode === 'pdf'
                    ? 'Upload a PDF signed by KeyBridge; the signature and signer certificate are inside it'
                    : 'Upload original file that was signed'
                }
              />
            </div>

            {/* 2. Interactive Tamper Demonstration */}
            {docFile && (
              <TamperDemoButton
                originalFile={docFile}
                isTampered={isTampered}
                onTamperToggle={handleTamperToggle}
              />
            )}

            {mode === 'detached' && (
            <>
            {/* 3. Signature File Upload */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                2. Digital Signature File (.sig)
              </label>
              <FileDrop
                onFileSelect={(f) => setSigFile(f)}
                accept=".sig,.txt"
                maxSizeMb={2}
                helperText="Upload the .sig file generated during signing"
              />
            </div>

            {/* 4. Signer Certificate */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  3. Signer Certificate
                </label>
                <div className="flex items-center gap-2 text-xs">
                  <button
                    type="button"
                    onClick={() => setUseExistingCert(true)}
                    className={`px-2.5 py-1 rounded-lg transition-colors ${
                      useExistingCert ? 'bg-indigo-600 text-white font-semibold' : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Select from Registry
                  </button>
                  <button
                    type="button"
                    onClick={() => setUseExistingCert(false)}
                    className={`px-2.5 py-1 rounded-lg transition-colors ${
                      !useExistingCert ? 'bg-indigo-600 text-white font-semibold' : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Upload PEM
                  </button>
                </div>
              </div>

              {useExistingCert ? (
                <select
                  value={selectedCertId}
                  onChange={(e) => setSelectedCertId(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-900/80 border border-slate-800 rounded-xl text-xs text-slate-100 focus:outline-none focus:border-indigo-500/80"
                >
                  {existingCerts.map((c) => (
                    <option key={c.id} value={c.id}>
                      {subjectCn(c.subjectDn || c.subject)} (SN: {(c.serialNumber || c.serialHex || '').slice(0, 14)}...) - {c.status}
                    </option>
                  ))}
                </select>
              ) : (
                <FileDrop
                  onFileSelect={(f) => setCertFile(f)}
                  accept=".pem,.cer,.crt"
                  maxSizeMb={2}
                  helperText="Upload .pem or .cer public certificate"
                />
              )}
            </div>
            </>
            )}

            <div className="flex items-center gap-3">
              <Button
                type="submit"
                variant="primary"
                size="lg"
                icon={FileCheck2}
                loading={isVerifying}
                disabled={!docFile || (mode === 'detached' && !sigFile)}
                className="flex-1"
              >
                Execute 5-Stage Verification
              </Button>
              {verifyResult && (
                <Button variant="secondary" size="lg" icon={RotateCcw} onClick={handleReset}>
                  Reset
                </Button>
              )}
            </div>
          </form>
        </div>

        {/* Verification Pipeline & Outcome (5 Cols) */}
        <div className="lg:col-span-5 space-y-6">
          <div className="glass p-6 space-y-4">
            <h3 className="text-base font-semibold text-slate-100">Verification Engine Stages</h3>
            <PipelineSteps
              steps={verifyResult?.steps || []}
              activeIndex={activeStepIndex}
            />
          </div>
        </div>
      </div>

      {/* Result Card on Completion */}
      {verifyResult && (
        <motion.div
          initial={{ opacity: 0, y: 15 }}
          animate={{ opacity: 1, y: 0 }}
          className="w-full"
        >
          <VerifyResultCard result={verifyResult} />
        </motion.div>
      )}

      {/* Verification History Table */}
      {canSeeLogs && (
      <div className="space-y-4">
        <h3 className="text-lg font-bold text-slate-100">Recent Verification Logs</h3>
        <DataTable
          columns={historyColumns}
          data={history}
          searchKeys={['fileName', 'result', 'verifiedByName', 'reason']}
          searchPlaceholder="Search verification logs..."
        />
      </div>
      )}
    </div>
  );
}
