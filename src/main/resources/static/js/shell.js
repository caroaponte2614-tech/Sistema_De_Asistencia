/*
 * El shell de toda pantalla interna: menú lateral en móvil y campanita de
 * notificaciones. Depende de core.js (window.SchoolPlus.csrf), que se carga antes.
 */
(function () {
  'use strict';

  // ── Menú lateral en móvil ───────────────────────────────────────────────
  var sidebar = document.getElementById('sidebar');
  var overlay = document.getElementById('overlay');
  var abrir = document.getElementById('abrir-menu');

  function alternarMenu(mostrar) {
    if (!sidebar || !overlay) { return; }
    sidebar.classList.toggle('-translate-x-full', !mostrar);
    overlay.classList.toggle('hidden', !mostrar);
  }
  if (abrir) { abrir.addEventListener('click', function () { alternarMenu(true); }); }
  if (overlay) { overlay.addEventListener('click', function () { alternarMenu(false); }); }

  // ── Campanita de notificaciones ──────────────────────────────────────────
  // Vive en todas las pantallas internas (el markup está en app-layout.html):
  // carga las recientes por REST al entrar y se suscribe al WebSocket para lo
  // que llegue mientras la pestaña está abierta.
  var campanaBoton = document.getElementById('campana-boton');
  var campanaPanel = document.getElementById('campana-panel');
  var campanaLista = document.getElementById('campana-lista');
  var campanaVacio = document.getElementById('campana-vacio');
  var campanaContador = document.getElementById('campana-contador');
  var campanaMarcarTodas = document.getElementById('campana-marcar-todas');
  var plantillaNotificacion = document.getElementById('plantilla-notificacion');

  if (campanaBoton && campanaPanel && campanaLista && plantillaNotificacion) {
    (function () {
      var csrf = window.SchoolPlus.csrf;
      var noLeidas = 0;

      function actualizarContador() {
        if (!campanaContador) { return; }
        campanaContador.textContent = noLeidas > 99 ? '99+' : String(noLeidas);
        campanaContador.classList.toggle('hidden', noLeidas === 0);
      }

      function formatearFecha(iso) {
        try {
          var fecha = new Date(iso);
          return fecha.toLocaleString('es-CO', {
            day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
          });
        } catch (error) {
          return '';
        }
      }

      function pintarFila(notificacion, alPrincipio) {
        var nodo = plantillaNotificacion.content.firstElementChild.cloneNode(true);
        nodo.dataset.id = notificacion.id;
        if (!notificacion.leida) { nodo.classList.add('bg-accent-100'); }
        nodo.querySelector('[data-notificacion-titulo]').textContent = notificacion.titulo;
        var cuerpo = nodo.querySelector('[data-notificacion-cuerpo]');
        if (notificacion.cuerpo) { cuerpo.textContent = notificacion.cuerpo; } else { cuerpo.remove(); }
        nodo.querySelector('[data-notificacion-fecha]').textContent = formatearFecha(notificacion.creadoEn);

        nodo.addEventListener('click', function (evento) {
          evento.preventDefault();
          marcarLeida(notificacion.id, nodo);
          if (notificacion.enlace) { window.location.href = notificacion.enlace; }
        });

        campanaVacio.classList.add('hidden');
        if (alPrincipio && campanaLista.firstChild) {
          campanaLista.insertBefore(nodo, campanaLista.firstChild);
        } else {
          campanaLista.appendChild(nodo);
        }
      }

      function marcarLeida(id, nodo) {
        if (nodo && nodo.classList.contains('bg-accent-100')) {
          nodo.classList.remove('bg-accent-100');
          noLeidas = Math.max(0, noLeidas - 1);
          actualizarContador();
        }
        fetch('/api/v1/notificaciones/' + id + '/leer', {
          method: 'POST',
          headers: { 'X-XSRF-TOKEN': csrf() }
        }).catch(function () {
          // Sin conexión: la próxima carga de la lista corrige el estado.
        });
      }

      function cargarRecientes() {
        fetch('/api/v1/notificaciones').then(function (respuesta) {
          return respuesta.ok ? respuesta.json() : null;
        }).then(function (datos) {
          if (!datos) { return; }
          campanaLista.innerHTML = '';
          noLeidas = datos.noLeidas;
          actualizarContador();
          if (datos.recientes.length === 0) {
            campanaVacio.classList.remove('hidden');
            return;
          }
          datos.recientes.forEach(function (n) { pintarFila(n, false); });
        }).catch(function () { /* la campanita simplemente queda vacía */ });
      }

      campanaBoton.addEventListener('click', function () {
        var abierto = !campanaPanel.classList.contains('hidden');
        campanaPanel.classList.toggle('hidden', abierto);
        campanaBoton.setAttribute('aria-expanded', String(!abierto));
      });
      document.addEventListener('click', function (evento) {
        if (!campanaPanel.classList.contains('hidden')
            && !campanaPanel.contains(evento.target) && evento.target !== campanaBoton
            && !campanaBoton.contains(evento.target)) {
          campanaPanel.classList.add('hidden');
          campanaBoton.setAttribute('aria-expanded', 'false');
        }
      });

      if (campanaMarcarTodas) {
        campanaMarcarTodas.addEventListener('click', function () {
          fetch('/api/v1/notificaciones/leer-todas', {
            method: 'POST',
            headers: { 'X-XSRF-TOKEN': csrf() }
          }).then(function () {
            campanaLista.querySelectorAll('.bg-accent-100').forEach(function (fila) {
              fila.classList.remove('bg-accent-100');
            });
            noLeidas = 0;
            actualizarContador();
          }).catch(function () { /* se corrige en la próxima carga */ });
        });
      }

      cargarRecientes();

      // WebSocket: lo que llegue mientras la pestaña está abierta, en vivo.
      // Sin STOMP ni librería externa (la CSP no admite scripts de CDN): un socket
      // crudo que manda el JSON de la notificación tal cual.
      if ('WebSocket' in window) {
        var protocolo = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        try {
          var socket = new WebSocket(protocolo + '//' + window.location.host + '/ws/notificaciones');
          socket.addEventListener('message', function (evento) {
            try {
              var notificacion = JSON.parse(evento.data);
              pintarFila(notificacion, true);
              noLeidas += 1;
              actualizarContador();
            } catch (error) {
              // Mensaje que no se pudo interpretar: se ignora, no rompe la campanita.
            }
          });
        } catch (error) {
          // Sin WebSocket disponible (proxy que lo bloquea, etc.), la campanita
          // sigue funcionando con lo que trae la carga inicial por REST.
        }
      }
    })();
  }
})();
