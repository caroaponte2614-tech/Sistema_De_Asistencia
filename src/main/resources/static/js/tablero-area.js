/*
 * Tablero de área: igual que tablero.js, pero apuntando al tablero del área activa
 * (data-area-id) y solo si quien mira puede editarlo (data-puede-editar) — quien solo
 * puede ver no tiene ni formulario ni botones de borrar en el HTML, así que esto se queda
 * inerte para esa persona sin necesidad de otra rama de código.
 */
(function () {
  'use strict';

  var tablero = document.getElementById('tablero-area');
  if (!tablero || tablero.dataset.puedeEditar !== 'true') { return; }

  var csrf = window.SchoolPlus.csrf;
  var mostrarAviso = window.SchoolPlus.mostrarAviso;
  var pedirConfirmacion = window.SchoolPlus.pedirConfirmacion;

  var API = '/api/v1/tablero/area/' + tablero.dataset.areaId;
  var avisoConflicto = document.getElementById('aviso-conflicto');

  function pedir(url, metodo, cuerpo) {
    return fetch(url, {
      method: metodo,
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() },
      body: cuerpo ? JSON.stringify(cuerpo) : undefined
    }).then(function (respuesta) {
      if (respuesta.status === 409) {
        if (avisoConflicto) { avisoConflicto.classList.remove('hidden'); }
        window.setTimeout(function () { window.location.reload(); }, 1800);
        return Promise.reject(new Error('conflicto'));
      }
      if (!respuesta.ok) {
        return respuesta.json().catch(function () { return {}; }).then(function (datos) {
          mostrarAviso(datos.mensaje || 'No se pudo completar la operación.');
          return Promise.reject(new Error('error'));
        });
      }
      return respuesta.status === 204 ? null : respuesta.json();
    });
  }

  var formNueva = document.getElementById('form-nueva-tarjeta-area');
  if (formNueva) {
    formNueva.addEventListener('submit', function (evento) {
      evento.preventDefault();
      pedir(API + '/tarjetas', 'POST', {
        columnaId: formNueva.columnaId.value,
        titulo: formNueva.titulo.value,
        etiqueta: formNueva.etiqueta.value,
        prioridad: formNueva.prioridad.value,
        fechaLimite: formNueva.fechaLimite.value || null
      }).then(function () { window.location.reload(); }).catch(function () { /* ya avisado */ });
    });
  }

  tablero.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-borrar]');
    if (!boton) { return; }
    var tarjeta = boton.closest('[data-tarjeta]');
    if (!tarjeta) { return; }
    pedirConfirmacion('¿Eliminar esta tarea?').then(function (confirmado) {
      if (!confirmado) { return; }
      pedir(API + '/tarjetas/' + tarjeta.dataset.tarjetaId, 'DELETE')
        .then(function () { window.location.reload(); })
        .catch(function () { /* ya avisado */ });
    });
  });

  var arrastrada = null;
  var columnaResaltada = null;

  function resaltarColumna(columna) {
    if (columnaResaltada === columna) { return; }
    if (columnaResaltada) { columnaResaltada.removeAttribute('data-sobre-columna'); }
    columnaResaltada = columna;
    if (columnaResaltada) { columnaResaltada.setAttribute('data-sobre-columna', ''); }
  }

  tablero.addEventListener('dragstart', function (evento) {
    var tarjeta = evento.target.closest('[data-tarjeta]');
    if (!tarjeta) { return; }
    arrastrada = tarjeta;
    evento.dataTransfer.effectAllowed = 'move';
    evento.dataTransfer.setData('text/plain', tarjeta.dataset.tarjetaId);
    tarjeta.classList.add('arrastrando');
  });

  tablero.addEventListener('dragend', function () {
    if (arrastrada) { arrastrada.classList.remove('arrastrando'); }
    arrastrada = null;
    resaltarColumna(null);
  });

  tablero.addEventListener('dragover', function (evento) {
    var columna = evento.target.closest('[data-columna]');
    if (!arrastrada || !columna) { return; }
    evento.preventDefault();
    evento.dataTransfer.dropEffect = 'move';
    resaltarColumna(columna);
  });

  tablero.addEventListener('drop', function (evento) {
    var columna = evento.target.closest('[data-columna]');
    resaltarColumna(null);
    if (!arrastrada || !columna) { return; }
    evento.preventDefault();

    var tarjetas = Array.prototype.slice.call(columna.querySelectorAll('[data-tarjeta]'))
      .filter(function (t) { return t !== arrastrada; });
    var posicion = tarjetas.length;
    for (var i = 0; i < tarjetas.length; i++) {
      var caja = tarjetas[i].getBoundingClientRect();
      if (evento.clientY < caja.top + caja.height / 2) { posicion = i; break; }
    }

    pedir(API + '/tarjetas/' + arrastrada.dataset.tarjetaId + '/mover', 'PATCH', {
      columnaDestinoId: columna.dataset.columnaId,
      posicion: posicion,
      version: Number(arrastrada.dataset.version)
    }).then(function () { window.location.reload(); }).catch(function () { /* ya avisado */ });
  });
})();
