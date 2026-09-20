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

const isProduction = process.env['NODE_ENV'] === 'production';
const fileName = isProduction ? 'environment.production.ts' : 'environment.ts';

const apiBaseUrl = requireEnv('ENERLYTICS_API_BASE_URL', 'http://localhost:8080/api');
const appName = requireEnv('ENERLYTICS_APP_NAME', 'Enerlytics');

const content = `export const environment = {
  production: ${isProduction},
  apiBaseUrl: '${apiBaseUrl}',
  appName: '${appName}'
};
`;

fs.writeFileSync(path.join(targetDir, fileName), content, { encoding: 'utf8' });
console.log(`Wrote ${fileName} with apiBaseUrl=${apiBaseUrl}`);
