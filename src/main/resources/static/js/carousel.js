/*
 * Carrusel genérico y reutilizable: capacitaciones/catalogo.html (cursos disponibles) y
 * muro/portal.html (documentos institucionales) comparten este mismo componente en vez
 * de duplicar la lógica de rotación.
 *
 * Markup esperado:
 *   <div data-carousel data-interval="3000">
 *     <div data-carousel-track>
 *       <div data-carousel-slide>...</div>
 *       <div data-carousel-slide>...</div>
 *     </div>
 *     <div data-carousel-dots></div>   (opcional)
 *   </div>
 *
 * Sin librería de terceros a propósito: la CSP no admite scripts de CDN.
 */
(function () {
  'use strict';

  var INTERVALO_POR_DEFECTO = 3000;

  document.querySelectorAll('[data-carousel]').forEach(function (carrusel) {
    var slides = carrusel.querySelectorAll('[data-carousel-slide]');
    if (slides.length === 0) { return; }

    var indiceActual = 0;
    var intervaloMs = parseInt(carrusel.getAttribute('data-interval'), 10) || INTERVALO_POR_DEFECTO;
    var temporizador = null;
    var contenedorPuntos = carrusel.querySelector('[data-carousel-dots]');
    var puntos = [];

    function mostrar(indice) {
      slides.forEach(function (slide, i) {
        slide.classList.toggle('hidden', i !== indice);
      });
      puntos.forEach(function (punto, i) {
        punto.classList.toggle('opacity-100', i === indice);
        punto.classList.toggle('opacity-40', i !== indice);
      });
      indiceActual = indice;
    }

    function siguiente() {
      mostrar((indiceActual + 1) % slides.length);
    }

    function iniciar() {
      if (temporizador || slides.length < 2) { return; }
      temporizador = window.setInterval(siguiente, intervaloMs);
    }

    function pausar() {
      if (temporizador) {
        window.clearInterval(temporizador);
        temporizador = null;
      }
    }

    if (contenedorPuntos && slides.length > 1) {
      slides.forEach(function (slide, i) {
        var punto = document.createElement('button');
        punto.type = 'button';
        punto.className = 'h-1.5 w-1.5 shrink-0 rounded-full bg-current opacity-40 transition-opacity';
        punto.setAttribute('aria-label', 'Ir al elemento ' + (i + 1));
        punto.addEventListener('click', function () {
          mostrar(i);
          pausar();
          iniciar();
        });
        contenedorPuntos.appendChild(punto);
        puntos.push(punto);
      });
    }

    carrusel.addEventListener('mouseenter', pausar);
    carrusel.addEventListener('mouseleave', iniciar);
    carrusel.addEventListener('focusin', pausar);
    carrusel.addEventListener('focusout', iniciar);

    mostrar(0);
    iniciar();
  });
})();
