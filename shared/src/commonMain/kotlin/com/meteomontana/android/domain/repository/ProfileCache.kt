package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.model.School

/**
 * Cachés locales como PUERTOS de dominio: los use cases dependen de estas
 * abstracciones, no de las clases SQLDelight de `data/saved` (ARCHITECTURE.md
 * §1: la flecha va data → domain, nunca al revés).
 */
interface ProfileCache {
    // @Throws obligatorio: la implementación lo lleva y Kotlin/Native exige que
    // el override tenga el MISMO filtro (si no, el build de iOS no compila).
    @Throws(Exception::class)
    suspend fun save(profile: PublicProfile)
    @Throws(Exception::class)
    suspend fun load(uid: String): PublicProfile?
}

/** Catálogo de escuelas cacheado en disco (contexto local sin red). */
interface SchoolCatalogCache {
    @Throws(Exception::class)
    suspend fun load(): List<School>
}
