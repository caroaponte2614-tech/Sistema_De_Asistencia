/*
 * La pantalla de proyección del profesor: QR grande, código de 6 dígitos, contador y lista
 * de presentes en vivo.
 *
 * Cómo se mantiene al día, con dos mecanismos que se complementan:
 *   · un temporizador que vuelve a pedir el estado justo cuando el QR rota (el servidor
 *     dice cuántos segundos faltan, así que no hay que adivinarlo);
 *   · un WebSocket que avisa "cambió" en cuanto alguien se registra, para que el contador
 *     suba al instante y no dentro de 15 segundos.
 *
 * El socket no manda datos, solo el aviso: la pantalla vuelve a pedir el estado por la
 * misma llamada de siempre. Así hay un solo camino por el que se pinta la lista. Si el
 * socket no conecta (red del colegio, proxy), el temporizador solo ya mantiene la pantalla
 * correcta.
 */
(function () {
  'use strict';

  var panel = document.querySelector('[data-proyeccion]');
  if (!panel) { return; }

  var sesionId = panel.getAttribute('data-sesion');
  var qr = panel.querySelector('[data-qr]');
  var codigo = panel.querySelector('[data-codigo]');
  var cuenta = panel.querySelector('[data-cuenta]');
  var barra = panel.querySelector('[data-barra]');
  var presentes = panel.querySelector('[data-presentes]');
  var tardes = panel.querySelector('[data-tardes]');
  var inscritos = panel.querySelector('[data-inscritos]');
  var lista = panel.querySelector('[data-lista]');
  var vacio = panel.querySelector('[data-lista-vacia]');
  var botonPantalla = panel.querySelector('[data-pantalla-completa]');

  var siguiente = null;
  var bloqueoPantalla = null;

  function pedirEstado() {
    fetch('/api/v1/asistencia/sesiones/' + sesionId + '/estado', {
      headers: { 'Accept': 'application/json' }
    }).then(function (respuesta) {
      if (!respuesta.ok) { throw new Error('estado'); }
      return respuesta.json();
    }).then(pintar).catch(function () {
      // Un fallo puntual no debe dejar la pantalla congelada para siempre.
      programar(5);
    });
  }

  function pintar(estado) {
    if (!estado.abierta) {
      window.location.reload();   // la cerraron: que se vea el detalle
      return;
    }
    qr.src = estado.qr;
    codigo.textContent = estado.codigo.replace(/(\d{3})(\d{3})/, '$1 $2');
    presentes.textContent = estado.presentes;
    tardes.textContent = estado.tardes;
    inscritos.textContent = estado.registrados + ' / ' + estado.inscritos;

    lista.innerHTML = '';
    estado.registros.forEach(function (registro) {
      var fila = document.createElement('li');
      fila.className = 'flex items-center justify-between gap-3 border-b border-divider py-2 last:border-0';

      var nombre = document.createElement('span');
      nombre.className = 'truncate text-[15px]';
      nombre.textContent = registro.estudiante;

      var etiqueta = document.createElement('span');
      etiqueta.className = 'tag shrink-0 ' + (registro.estado === 'TARDE' ? 'tag-warning' : 'tag-success');
      etiqueta.textContent = registro.hora + ' · ' + registro.estadoEtiqueta;

      fila.appendChild(nombre);
      fila.appendChild(etiqueta);
      lista.appendChild(fila);
    });
    vacio.hidden = estado.registros.length > 0;

    animarCuenta(estado.segundosParaRotar, estado.rotacionSegundos);
    programar(estado.segundosParaRotar);
  }

  function animarCuenta(restantes, total) {
    var segundos = restantes;
    cuenta.textContent = segundos;
    barra.style.width = Math.round((segundos / total) * 100) + '%';
    if (window.tictac) { window.clearInterval(window.tictac); }
    window.tictac = window.setInterval(function () {
      segundos -= 1;
      if (segundos < 0) { segundos = 0; }
      cuenta.textContent = segundos;
      barra.style.width = Math.round((segundos / total) * 100) + '%';
    }, 1000);
  }

  function programar(segundos) {
    if (siguiente) { window.clearTimeout(siguiente); }
    // Un pelín después del cambio de ventana, para no pedir el QR viejo por un milisegundo.
    siguiente = window.setTimeout(pedirEstado, Math.max(1, segundos) * 1000 + 250);
  }

  // ── Aviso en vivo ────────────────────────────────────────────────────────
  function conectarSocket() {
    if (!window.WebSocket) { return; }
    var protocolo = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    var socket = new WebSocket(protocolo + window.location.host + '/ws/asistencia?sesion=' + sesionId);
    socket.onmessage = function () { pedirEstado(); };
    socket.onclose = function () {
      // Reintento espaciado: el temporizador del QR ya mantiene la pantalla viva.
      window.setTimeout(conectarSocket, 10000);
    };
  }

  // ── Modo proyección ──────────────────────────────────────────────────────
  if (botonPantalla) {
    botonPantalla.addEventListener('click', function () {
      if (document.fullscreenElement) {
        document.exitFullscreen();
      } else if (panel.requestFullscreen) {
        panel.requestFullscreen();
      }
    });
  }

  // Que el proyector o la tablet no se apaguen en mitad de la clase.
  function mantenerPantallaEncendida() {
    if (!navigator.wakeLock) { return; }
    navigator.wakeLock.request('screen').then(function (bloqueo) {
      bloqueoPantalla = bloqueo;
    }).catch(function () {
      // El navegador puede negarlo; no es crítico.
    });
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') {
      if (!bloqueoPantalla) { mantenerPantallaEncendida(); }
      pedirEstado();
    }
  });

  pedirEstado();
  conectarSocket();
  mantenerPantallaEncendida();
})();
