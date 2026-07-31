import express from 'express';
import { router } from './routes.js';

const app = express();
app.use(express.json({ limit: '16kb' }));

// Minimal request logging.
app.use((req, _res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.path}`);
  next();
});

app.use('/v1', router);

app.use((_req, res) => res.status(404).json({ error: 'not_found' }));

const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';
app.listen(PORT, HOST, () => {
  console.log(`HausVPN control plane listening on ${HOST}:${PORT}`);
  console.log(`WireGuard apply mode: ${process.env.WG_APPLY === '1' ? 'ON' : 'OFF (dev)'}`);
});
