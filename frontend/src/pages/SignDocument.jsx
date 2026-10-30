import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  FileSignature,
  Shield,
  KeyRound,
  Download,
  CheckCircle2,
  Lock,
  ArrowRight,
  FileText,
  RotateCcw,
} from 'lucide-react';
import FileDrop from '../components/ui/FileDrop';
import Button from '../components/ui/Button';
import SigningProgress from '../components/crypto/SigningProgress';
import DataTable from '../components/ui/DataTable';
import api from '../api/axios';
import { toast } from 'sonner';

export default function SignDocument() {
  const [certs, setCerts] = useState([]);
  const [selectedCertId, setSelectedCertId] = useState('');
  const [file, setFile] = useState(null);
  // PKCS#11 token PIN: typed per request, sent once, never stored. .p12 keys don't need one.
  const [pin, setPin] = useState('');
  const [history, setHistory] = useState([]);

  // Pipeline execution state
  const [isSigning, setIsSigning] = useState(false);
  const [pipelineStep, setPipelineStep] = useState(1);
  const [signComplete, setSignComplete] = useState(false);
  const [signResult, setSignResult] = useState(null);

  const selectedCert = certs.find((c) => String(c.id) === String(selectedCertId));
  const needsPin = selectedCert?.keyStorage === 'PKCS11';

  const fetchCertsAndHistory = async () => {
    try {
      const [certRes, histRes] = await Promise.all([
        api.get('/certs'),
        api.get('/signed-documents'),
      ]);
      const allCerts = certRes.data || [];
      const usableCerts = allCerts.filter(
        (c) => (c.status === 'VALID' || c.status === 'EXPIRING') && (c.hasPrivateKey !== false)
      );
      // Fallback to all valid/expiring certs if none explicitly have hasPrivateKey flag
      const activeCerts = usableCerts.length > 0
        ? usableCerts
        : allCerts.filter((c) => c.status === 'VALID' || c.status === 'EXPIRING');

      setCerts(activeCerts);
      if (activeCerts.length > 0 && !selectedCertId) {
        setSelectedCertId(activeCerts[0].id);
      }
      setHistory(histRes.data || []);
    } catch (err) {
      console.error('Failed to fetch signing prerequisites', err);
    }
  };

  useEffect(() => {
    fetchCertsAndHistory();
  }, []);

  const handleExecuteSign = async (e) => {
    e.preventDefault();
    if (!file) {
      toast.error('Please upload a document to sign');
      return;
    }
    if (!selectedCertId) {
      toast.error('Please select an active signing certificate');
      return;
    }

    setIsSigning(true);
    setSignComplete(false);
    setPipelineStep(1);

    // Stepper visual animation sequence
    setTimeout(() => setPipelineStep(2), 600);
    setTimeout(() => setPipelineStep(3), 1200);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('certificateId', selectedCertId);
    formData.append('certId', selectedCertId);
    if (needsPin) {
      formData.append('pin', pin);
    }
    setPin(''); // don't keep the PIN in page state after this request

    try {
      const res = await api.post('/sign', formData);

      setPipelineStep(4);
      setSignResult(res.data);
      setSignComplete(true);
      toast.success('Digital signature successfully generated with RSA-2048 & SHA-256!');
      fetchCertsAndHistory();
    } catch (err) {
      setIsSigning(false);
      setPipelineStep(1);
      toast.error(err.response?.data?.message || 'Digital signature generation failed');
    }
  };

  const downloadSignatureFile = () => {
    const signatureText = signResult?.signatureB64 || signResult?.signatureBase64;
    if (!signatureText) {
      toast.error('No signature data available to download');
      return;
    }
    const blob = new Blob([signatureText], { type: 'text/plain;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const docName = file?.name || signResult?.documentName || 'document';
    const baseName = docName.replace(/\.[^/.]+$/, "");
    link.setAttribute('download', `${baseName}.sig`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    toast.success(`Downloaded ${baseName}.sig`);
  };

  const handleReset = () => {
    setFile(null);
    setIsSigning(false);
    setSignComplete(false);
    setSignResult(null);
  };

  const historyColumns = [
    {
      header: 'Document Name',
      field: 'fileName',
      render: (val, row) => (
        <div className="flex items-center gap-2">
          <FileText className="w-4 h-4 text-indigo-400" />
          <span className="font-semibold text-slate-100">{val || row?.documentName || 'document'}</span>
        </div>
      ),
    },
    {
      header: 'SHA-256 Digest',
      field: 'docHash',
      render: (val, row) => (
        <span className="font-mono text-xs text-slate-400 truncate max-w-[160px] block">
          {val || row?.documentHash || '—'}
        </span>
      ),
    },
    {
      header: 'Signing Certificate',
      field: 'certSubject',
      render: (val, row) => (
        <span className="text-xs text-slate-300 truncate max-w-[180px] block">
          {val || row?.certificateSubject || 'Certificate'}
        </span>
      ),
    },
    {
      header: 'Signed At',
      field: 'signedAt',
      render: (val) => (val ? new Date(val).toLocaleString() : '—'),
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-100">
          RSA Digital Signature Studio
        </h2>
        <p className="text-sm text-slate-400 mt-1">
          Compute document SHA-256 digests and sign with private keys secured in hardware or PKCS#12 tokens.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left Form (7 Cols) */}
        <div className="lg:col-span-7 glass p-6 space-y-6">
          {!isSigning ? (
            <form onSubmit={handleExecuteSign} className="space-y-6">
              {/* Step 1: Certificate Selection */}
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                  1. Select Signing Certificate
                </label>
                {certs.length === 0 ? (
                  <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-300">
                    No active certificates available. Please issue or upload an active certificate first.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-2.5 max-h-48 overflow-y-auto pr-1">
                    {certs.map((c) => {
                      const isSelected = String(c.id) === String(selectedCertId);
                      return (
                        <div
                          key={c.id}
                          onClick={() => setSelectedCertId(c.id)}
                          className={`p-3 rounded-xl border cursor-pointer transition-all flex items-center justify-between ${
                            isSelected
                              ? 'bg-indigo-600/20 border-indigo-500 shadow-md shadow-indigo-500/10'
                              : 'bg-slate-900/60 border-slate-800 hover:border-slate-700'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <Shield className={`w-4 h-4 ${isSelected ? 'text-indigo-400' : 'text-slate-500'}`} />
                            <div>
                              <p className="text-xs font-semibold text-slate-200">{c.subjectDn || c.subject}</p>
                              <p className="text-[10px] text-slate-400 font-mono">
                                SN: {(c.serialNumber || c.serialHex || '').slice(0, 16)}...
                              </p>
                            </div>
                          </div>
                          {isSelected && <CheckCircle2 className="w-4 h-4 text-indigo-400 shrink-0" />}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Step 2: Upload File */}
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                  2. Document Payload
                </label>
                <FileDrop
                  onFileSelect={(f) => setFile(f)}
                  accept="*/*"
                  maxSizeMb={10}
                  helperText="Upload any PDF, XML, JSON, or text file to generate digital signature"
                />
              </div>

              {/* Step 3: token PIN, only for keys on a PKCS#11 device */}
              {needsPin && (
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
                  3. Token PIN
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="password"
                    required
                    autoComplete="off"
                    value={pin}
                    onChange={(e) => setPin(e.target.value)}
                    placeholder="Enter your token PIN"
                    className="w-full pl-10 pr-4 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
                  />
                </div>
                <span className="text-[10px] text-slate-500 mt-1 block">
                  Unlocks the key on your hardware token for this signature only. It is not stored.
                </span>
              </div>
              )}

              <Button
                type="submit"
                variant="primary"
                size="lg"
                icon={FileSignature}
                disabled={!file || !selectedCertId || (needsPin && !pin)}
                className="w-full"
              >
                Execute Digital Signature
              </Button>
            </form>
          ) : (
            /* Progress & Completed Result View */
            <div className="space-y-6">
              <SigningProgress
                activeStep={pipelineStep}
                docHash={signResult?.docHash || signResult?.documentHash}
                isComplete={signComplete}
              />

              {signComplete && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="bg-emerald-950/20 border border-emerald-500/40 p-5 rounded-2xl space-y-4"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30">
                      <CheckCircle2 className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-emerald-200">Signature Envelope Generated</h4>
                      <p className="text-xs text-slate-400">
                        Signature is bound to document bytes and signer public key.
                      </p>
                    </div>
                  </div>

                  <div className="bg-slate-950/80 p-3 rounded-xl border border-slate-800">
                    <span className="text-[10px] uppercase font-semibold text-slate-500 block mb-1">
                      Base64 Signature Output
                    </span>
                    <p className="font-mono text-xs text-indigo-300 break-all max-h-24 overflow-y-auto">
                      {signResult?.signatureB64 || signResult?.signatureBase64}
                    </p>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <Button
                      variant="success"
                      size="md"
                      icon={Download}
                      onClick={downloadSignatureFile}
                      className="flex-1"
                    >
                      Download .sig File
                    </Button>
                    <Button
                      variant="secondary"
                      size="md"
                      icon={RotateCcw}
                      onClick={handleReset}
                    >
                      Sign Another Document
                    </Button>
                  </div>
                </motion.div>
              )}
            </div>
          )}
        </div>

        {/* Right Info Card (5 Cols) */}
        <div className="lg:col-span-5 space-y-6">
          <div className="glass p-6 space-y-4 border-slate-800">
            <div className="flex items-center gap-3 text-indigo-400">
              <KeyRound className="w-5 h-5" />
              <h3 className="text-base font-semibold text-slate-100">Zero Private Key Exposure</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              In accordance with PKI enterprise standards, KeyBridge never stores unencrypted private keys in any database table. Private keys are accessed strictly via encrypted PKCS#12 `.p12` keystores loaded in memory only during digital signature computation.
            </p>

            <div className="border-t border-slate-800 pt-4 space-y-2 text-xs">
              <div className="flex justify-between text-slate-400">
                <span>Hashing Algorithm:</span>
                <span className="font-mono text-slate-200">SHA-256 (FIPS 180-4)</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Asymmetric Cipher:</span>
                <span className="font-mono text-slate-200">RSA 2048-bit (PKCS#1 v1.5)</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Certificate Authority:</span>
                <span className="font-mono text-indigo-400">KeyBridge Root CA</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* History Table */}
      <div className="space-y-4">
        <h3 className="text-lg font-bold text-slate-100">Document Signing History</h3>
        <DataTable
          columns={historyColumns}
          data={history}
          searchKeys={['fileName', 'documentName', 'docHash', 'certSubject']}
          searchPlaceholder="Search signed documents..."
        />
      </div>
    </div>
  );
}
