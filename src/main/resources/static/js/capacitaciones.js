/*
 * Capacitaciones: marcar el video como visto al terminarlo, y el editor dinámico de
 * preguntas/opciones del quiz (agregar/quitar filas y renumerar los índices para que
 * el binding de Spring — preguntas[N].opciones[M].campo — siga siendo válido).
 * Depende de core.js (csrf), que se carga antes.
 */
(function () {
  'use strict';

  var csrf = window.SchoolPlus.csrf;

  // ── Marcar el video como visto ──────────────────────────────────────────
  var video = document.querySelector('[data-curso-video]');
  var formVideoVisto = document.querySelector('[data-form-video-visto]');
  if (video && formVideoVisto) {
    video.addEventListener('ended', function () {
      fetch(formVideoVisto.action, {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': csrf() }
      }).then(function () {
        var aviso = document.querySelector('[data-video-completado]');
        if (aviso) { aviso.classList.remove('hidden'); }
        var botonQuiz = document.querySelector('[data-ir-al-quiz]');
        if (botonQuiz) { botonQuiz.disabled = false; }
      });
    });
  }

  // ── No dejar adelantar el video ──────────────────────────────────────────
  // El quiz exige haber "visto" el video (evento ended), pero sin esto cualquiera
  // podría arrastrar la barra al final y disparar ended sin mirar nada. Se recuerda
  // el punto más lejano alcanzado por reproducción normal y, si un seek (barra,
  // flechas del teclado) salta más allá, se lo devuelve ahí. Retroceder para repasar
  // sigue funcionando: solo se bloquea avanzar más allá de lo ya visto.
  if (video) {
    var maxAlcanzado = 0;
    video.addEventListener('timeupdate', function () {
      if (!video.seeking && video.currentTime > maxAlcanzado) {
        maxAlcanzado = video.currentTime;
      }
    });
    video.addEventListener('seeking', function () {
      if (video.currentTime > maxAlcanzado + 0.5) {
        video.currentTime = maxAlcanzado;
      }
    });
  }

  // ── Editor de preguntas (admin) ──────────────────────────────────────────
  var listaPreguntas = document.querySelector('[data-preguntas-lista]');
  var plantillaPregunta = document.querySelector('[data-plantilla-pregunta]');
  var plantillaOpcion = document.querySelector('[data-plantilla-opcion]');
  var botonAgregarPregunta = document.querySelector('[data-agregar-pregunta]');
  if (!listaPreguntas || !plantillaPregunta || !plantillaOpcion || !botonAgregarPregunta) {
    return;
  }

  function renumerar() {
    var preguntas = listaPreguntas.querySelectorAll('[data-pregunta-fila]');
    preguntas.forEach(function (filaPregunta, indicePregunta) {
      filaPregunta.querySelectorAll('[data-campo]').forEach(function (campo) {
        // Los campos de las opciones se renumeran aparte, no acá.
        if (campo.closest('[data-opcion-fila]')) { return; }
        campo.name = 'preguntas[' + indicePregunta + '].' + campo.getAttribute('data-campo');
      });

      var opciones = filaPregunta.querySelectorAll('[data-opcion-fila]');
      opciones.forEach(function (filaOpcion, indiceOpcion) {
        var prefijo = 'preguntas[' + indicePregunta + '].opciones[' + indiceOpcion + '].';
        filaOpcion.querySelectorAll('[data-campo]').forEach(function (campo) {
          campo.name = prefijo + campo.getAttribute('data-campo');
        });

        // El radio "esta es la correcta" no es un campo de la opción: es
        // preguntas[i].correctaIndice = j, todas comparten el mismo name (por eso
        // son un grupo de radio) y cada una vale su propia posición.
        var radioCorrecta = filaOpcion.querySelector('[data-campo-radio-correcta]');
        if (radioCorrecta) {
          radioCorrecta.name = 'preguntas[' + indicePregunta + '].correctaIndice';
          radioCorrecta.value = String(indiceOpcion);
        }
      });
    });
  }

  function agregarOpcion(listaOpciones) {
    var nodo = plantillaOpcion.content.cloneNode(true);
    var fila = nodo.querySelector('[data-opcion-fila]');
    fila.querySelector('[data-quitar-opcion]').addEventListener('click', function () {
      fila.remove();
      renumerar();
    });
    listaOpciones.appendChild(nodo);
  }

  function agregarPregunta() {
    var nodo = plantillaPregunta.content.cloneNode(true);
    var fila = nodo.querySelector('[data-pregunta-fila]');
    var listaOpciones = fila.querySelector('[data-opciones-lista]');

    fila.querySelector('[data-quitar-pregunta]').addEventListener('click', function () {
      fila.remove();
      renumerar();
    });
    fila.querySelector('[data-agregar-opcion]').addEventListener('click', function () {
      agregarOpcion(listaOpciones);
      renumerar();
    });

    listaPreguntas.appendChild(nodo);
    // Toda pregunta nueva arranca con dos opciones en blanco.
    agregarOpcion(listaOpciones);
    agregarOpcion(listaOpciones);
    renumerar();
  }

  botonAgregarPregunta.addEventListener('click', agregarPregunta);

  // Las filas que ya vinieron server-side (editando un curso existente) también
  // necesitan sus manejadores de "quitar".
  listaPreguntas.querySelectorAll('[data-pregunta-fila]').forEach(function (fila) {
    fila.querySelector('[data-quitar-pregunta]').addEventListener('click', function () {
      fila.remove();
      renumerar();
    });
    fila.querySelectorAll('[data-opcion-fila]').forEach(function (filaOpcion) {
      filaOpcion.querySelector('[data-quitar-opcion]').addEventListener('click', function () {
        filaOpcion.remove();
        renumerar();
      });
    });
    fila.querySelector('[data-agregar-opcion]').addEventListener('click', function () {
      agregarOpcion(fila.querySelector('[data-opciones-lista]'));
      renumerar();
    });
  });
})();
