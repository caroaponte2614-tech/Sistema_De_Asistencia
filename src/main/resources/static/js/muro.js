/*
 * Muro: alternar opciones de encuesta, agregar opciones y mandar los
 * formularios (comentar, reaccionar, votar, fijar, moderar) por AJAX.
 * Depende de core.js (window.SchoolPlus.mostrarAviso), que se carga antes.
 */
(function () {
  'use strict';

  var mostrarAviso = window.SchoolPlus.mostrarAviso;

  // ── Muro: opciones de encuesta solo cuando se elige ese tipo ────────────
  var opciones = document.querySelector('[data-opciones-encuesta]');
  if (opciones) {
    document.querySelectorAll('[data-tipo-publicacion]').forEach(function (radio) {
      radio.addEventListener('change', function () {
        var esEncuesta = radio.value === 'ENCUESTA' && radio.checked;
        opciones.classList.toggle('hidden', !esEncuesta);
        opciones.classList.toggle('flex', esEncuesta);
      });
    });
  }

  // ── Muro: agregar más opciones a una encuesta ────────────────────────────
  var MAX_OPCIONES_ENCUESTA = 8;
  var listaOpciones = document.querySelector('[data-lista-opciones]');
  var botonAgregarOpcion = document.querySelector('[data-agregar-opcion]');
  if (listaOpciones && botonAgregarOpcion) {
    botonAgregarOpcion.addEventListener('click', function () {
      var actuales = listaOpciones.querySelectorAll('input[name="opciones"]');
      if (actuales.length >= MAX_OPCIONES_ENCUESTA) { return; }

      var fila = document.createElement('div');
      fila.className = 'flex items-center gap-2';

      var campo = document.createElement('input');
      campo.name = 'opciones';
      campo.type = 'text';
      campo.maxLength = 200;
      campo.className = 'input-linea flex-1';
      campo.placeholder = 'Opción ' + (actuales.length + 1);

      var quitar = document.createElement('button');
      quitar.type = 'button';
      quitar.className = 'btn btn-secondary shrink-0 px-2.5 py-1.5 text-[12px]';
      quitar.setAttribute('aria-label', 'Quitar esta opción');
      quitar.textContent = '×';
      quitar.addEventListener('click', function () { fila.remove(); });

      fila.appendChild(campo);
      fila.appendChild(quitar);
      listaOpciones.appendChild(fila);
      campo.focus();

      if (actuales.length + 1 >= MAX_OPCIONES_ENCUESTA) {
        botonAgregarOpcion.disabled = true;
      }
    });
  }

  // ── Muro: comentar, reaccionar, votar, fijar y moderar sin recargar ─────
  // Cada formulario marcado data-ajax-muro vive dentro de un <article
  // data-publicacion-id="..."> (fragmento "entrada" de muro/portal.html). Se
  // manda con fetch + X-Ajax; el servidor responde con el <article> actualizado
  // y se reemplaza el nodo entero — no hay estado que sincronizar a mano.
  document.addEventListener('submit', function (evento) {
    var formulario = evento.target.closest('[data-ajax-muro]');
    if (!formulario) { return; }
    evento.preventDefault();

    var articulo = formulario.closest('[data-publicacion]');
    if (!articulo) { return; }

    var boton = formulario.querySelector('button[type="submit"]');
    if (boton) { boton.disabled = true; }

    fetch(formulario.action, {
      method: 'POST',
      headers: { 'X-Ajax': 'true' },
      body: new FormData(formulario)
    }).then(function (respuesta) {
      if (!respuesta.ok) {
        return respuesta.text().then(function (mensaje) {
          mostrarAviso(mensaje || 'No se pudo completar la acción.');
          if (boton) { boton.disabled = false; }
        });
      }
      return respuesta.text().then(function (html) {
        var envoltorio = document.createElement('div');
        envoltorio.innerHTML = html.trim();
        var nuevoArticulo = envoltorio.querySelector('[data-publicacion]');
        if (nuevoArticulo) { articulo.replaceWith(nuevoArticulo); }
      });
    }).catch(function () {
      mostrarAviso('No se pudo conectar con el servidor. Intenta de nuevo.');
      if (boton) { boton.disabled = false; }
    });
  });
})();
