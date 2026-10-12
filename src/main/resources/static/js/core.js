/*
 * Lo que el resto de los scripts del shell comparten: el modal genérico y el token
 * CSRF. Se carga primero (ver app-layout.html) y expone window.SchoolPlus para que
 * shell.js y los scripts de asistencia lo usen sin duplicar nada.
 *
 * Archivo aparte y no <script> embebido: la CSP de SecurityConfig no admite scripts en
 * línea.
 */
(function () {
  'use strict';

  window.SchoolPlus = window.SchoolPlus || {};

  // ── Modal genérico ──────────────────────────────────────────────────────
  // Reemplaza a window.alert()/window.confirm() (el "localhost dice" nativo del
  // navegador) por el diálogo del sistema de diseño (.dialog-backdrop / .dialog,
  // ya definidos en styles.css). Devuelve una Promise<boolean>: true = aceptar,
  // false = cancelar o cerrar sin decidir.
  var modal = {
    backdrop: document.getElementById('modal-backdrop'),
    titulo: document.getElementById('modal-titulo'),
    cuerpo: document.getElementById('modal-cuerpo'),
    aceptar: document.getElementById('modal-aceptar'),
    cancelar: document.getElementById('modal-cancelar'),
    resolver: null
  };

  function cerrarModal(resultado) {
    if (!modal.backdrop) { return; }
    modal.backdrop.classList.add('hidden');
    if (modal.resolver) {
      var resolver = modal.resolver;
      modal.resolver = null;
      resolver(resultado);
    }
  }

  function abrirModal(mensaje, titulo, conCancelar) {
    if (!modal.backdrop) {
      // Sin el markup del modal (no debería pasar: vive en app-layout.html), se
      // degrada a los diálogos nativos para no dejar la acción sin respuesta.
      return Promise.resolve(conCancelar ? window.confirm(mensaje) : (window.alert(mensaje), true));
    }
    modal.titulo.textContent = titulo;
    modal.cuerpo.textContent = mensaje;
    modal.cancelar.classList.toggle('hidden', !conCancelar);
    modal.backdrop.classList.remove('hidden');
    modal.aceptar.focus();
    return new Promise(function (resolve) { modal.resolver = resolve; });
  }

  window.SchoolPlus.mostrarAviso = function (mensaje, titulo) {
    return abrirModal(mensaje, titulo || 'Aviso', false);
  };

  window.SchoolPlus.pedirConfirmacion = function (mensaje, titulo) {
    return abrirModal(mensaje, titulo || 'Confirmar', true);
  };

  if (modal.backdrop) {
    modal.aceptar.addEventListener('click', function () { cerrarModal(true); });
    modal.cancelar.addEventListener('click', function () { cerrarModal(false); });
    modal.backdrop.addEventListener('click', function (evento) {
      if (evento.target === modal.backdrop) { cerrarModal(false); }
    });
    document.addEventListener('keydown', function (evento) {
      if (evento.key === 'Escape' && !modal.backdrop.classList.contains('hidden')) {
        cerrarModal(false);
      }
    });
  }

  // ── CSRF ─────────────────────────────────────────────────────────────────
  // Spring lo deja en una cookie legible (CookieCsrfTokenRepository). Compartida por
  // la campanita y la asistencia: sin este header, Spring Security rechaza el POST con
  // 403 — y como fetch() no rechaza su promesa por códigos de error HTTP (solo por
  // fallas de red), un 403 así pasa desapercibido en silencio.
  window.SchoolPlus.csrf = function () {
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : '';
  };
})();
