/*
 * Catálogo con buscador y chips de filtro, reutilizable: capacitaciones/catalogo.html
 * y documentos-institucionales/listado.html comparten este mismo componente en vez de
 * duplicar la lógica de filtrado. Todo client-side sobre las tarjetas ya renderizadas —
 * ninguno de los dos catálogos es tan grande como para justificar una ida y vuelta al
 * servidor por cada tecla o clic.
 *
 * Markup esperado:
 *   <div data-catalogo>
 *     <input type="search" data-catalogo-buscador>
 *     <button type="button" data-catalogo-filtro>Todos</button>
 *     <button type="button" data-catalogo-filtro data-campo="obligatorio" data-valor="true">Obligatorios</button>
 *     <div data-catalogo-lista>
 *       <article data-catalogo-tarjeta data-titulo="..." data-obligatorio="true">...</article>
 *     </div>
 *     <p data-catalogo-sin-resultados class="hidden">Sin resultados</p>
 *   </div>
 *
 * Un botón de filtro sin data-campo/data-valor es "todos" (sin filtrar). Cada tarjeta
 * declara sus propios data-<campo> para que cualquier filtro nuevo sea solo un botón
 * más, sin tocar este archivo.
 */
(function () {
  'use strict';

  document.querySelectorAll('[data-catalogo]').forEach(function (catalogo) {
    var lista = catalogo.querySelector('[data-catalogo-lista]');
    if (!lista) { return; }

    var buscador = catalogo.querySelector('[data-catalogo-buscador]');
    var botonesFiltro = catalogo.querySelectorAll('[data-catalogo-filtro]');
    var tarjetas = lista.querySelectorAll('[data-catalogo-tarjeta]');
    var sinResultados = catalogo.querySelector('[data-catalogo-sin-resultados]');
    var filtroActivo = null;

    function coincideFiltro(tarjeta) {
      if (!filtroActivo) { return true; }
      return tarjeta.getAttribute('data-' + filtroActivo.campo) === filtroActivo.valor;
    }

    function aplicar() {
      var texto = (buscador ? buscador.value : '').trim().toLowerCase();
      var visibles = 0;
      tarjetas.forEach(function (tarjeta) {
        var coincideTexto = !texto || tarjeta.getAttribute('data-titulo').indexOf(texto) !== -1;
        var visible = coincideTexto && coincideFiltro(tarjeta);
        tarjeta.classList.toggle('hidden', !visible);
        if (visible) { visibles++; }
      });
      if (sinResultados) { sinResultados.classList.toggle('hidden', visibles > 0); }
    }

    if (buscador) {
      buscador.addEventListener('input', aplicar);
    }

    botonesFiltro.forEach(function (boton) {
      boton.addEventListener('click', function () {
        var campo = boton.getAttribute('data-campo');
        filtroActivo = campo ? { campo: campo, valor: boton.getAttribute('data-valor') } : null;
        botonesFiltro.forEach(function (b) {
          b.classList.toggle('btn-primary', b === boton);
          b.classList.toggle('btn-secondary', b !== boton);
        });
        aplicar();
      });
    });

    aplicar();
  });
})();
