/**
 * Cloudflare Worker: getUserRole
 * 
 * Verifies Google Sign-In ID tokens and returns user role from TeamPulse_Users_Registry sheet.
 * 
 * Security:
 * - Verifies Google ID tokens against accounts.google.com issuer
 * - Uses service account (stored as Cloudflare Secret) for Sheets access
 * - Users can only lookup their own role (email matching enforced)
 * 
 * No billing account required - Cloudflare Workers free tier: 100K requests/day
 */

// Configuration
const CONFIG = {
  GOOGLE_JWK_URL: 'https://www.googleapis.com/oauth2/v3/certs',
  EXPECTED_ISSUER: ['accounts.google.com', 'https://accounts.google.com'],
  EXPECTED_AUDIENCE: '973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com',
  USERS_REGISTRY_SPREADSHEET_ID: '1MzysETdhkqVYxPvlehkTxEXd1qG8y85cqmB6g2qmzK0',
  USERS_REGISTRY_RANGE: 'Users!A:F',
  SHEETS_API_BASE: 'https://sheets.googleapis.com/v4/spreadsheets',
  GOOGLE_TOKEN_URI: 'https://oauth2.googleapis.com/token',
};

// Cache for Google's public keys (rotated periodically)
let jwkCache = null;
let jwkCacheExpiry = 0;

// Cache for service account access token
let accessTokenCache = null;
let accessTokenExpiry = 0;

/**
 * Main request handler
 */
export default {
  async fetch(request, env, ctx) {
    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
        },
      });
    }

    if (request.method !== 'POST') {
      return jsonResponse({ error: 'Method not allowed. Use POST.' }, 405);
    }

    try {
      // 1. Extract ID token from Authorization header
      const authHeader = request.headers.get('Authorization');
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return jsonResponse({ error: 'Missing or invalid Authorization header' }, 401);
      }

      const idToken = authHeader.substring(7);

      // 2. Extract requested email from body
      const body = await request.json();
      const { email } = body;
      if (!email) {
        return jsonResponse({ error: 'Missing email in request body' }, 400);
      }

      // 3. Verify Google ID token
      const tokenPayload = await verifyGoogleIdToken(idToken);
      if (!tokenPayload) {
        return jsonResponse({ error: 'Invalid or expired ID token' }, 401);
      }

      const tokenEmail = tokenPayload.email;
      if (!tokenEmail) {
        return jsonResponse({ error: 'ID token does not contain email' }, 401);
      }

      // 4. Verify token email matches requested email (users can only lookup their own role)
      if (tokenEmail.toLowerCase() !== email.toLowerCase()) {
        return jsonResponse({ error: 'Token email does not match requested email' }, 403);
      }

      // 5. Get service account access token
      const accessToken = await getServiceAccountAccessToken(env);
      if (!accessToken) {
        return jsonResponse({ error: 'Failed to authenticate with Google Sheets API' }, 500);
      }

      // 6. Query Users Registry sheet
      const sheetsUrl = `${CONFIG.SHEETS_API_BASE}/${CONFIG.USERS_REGISTRY_SPREADSHEET_ID}/values/${CONFIG.USERS_REGISTRY_RANGE}`;
      const sheetsResponse = await fetch(sheetsUrl, {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
        },
      });

      if (!sheetsResponse.ok) {
        console.error('Sheets API error:', await sheetsResponse.text());
        return jsonResponse({ error: 'Failed to read Users Registry sheet' }, 500);
      }

      const sheetsData = await sheetsResponse.json();
      const rows = sheetsData.values;

      if (!rows || rows.length <= 1) {
        return jsonResponse({ error: 'Users Registry sheet is empty or malformed' }, 404);
      }

      // 7. Find user row (skip header at index 0)
      const userRow = rows.slice(1).find(row => {
        return row[0] && row[0].toLowerCase() === email.toLowerCase();
      });

      if (!userRow) {
        return jsonResponse({
          error: 'User not found in Users Registry. Please contact your teacher to be enrolled.',
        }, 404);
      }

      // 8. Parse and validate role (column C, index 2)
      const roleString = userRow[2] ? userRow[2].toUpperCase() : null;
      if (roleString !== 'TEACHER' && roleString !== 'STUDENT') {
        return jsonResponse({
          error: `Invalid role in Users Registry: ${roleString}. Please contact support.`,
        }, 400);
      }

      // 9. Return role and metadata
      return jsonResponse({
        role: roleString,
        displayName: userRow[1] || email,
        enrolledAt: userRow[3] || null,
        status: userRow[4] || 'ACTIVE',
      }, 200);

    } catch (err) {
      console.error('Function error:', err);
      return jsonResponse({
        error: 'Internal server error during role lookup',
        details: err.message,
      }, 500);
    }
  },
};

