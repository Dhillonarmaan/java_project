// Subject DNs come back as "O=...,E=...,CN=Alice"; the CN is the readable name.
export function subjectCn(dn) {
  if (!dn) return '';
  const cn = dn.split(',').map((p) => p.trim()).find((p) => p.toUpperCase().startsWith('CN='));
  return cn ? cn.slice(3) : dn;
}
