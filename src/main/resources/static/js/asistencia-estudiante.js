/*
 * La pantalla del estudiante: escanear el QR con la cámara, o escribir el código de 6
 * dígitos, y ver el resultado.
 *
 * Tres detalles que explican por qué está escrito así:
 *
 *   1. El lector usa el BarcodeDetector NATIVO del navegador si existe (Chrome en Android
 *      lo trae y es el más rápido y el que menos batería gasta). Solo si no existe —Safari
 *      en iOS— se descarga el respaldo en WebAssembly, y se descarga en ese momento, no en
 *      cada carga de página.
 *   2. La cámara exige HTTPS (o localhost). Si el navegador la niega, no se deja al
 *      estudiante sin salida: el formulario del código sigue ahí.
 *   3. El permiso de cámara se pide DESPUÉS de explicar para qué, no de golpe al abrir:
 *      un permiso negado por sorpresa es muy difícil de revertir en un celular.
 *
 * Depende de core.js (csrf), que se carga antes.
 */
(function () {
  'use strict';

  var contenedor = document.querySelector('[data-asistencia-estudiante]');
  if (!contenedor) { return; }

  var csrf = window.SchoolPlus.csrf;
  var ENDPOINT = '/api/v1/asistencia/registros';
  var RUTA_PONYFILL = '/vendor/barcode-detector/ponyfill.js';
  var RUTA_WASM = '/vendor/zxing-wasm/zxing_reader.wasm';

  var geoRequerida = contenedor.getAttribute('data-geo') === 'true';
  var confirmarPendiente = contenedor.hasAttribute('data-confirmar');

  var botonEscanear = contenedor.querySelector('[data-escanear]');
  var explicacion = contenedor.querySelector('[data-explicacion]');
  var lector = contenedor.querySelector('[data-lector]');
  var video = contenedor.querySelector('video');
  var formularioCodigo = contenedor.querySelector('[data-form-codigo]');
  var campoCodigo = contenedor.querySelector('[data-codigo]');
  var resultado = contenedor.querySelector('[data-resultado]');
  var resultadoTitulo = contenedor.querySelector('[data-resultado-titulo]');
  var resultadoMensaje = contenedor.querySelector('[data-resultado-mensaje]');
  var resultadoDetalle = contenedor.querySelector('[data-resultado-detalle]');

  var flujo = null;
  var buscador = null;
  var temporizador = null;
  var enviando = false;

  // ── Resultado en pantalla ────────────────────────────────────────────────
  function mostrar(ok, titulo, mensaje, detalle) {
    detenerLector();
    resultado.hidden = false;
    resultado.classList.toggle('resultado-ok', ok);
    resultado.classList.toggle('resultado-error', !ok);
    resultadoTitulo.textContent = titulo;
    resultadoMensaje.textContent = mensaje;
    resultadoDetalle.textContent = detalle || '';
    resultadoDetalle.hidden = !detalle;
    resultado.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // ── Ubicación (solo si el colegio la exige) ──────────────────────────────
  function ubicacion() {
    if (!geoRequerida || !navigator.geolocation) {
      return Promise.resolve({});
    }
    return new Promise(function (resolve) {
      navigator.geolocation.getCurrentPosition(
        function (posicion) {
          resolve({ latitud: posicion.coords.latitude, longitud: posicion.coords.longitude });
        },
        function () { resolve({}); },
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
      );
    });
  }

  // ── Envío ────────────────────────────────────────────────────────────────
  function registrar(cuerpo) {
    if (enviando) { return; }
    enviando = true;
    ubicacion().then(function (coordenadas) {
      var datos = Object.assign({}, cuerpo, coordenadas);
      return fetch(ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() },
        body: JSON.stringify(datos)
      }).then(function (respuesta) {
        return respuesta.json().then(function (json) {
          if (respuesta.ok && json.ok) {
            mostrar(true, json.titulo, json.mensaje, json.clase + ' · ' + json.hora);
          } else {
            mostrar(false, json.titulo || 'No se pudo registrar',
              json.mensaje || 'Inténtalo otra vez en un momento.', '');
          }
        });
      });
    }).catch(function () {
      mostrar(false, 'Sin conexión', 'No pudimos hablar con el servidor. Revisa tu conexión e inténtalo de nuevo.', '');
    }).then(function () {
      enviando = false;
    });
  }

  // ── Lector de QR ─────────────────────────────────────────────────────────
  function cargarPonyfill() {
    if (window.BarcodeDetectionAPI) { return Promise.resolve(); }
    return new Promise(function (resolve, reject) {
      var script = document.createElement('script');
      script.src = RUTA_PONYFILL;
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  function crearBuscador() {
    // Nativo primero: en Android suele estar y evita descargar 1 MB de WebAssembly.
    if (window.BarcodeDetector && window.BarcodeDetector.getSupportedFormats) {
      return window.BarcodeDetector.getSupportedFormats().then(function (formatos) {
        if (formatos.indexOf('qr_code') >= 0) {
          return new window.BarcodeDetector({ formats: ['qr_code'] });
        }
        return respaldo();
      }).catch(respaldo);
    }
    return respaldo();
  }

  function respaldo() {
    return cargarPonyfill().then(function () {
      // Sin esto, la librería buscaría el .wasm en un CDN y la política de contenido lo
      // bloquearía en silencio.
      window.BarcodeDetectionAPI.prepareZXingModule({
        overrides: {
          locateFile: function (ruta, prefijo) {
            return ruta.endsWith('.wasm') ? RUTA_WASM : prefijo + ruta;
          }
        }
      });
      return new window.BarcodeDetectionAPI.BarcodeDetector({ formats: ['qr_code'] });
    });
  }

  function abrirCamara() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      mostrar(false, 'Este navegador no abre la cámara',
        'Escribe el código de 6 dígitos que aparece en la pantalla del salón.', '');
      return;
    }
    navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      .then(function (stream) {
        flujo = stream;
        video.srcObject = stream;
        video.setAttribute('playsinline', 'true');   // iOS: sin esto abre el video a pantalla completa
        return video.play();
      })
      .then(crearBuscador)
      .then(function (detector) {
        buscador = detector;
        explicacion.hidden = true;
        lector.hidden = false;
        // Cada 400 ms alcanza de sobra y deja respirar al teléfono.
        temporizador = window.setInterval(buscarQr, 400);
      })
      .catch(function (error) {
        var negado = error && (error.name === 'NotAllowedError' || error.name === 'SecurityError');
        mostrar(false,
          negado ? 'No diste permiso de cámara' : 'No pudimos abrir la cámara',
          negado
            ? 'Puedes registrar tu asistencia escribiendo el código de 6 dígitos de la pantalla.'
            : 'Usa el código de 6 dígitos de la pantalla, o revisa que ninguna otra app esté usando la cámara.',
          '');
      });
  }

  function buscarQr() {
    if (!buscador || !video || video.readyState < 2 || enviando) { return; }
    buscador.detect(video).then(function (codigos) {
      if (!codigos || !codigos.length) { return; }
      var texto = codigos[0].rawValue;
      if (!texto) { return; }
      detenerLector();
      registrar({ token: texto });
    }).catch(function () {
      // Un fotograma borroso falla y ya: el siguiente intento llega en 400 ms.
    });
  }

  function detenerLector() {
    if (temporizador) { window.clearInterval(temporizador); temporizador = null; }
    if (flujo) { flujo.getTracks().forEach(function (pista) { pista.stop(); }); flujo = null; }
    if (lector) { lector.hidden = true; }
    if (video) { video.srcObject = null; }
  }

  // ── Enganches ────────────────────────────────────────────────────────────
  if (botonEscanear) {
    botonEscanear.addEventListener('click', function () {
      resultado.hidden = true;
      explicacion.hidden = false;
      explicacion.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }

  var botonPermiso = contenedor.querySelector('[data-abrir-camara]');
  if (botonPermiso) { botonPermiso.addEventListener('click', abrirCamara); }

  var botonCancelar = contenedor.querySelector('[data-cancelar-camara]');
  if (botonCancelar) {
    botonCancelar.addEventListener('click', function () {
      detenerLector();
      explicacion.hidden = true;
    });
  }

  if (formularioCodigo) {
    formularioCodigo.addEventListener('submit', function (evento) {
      evento.preventDefault();
      var codigo = (campoCodigo.value || '').replace(/\D/g, '');
      if (codigo.length !== 6) {
        mostrar(false, 'Código incompleto', 'Son 6 dígitos, los que ves en la pantalla del salón.', '');
        return;
      }
      registrar({ codigo: codigo });
      campoCodigo.value = '';
    });
  }

  // La pantalla de confirmación no pide nada: viene de escanear con la cámara del
  // teléfono y solo tiene que rematar el registro.
  if (confirmarPendiente) {
    registrar({ pendiente: true });
  }

  window.addEventListener('pagehide', detenerLector);
})();
