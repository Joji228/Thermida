package app.thermida.data

/**
 * Regional food hints. On (the default): the AI and food search know the user lives in Greece and
 * eats Greek and other European food, but also US and other international dishes and brands. Off,
 * the AI gets no hint at all. Open Food Facts products sold in Greece only come first as a
 * tie-breaker; nothing is left out.
 *
 * [greekTuning] mirrors the saved setting; the app keeps it in step (see ThermidaApp).
 */
object AppRegion {
    /** Open Food Facts' tag for products sold in Greece. */
    const val FOOD_FACTS_COUNTRY = "en:greece"

    @Volatile
    var greekTuning: Boolean = true

    /** One line the AI prompts share; empty when the hints are off. */
    val promptHint: String
        get() = if (greekTuning) {
            "The user lives in Greece and eats international food too: Greek and other European dishes and brands, " +
                "US brands and chains, and anything else. Do not assume Greek unless they say so " +
                "(for example Lacta is a Greek chocolate, Fage and Delta are Greek dairy brands, a Big Mac is a US fast-food item)."
        } else {
            ""
        }
}
