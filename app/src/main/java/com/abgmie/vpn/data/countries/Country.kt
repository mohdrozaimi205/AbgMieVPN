package com.abgmie.vpn.data.countries

enum class Continent(val label: String) {
    ASIA("Asia"),
    EUROPE("Europe"),
    NORTH_AMERICA("North America"),
    SOUTH_AMERICA("South America"),
    AFRICA("Africa"),
    OCEANIA("Oceania"),
    ANTARCTICA("Antarctica");
}

data class Country(
    val code: String,
    val name: String,
    val continent: Continent,
    val cities: List<String>,
) {
    /** Flag emoji built from the ISO code (regional indicator symbols) – no image assets needed. */
    val flag: String
        get() = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}