/**
 * Verify Google ID token using Google's public keys
 */
async function verifyGoogleIdToken(idToken) {
  try {
    // Parse JWT (3 parts: header.payload.signature)
    const parts = idToken.split('.');
    if (parts.length !== 3) {
      return null;
    }

    // Decode header and payload
    const header = JSON.parse(base64urlDecode(parts[0]));
    const payload = JSON.parse(base64urlDecode(parts[1]));

    // Validate algorithm
    if (header.alg !== 'RS256' || header.typ !== 'JWT') {
      console.error('Invalid JWT algorithm or type');
      return null;
    }

    // Validate claims
    const now = Math.floor(Date.now() / 1000);

    // Check issuer
    if (!CONFIG.EXPECTED_ISSUER.includes(payload.iss)) {
      console.error('Invalid issuer:', payload.iss);
      return null;
    }

    // Check audience
    if (payload.aud !== CONFIG.EXPECTED_AUDIENCE) {
      console.error('Invalid audience:', payload.aud);
      return null;
    }

    // Check expiration
    if (payload.exp <= now) {
      console.error('Token expired');
      return null;
    }

    // Check issued-at time (not in future)
    if (payload.iat > now + 60) { // Allow 60s clock skew
      console.error('Token issued in the future');
      return null;
    }

    // Fetch Google's public keys
    const jwks = await fetchGoogleJWKs();
    const jwk = jwks[header.kid];
    if (!jwk) {
      console.error('Key ID not found in JWK set');
      return null;
    }

    // Verify signature
    const signatureValid = await verifyRS256Signature(
      `${parts[0]}.${parts[1]}`,
      base64urlToArrayBuffer(parts[2]),
      jwk
    );

    if (!signatureValid) {
      console.error('Signature verification failed');
      return null;
    }

    return payload;
  } catch (err) {
    console.error('Token verification error:', err);
    return null;
  }
}

/**
 * Fetch and cache Google's public JWKs
 */
async function fetchGoogleJWKs() {
  const now = Date.now();

  // Return cached keys if still valid
  if (jwkCache && jwkCacheExpiry > now) {
    return jwkCache;
  }

  // Fetch fresh keys
  const response = await fetch(CONFIG.GOOGLE_JWK_URL);
  if (!response.ok) {
    throw new Error('Failed to fetch Google JWKs');
  }

  const data = await response.json();

  // Build lookup map by kid
  const jwks = {};
  data.keys.forEach(key => {
    jwks[key.kid] = key;
  });

  // Cache for 1 hour (Google's keys rotate periodically)
  jwkCache = jwks;
  jwkCacheExpiry = now + 3600000; // 1 hour

  return jwks;
}

/**
 * Verify RS256 signature using Web Crypto API
 */
async function verifyRS256Signature(data, signature, jwk) {
  try {
    const key = await crypto.subtle.importKey(
      'jwk',
      jwk,
      {
        name: 'RSASSA-PKCS1-v1_5',
        hash: { name: 'SHA-256' },
      },
      false,
      ['verify']
    );

    const dataBuffer = new TextEncoder().encode(data);
    return await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5',
      key,
      signature,
      dataBuffer
    );
  } catch (err) {
    console.error('Signature verification error:', err);
    return false;
  }
}

/**
 * Get service account access token for Sheets API
 */
