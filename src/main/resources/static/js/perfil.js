/*
 * Ficha del colaborador: agregar/quitar filas de hijos y renumerar los índices
 * (hijos[N].campo) para que el binding de Spring siga siendo válido. Mismo patrón
 * que capacitaciones.js con las preguntas del quiz, pero de un solo nivel.
 */
(function () {
  'use strict';

  var lista = document.querySelector('[data-hijos-lista]');
  var plantilla = document.querySelector('[data-plantilla-hijo]');
  var botonAgregar = document.querySelector('[data-agregar-hijo]');
  if (!lista || !plantilla || !botonAgregar) { return; }

  function renumerar() {
    lista.querySelectorAll('[data-hijo-fila]').forEach(function (fila, indice) {
      fila.querySelectorAll('[data-campo]').forEach(function (campo) {
        campo.name = 'hijos[' + indice + '].' + campo.getAttribute('data-campo');
      });
    });
  }

  function engancharQuitar(fila) {
    fila.querySelector('[data-quitar-hijo]').addEventListener('click', function () {
      fila.remove();
      renumerar();
    });
  }

  botonAgregar.addEventListener('click', function () {
    var nodo = plantilla.content.cloneNode(true);
    var fila = nodo.querySelector('[data-hijo-fila]');
    engancharQuitar(fila);
    lista.appendChild(nodo);
    renumerar();
  });

  lista.querySelectorAll('[data-hijo-fila]').forEach(engancharQuitar);
})();
