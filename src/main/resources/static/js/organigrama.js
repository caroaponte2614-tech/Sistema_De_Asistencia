/*
 * Jerarquía: alterna entre la vista de lista y la gráfica (mismo dato, dos
 * fragmentos ya renderizados por el servidor — acá solo se muestra uno u otro),
 * colapsa/expande ramas del árbol gráfico y hace zoom con +/-.
 */
(function () {
  'use strict';

  var vistaLista = document.querySelector('[data-organigrama-vista-lista]');
  var vistaGrafico = document.querySelector('[data-organigrama-vista-grafico]');
  var botonesModo = document.querySelectorAll('[data-organigrama-modo]');
  var controlesZoom = document.querySelector('[data-organigrama-zoom]');
  if (!vistaLista || !vistaGrafico || botonesModo.length === 0) { return; }

  function activar(modo) {
    vistaLista.classList.toggle('hidden', modo !== 'lista');
    vistaGrafico.classList.toggle('hidden', modo !== 'grafico');
    if (controlesZoom) {
      controlesZoom.classList.toggle('hidden', modo !== 'grafico');
      controlesZoom.classList.toggle('flex', modo === 'grafico');
    }
    botonesModo.forEach(function (boton) {
      var esEsteModo = boton.getAttribute('data-organigrama-modo') === modo;
      boton.classList.toggle('btn-primary', esEsteModo);
      boton.classList.toggle('btn-secondary', !esEsteModo);
    });
  }

  botonesModo.forEach(function (boton) {
    boton.addEventListener('click', function () {
      activar(boton.getAttribute('data-organigrama-modo'));
    });
  });

  // ── Colapsar / expandir ramas ────────────────────────────────────────────
  vistaGrafico.querySelectorAll('.organigrama-nodo[data-tiene-hijos="true"]').forEach(function (nodo) {
    nodo.addEventListener('click', function () {
      var ramaHijos = nodo.parentElement.querySelector('ul');
      if (ramaHijos) { ramaHijos.classList.toggle('hidden'); }
    });
  });

  // ── Zoom ──────────────────────────────────────────────────────────────────
  var escalable = document.querySelector('[data-organigrama-escalable]');
  var escala = 1;
  var PASO = 0.15;
  var MIN = 0.4;
  var MAX = 2;

  function aplicarEscala() {
    if (escalable) { escalable.style.transform = 'scale(' + escala + ')'; }
  }

  var botonAcercar = document.querySelector('[data-organigrama-acercar]');
  var botonAlejar = document.querySelector('[data-organigrama-alejar]');
  if (botonAcercar) {
    botonAcercar.addEventListener('click', function () {
      escala = Math.min(MAX, escala + PASO);
      aplicarEscala();
    });
  }
  if (botonAlejar) {
    botonAlejar.addEventListener('click', function () {
      escala = Math.max(MIN, escala - PASO);
      aplicarEscala();
    });
  }
})();