async function getServiceAccountAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);

  // Return cached token if still valid (with 60s buffer)
  if (accessTokenCache && accessTokenExpiry > now + 60) {
    return accessTokenCache;
  }

  try {
    // Decode service account JSON from base64-encoded secret
    const serviceAccountJson = env.GOOGLE_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountJson) {
      throw new Error('GOOGLE_SERVICE_ACCOUNT_JSON secret not configured');
    }

    const serviceAccount = JSON.parse(atob(serviceAccountJson));

    // Create JWT for service account
    const iat = now;
    const exp = iat + 3600; // 1 hour max

    const header = {
      alg: 'RS256',
      typ: 'JWT',
      kid: serviceAccount.private_key_id,
    };

    const payload = {
      iss: serviceAccount.client_email,
      sub: serviceAccount.client_email,
      aud: CONFIG.GOOGLE_TOKEN_URI,
      iat: iat,
      exp: exp,
      scope: 'https://www.googleapis.com/auth/spreadsheets.readonly',
    };

    // Sign JWT
    const signedJWT = await signServiceAccountJWT(header, payload, serviceAccount.private_key);

    // Exchange JWT for access token
    const formData = new FormData();
    formData.append('grant_type', 'urn:ietf:params:oauth:grant-type:jwt-bearer');
    formData.append('assertion', signedJWT);

    const tokenResponse = await fetch(CONFIG.GOOGLE_TOKEN_URI, {
      method: 'POST',
      body: formData,
    });

    if (!tokenResponse.ok) {
      const errorText = await tokenResponse.text();
      console.error('Token exchange failed:', errorText);
      throw new Error('Failed to exchange JWT for access token');
    }

    const tokenData = await tokenResponse.json();
    accessTokenCache = tokenData.access_token;
    accessTokenExpiry = exp;

    return accessTokenCache;
  } catch (err) {
    console.error('Service account auth error:', err);
    return null;
  }
}

/**
 * Sign JWT using service account private key (RS256)
 */
async function signServiceAccountJWT(header, payload, pemPrivateKey) {
  // Encode header and payload
  const headerB64 = base64urlEncode(JSON.stringify(header));
  const payloadB64 = base64urlEncode(JSON.stringify(payload));
  const dataToSign = `${headerB64}.${payloadB64}`;

  // Parse PEM private key
  const privateKey = await parsePEMPrivateKey(pemPrivateKey);

  // Sign with RS256
  const dataBuffer = new TextEncoder().encode(dataToSign);
  const signatureBuffer = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    privateKey,
    dataBuffer
  );

  const signatureB64 = base64urlEncode(signatureBuffer);
  return `${dataToSign}.${signatureB64}`;
}

/**
 * Parse PEM-encoded RSA private key to CryptoKey
 */
async function parsePEMPrivateKey(pem) {
  // Strip PEM header/footer and newlines
  const pemHeader = '-----BEGIN PRIVATE KEY-----';
  const pemFooter = '-----END PRIVATE KEY-----';
  const pemContents = pem
    .replace(/\n/g, '')
    .replace(pemHeader, '')
    .replace(pemFooter, '');

  // Decode base64 to ArrayBuffer
  const binaryDer = base64Decode(pemContents);

  // Import as PKCS#8
  return await crypto.subtle.importKey(
    'pkcs8',
    binaryDer,
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: { name: 'SHA-256' },
    },
    false,
    ['sign']
  );
}

/**
 * Utility: Base64URL encode
 */
function base64urlEncode(data) {
  let base64;
  if (data instanceof ArrayBuffer) {
    const bytes = new Uint8Array(data);
    base64 = btoa(String.fromCharCode(...bytes));
  } else if (typeof data === 'string') {
    base64 = btoa(data);
  } else {
    throw new Error('Invalid data type for base64url encoding');
  }

  return base64
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

/**
 * Utility: Base64URL decode
 */
function base64urlDecode(str) {
  const base64 = str
    .replace(/-/g, '+')
    .replace(/_/g, '/');
  return atob(base64);
}

/**
 * Utility: Base64URL string to ArrayBuffer
 */
function base64urlToArrayBuffer(str) {
  const base64 = str
    .replace(/-/g, '+')
    .replace(/_/g, '/');
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/**
 * Utility: Base64 decode to ArrayBuffer
 */
function base64Decode(str) {
  const binary = atob(str);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/**
 * Utility: JSON response helper
 */
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
