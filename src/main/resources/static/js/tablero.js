/*
 * Tablero de tareas: alta, borrado y arrastrar-soltar con bloqueo optimista
 * (409 = alguien se adelantó, ver TableroService). Depende de core.js
 * (window.SchoolPlus.csrf/mostrarAviso/pedirConfirmacion), que se carga antes.
 */
(function () {
  'use strict';

  var tablero = document.getElementById('tablero');
  if (!tablero) { return; }

  var csrf = window.SchoolPlus.csrf;
  var mostrarAviso = window.SchoolPlus.mostrarAviso;
  var pedirConfirmacion = window.SchoolPlus.pedirConfirmacion;

  var API = '/api/v1/tablero';
  var avisoConflicto = document.getElementById('aviso-conflicto');

  function pedir(url, metodo, cuerpo) {
    return fetch(url, {
      method: metodo,
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() },
      body: cuerpo ? JSON.stringify(cuerpo) : undefined
    }).then(function (respuesta) {
      // 409 = alguien se adelantó (§5.2). No es un error del servidor: se avisa y se
      // recarga para que la persona vea el estado real antes de volver a intentarlo.
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

  // Alta de tarjeta
  var formNueva = document.getElementById('form-nueva-tarjeta');
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

  // Borrar
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

  // Arrastrar y soltar
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
    // Firefox exige que se escriba algo en dataTransfer para iniciar el arrastre.
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

    // Posición: cuántas tarjetas quedan por encima del punto donde se soltó.
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
      // La versión que se tenía a la vista. Si en la base es otra, el servidor
      // responde 409 en vez de dejar que este movimiento pise el del otro.
      version: Number(arrastrada.dataset.version)
    }).then(function () { window.location.reload(); }).catch(function () { /* ya avisado */ });
  });
})();
