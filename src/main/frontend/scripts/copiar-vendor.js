/*
 * Copia a `static/vendor` las piezas de terceros que el navegador necesita servidas desde
 * nuestro propio dominio.
 *
 * Por qué no un CDN: la política de contenido de SecurityConfig solo admite scripts de
 * 'self'. Y por qué no versionar el archivo: es salida del build, igual que
 * `static/main.css` — se regenera con `npm run build` y está en .gitignore.
 *
 * Lo que se copia:
 *   · barcode-detector (IIFE): el lector de QR. Usa el BarcodeDetector nativo del navegador
 *     cuando existe (Chrome en Android) y, si no (Safari en iOS), decodifica con WebAssembly.
 *   · zxing_reader.wasm: ese binario. La librería lo pediría a un CDN si no se le dijera
 *     dónde está; el JavaScript de la pantalla se lo indica con prepareZXingModule.
 *
 * Las rutas se arman a mano sobre node_modules y no con require.resolve porque estos
 * paquetes declaran "exports" y Node se niega a resolver rutas internas.
 */
const fs = require('fs');
const path = require('path');

const modulos = path.resolve(__dirname, '../node_modules');
const destino = path.resolve(__dirname, '../../resources/static/vendor');

const archivos = [
  {
    desde: path.join(modulos, 'barcode-detector/dist/iife/ponyfill.js'),
    hacia: 'barcode-detector/ponyfill.js'
  },
  {
    desde: path.join(modulos, 'barcode-detector/LICENSE'),
    hacia: 'barcode-detector/LICENSE'
  },
  {
    desde: path.join(modulos, 'zxing-wasm/dist/reader/zxing_reader.wasm'),
    hacia: 'zxing-wasm/zxing_reader.wasm'
  }
];

for (const archivo of archivos) {
  if (!fs.existsSync(archivo.desde)) {
    throw new Error('Falta ' + archivo.desde + '. ¿Se corrió "npm install" en src/main/frontend?');
  }
  const rutaFinal = path.join(destino, archivo.hacia);
  fs.mkdirSync(path.dirname(rutaFinal), { recursive: true });
  fs.copyFileSync(archivo.desde, rutaFinal);
  console.log('vendor <-', archivo.hacia);
}
