package dev.cramsoft.sallexplore

data class PersonajeInfo(
    val nombre          : String,
    val descripcionCorta: String,
    val frase           : String
)

val PERSONAJES_INFO = mapOf(
    "San_Juan_Bautista" to PersonajeInfo(
        nombre           = "San Juan Bautista de La Salle",
        descripcionCorta = "Fundador Lasallista · Patrono universal de los educadores",
        frase            = "\"Toca los corazones de los jóvenes\ncon ternura y firmeza a la vez.\""
    ),
    "Hermano_Miguel" to PersonajeInfo(
        nombre           = "Santo Hermano Miguel",
        descripcionCorta = "Maestro y pedagogo ecuatoriano · Canonizado en 1984",
        frase            = "\"La virtud del maestro es\nla mejor lección para el alumno.\""
    ),
    "Hermano_Salomon" to PersonajeInfo(
        nombre           = "Santo Hermano Salomón",
        descripcionCorta = "Educador y mártir lasallista · Canonizado en 2016",
        frase            = "\"La fe no se rinde\nante la adversidad.\""
    )
)