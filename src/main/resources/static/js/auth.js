/*
 * Comportamiento de las vistas de autenticación.
 *
 * Origen: el <script type="text/x-dc"> de los prototipos de diseño (login/Login.dc.html)
 * (estado `reveal` y estado `loading` del botón). Allí era un componente del lienzo de
 * Claude Design; aquí es JavaScript plano en un archivo aparte, porque la CSP de
 * SecurityConfig no admite scripts embebidos.
 *
 * Todo lo que hace es cosmético: si el JS no carga, los formularios siguen funcionando.
 */
(function () {
  'use strict';

  // ── Mostrar / ocultar contraseña ──────────────────────────────────────────
  document.querySelectorAll('[data-revelar]').forEach(function (boton) {
    var campo = document.getElementById(boton.getAttribute('data-revelar'));
    if (!campo) {
      return;
    }
    boton.setAttribute('aria-pressed', 'false');
    boton.addEventListener('click', function () {
      var oculto = campo.type === 'password';
      campo.type = oculto ? 'text' : 'password';
      boton.textContent = oculto ? 'Ocultar' : 'Mostrar';
      boton.setAttribute('aria-pressed', String(oculto));
    });
  });

  // ── Botón en estado "enviando" ────────────────────────────────────────────
  // Evita el doble envío, que en el registro crearía dos pendientes y en la
  // recuperación gastaría dos correos.
  document.querySelectorAll('form[data-formulario-auth]').forEach(function (formulario) {
    formulario.addEventListener('submit', function () {
      var boton = formulario.querySelector('[data-etiqueta-envio]');
      if (!boton || boton.disabled) {
        return;
      }
      var etiqueta = boton.querySelector('span');
      if (etiqueta) {
        etiqueta.textContent = boton.getAttribute('data-etiqueta-envio');
      }
      // Se desactiva después del envío para no anular el campo del botón.
      window.setTimeout(function () {
        boton.disabled = true;
      }, 0);
    });
  });
})();
