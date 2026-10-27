import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/fraunces/full.css';
import '@fontsource-variable/fraunces/full-italic.css';
import '@fontsource-variable/inter';
import './tokens.css';
import './styles.css';
import { Soundbox } from './Soundbox.tsx';
import { Pay } from './Pay.tsx';
import { Ledger } from './Ledger.tsx';
import { Control } from './Control.tsx';
import { Impact } from './Impact.tsx';

function App() {
  const path = location.pathname;
  if (path.startsWith('/pay/')) return <Pay refId={decodeURIComponent(path.slice(5))} />;
  if (path.startsWith('/ledger')) return <Ledger />;
  if (path.startsWith('/control')) return <Control />;
  if (path.startsWith('/paytm')) return <Impact />;
  return <Soundbox />;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
