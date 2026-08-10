package com.meteomontana.android.ui.theme

/**
 * Cómo se pinta el ARMAZÓN de la app: la barra de pestañas y las hojas.
 *
 * La regla de diseño que introduce esto (recogida en DESIGN.md): **el contenido
 * sigue plano y el armazón gana material**. Las tarjetas, los topos, las listas
 * y los grados no se tocan — siguen siendo papel y tinta. Lo que gana
 * profundidad es lo que rodea al contenido, que es exactamente lo que hace iOS:
 * allí las tarjetas de Cumbre también son planas, y el cristal lo pone el
 * sistema alrededor.
 */
enum class ChromeTreatment {
    /** Color liso, como toda la vida. El respaldo cuando no hay nada mejor. */
    SOLIDO,

    /** Esmerilado: el fondo se difumina por detrás. Requiere Android 12+. */
    ESMERILADO,

    /**
     * Esmerilado **más el borde de luz** que hace que parezca un canto de
     * vidrio y no una lámina translúcida.
     *
     * No es el Liquid Glass de Apple: aquello refracta de verdad, doblando lo
     * que hay detrás con un shader. Eso en Android existe (AGSL, Android 13+),
     * pero hoy solo está en librerías en alfa mantenidas por una persona, y
     * meter eso en una app con usuarios reales es otra conversación. Esto de
     * aquí busca la misma sensación con las herramientas de siempre: casi todo
     * el efecto, sin la deuda.
     */
    CRISTAL;

    companion object {

        /**
         * Desde esta versión de Android existe el desenfoque de verdad
         * (`RenderEffect`). Por debajo la API sencillamente no está: no es que
         * vaya lento, es que no se puede.
         */
        const val API_MINIMA_DESENFOQUE = 31

        /**
         * El tratamiento que un móvil puede permitirse.
         *
         * Existe como función pura —entra un número, sale un tratamiento— para
         * poder probarla sin dispositivo. Es la única parte de todo esto que se
         * puede verificar con tests: que la barra quede bonita no lo dice
         * ningún assert.
         *
         * Un móvil viejo pidiendo ESMERILADO no falla ni revienta: se le da
         * SOLIDO, que es lo que puede pintar.
         */
        fun paraApi(sdkInt: Int, deseado: ChromeTreatment): ChromeTreatment =
            if (deseado != SOLIDO && sdkInt < API_MINIMA_DESENFOQUE) SOLIDO
            else deseado
    }
}
