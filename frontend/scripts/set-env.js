const fs = require('fs');
const path = require('path');

function requireEnv(name, fallback) {
  const value = process.env[name];
  if (value === undefined || value === '') {
    if (fallback !== undefined) {
      console.warn(`Environment variable ${name} is not set; using fallback value.`);
      return fallback;
    }
    throw new Error(`Required environment variable ${name} is not set.`);
  }
  return value;
}

const targetDir = path.join(__dirname, '..', 'src', 'environments');
if (!fs.existsSync(targetDir)) {
  fs.mkdirSync(targetDir, { recursive: true });
}

const apiBaseUrl = requireEnv('ENERLYTICS_API_BASE_URL', 'http://localhost:8080/api');
const appName = requireEnv('ENERLYTICS_APP_NAME', 'Enerlytics');

// Both files are always regenerated so `ng build` (fileReplacements →
// environment.production.ts) and `ng serve` (environment.ts) stay in sync
// regardless of NODE_ENV.
const render = (production) => `export const environment = {
  production: ${production},
  apiBaseUrl: '${apiBaseUrl}',
  appName: '${appName}'
};
`;

fs.writeFileSync(path.join(targetDir, 'environment.ts'), render(false), { encoding: 'utf8' });
fs.writeFileSync(path.join(targetDir, 'environment.production.ts'), render(true), { encoding: 'utf8' });
console.log(`Wrote environment.ts and environment.production.ts with apiBaseUrl=${apiBaseUrl}`);
