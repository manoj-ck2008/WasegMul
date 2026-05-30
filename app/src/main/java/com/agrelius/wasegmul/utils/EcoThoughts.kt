package com.agrelius.wasegmul.utils

object EcoThoughts {
    private val thoughts = listOf(
        "Small acts, when multiplied by millions, can transform the world.",
        "Waste isn't waste until we waste it.",
        "The greatest threat to our planet is the belief that someone else will save it.",
        "Refuse what you do not need; reduce what you do; reuse what you can.",
        "Recycling is a good thing to do, but it is not a replacement for not consuming.",
        "Be the change you wish to see in the world.",
        "The Earth does not belong to us: we belong to the Earth.",
        "Sustainability is no longer about doing less harm. It’s about doing more good.",
        "We don't inherit the earth from our ancestors, we borrow it from our children.",
        "Earth provides enough to satisfy every man's needs, but not every man's greed."
    )

    fun getRandom(): String = thoughts.random()
}
