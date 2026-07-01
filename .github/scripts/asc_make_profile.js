// Genera (o regenera) un perfil de aprovisionamiento App Store para el bundle
// id dado, firmado con el certificado de DISTRIBUCIÓN del equipo, y lo instala
// en ~/Library/MobileDevice/Provisioning Profiles/. Pensado para CI: evita la
// firma automática (que crea un certificado nuevo en cada build efímero y agota
// el límite de la cuenta). Requiere env: KEY_ID, ISSUER_ID, API_KEY_PATH,
// BUNDLE_ID, PROFILE_NAME.
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const KEY_ID = process.env.KEY_ID;
const ISSUER_ID = process.env.ISSUER_ID;
const API_KEY_PATH = process.env.API_KEY_PATH;
const BUNDLE_ID = process.env.BUNDLE_ID;
const PROFILE_NAME = process.env.PROFILE_NAME;
const privateKey = fs.readFileSync(API_KEY_PATH, 'utf8');

const b64url = b => Buffer.from(b).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
function jwt() {
  const now = Math.floor(Date.now() / 1000);
  const si = b64url(JSON.stringify({ alg: 'ES256', kid: KEY_ID, typ: 'JWT' })) + '.' +
             b64url(JSON.stringify({ iss: ISSUER_ID, iat: now, exp: now + 600, aud: 'appstoreconnect-v1' }));
  const sig = crypto.sign('SHA256', Buffer.from(si), { key: privateKey, dsaEncoding: 'ieee-p1363' });
  return si + '.' + b64url(sig);
}
async function api(method, p, body) {
  const res = await fetch('https://api.appstoreconnect.apple.com' + p, {
    method,
    headers: { Authorization: 'Bearer ' + jwt(), 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const txt = await res.text();
  let json; try { json = txt ? JSON.parse(txt) : {}; } catch { json = { raw: txt }; }
  return { status: res.status, json };
}

(async () => {
  // 1. Certificado de distribución del equipo.
  const certs = await api('GET', '/v1/certificates?limit=200');
  const dist = (certs.json.data || []).find(c => (c.attributes || {}).certificateType === 'DISTRIBUTION');
  if (!dist) throw new Error('No hay certificado de DISTRIBUTION en la cuenta.');
  console.log('Cert distribución:', dist.id, (dist.attributes || {}).name);

  // 2. Id interno del bundle id.
  const bids = await api('GET', '/v1/bundleIds?limit=200');
  const bid = (bids.json.data || []).find(b => (b.attributes || {}).identifier === BUNDLE_ID);
  if (!bid) throw new Error('No existe el bundle id ' + BUNDLE_ID);
  console.log('Bundle id interno:', bid.id);

  // 3. Borrar perfiles previos con el mismo nombre (y los INVALID del bundle) para
  //    que no se acumulen y para forzar uno fresco/válido.
  const profs = await api('GET', '/v1/profiles?limit=200&include=bundleId');
  for (const p of (profs.json.data || [])) {
    const a = p.attributes || {};
    const sameName = a.name === PROFILE_NAME;
    if (sameName) {
      const del = await api('DELETE', '/v1/profiles/' + p.id);
      console.log('Borrado perfil previo', p.id, '->', del.status);
    }
  }

  // 4. Crear el perfil App Store nuevo.
  const create = await api('POST', '/v1/profiles', {
    data: {
      type: 'profiles',
      attributes: { name: PROFILE_NAME, profileType: 'IOS_APP_STORE' },
      relationships: {
        bundleId: { data: { type: 'bundleIds', id: bid.id } },
        certificates: { data: [{ type: 'certificates', id: dist.id }] },
      },
    },
  });
  if (create.status !== 201) {
    console.error('Fallo al crear perfil:', create.status, JSON.stringify(create.json).slice(0, 1500));
    process.exit(1);
  }
  const attr = create.json.data.attributes;
  const uuid = attr.uuid;
  console.log('Perfil creado:', PROFILE_NAME, 'uuid=', uuid);

  // 5. Instalarlo donde Xcode lo busca.
  const dir = path.join(os.homedir(), 'Library', 'MobileDevice', 'Provisioning Profiles');
  fs.mkdirSync(dir, { recursive: true });
  const dest = path.join(dir, uuid + '.mobileprovision');
  fs.writeFileSync(dest, Buffer.from(attr.profileContent, 'base64'));
  console.log('Perfil instalado en:', dest);
})().catch(e => { console.error('ERROR:', e.message); process.exit(1); });
