package com.driversafety.ai.ai

import kotlin.random.Random

class CognitiveGameManager {
    data class GameItem(val question: String, val answerPrompt: String, val validator: (String) -> Boolean)

    private val pool = listOf(
        GameItem("What is 15 plus 7?", "What is 15 + 7?") { it.trim() == "22" },
        GameItem("Type the word AWAKE backwards", "Type 'AWAKE' backwards:") { it.trim().equals("ekawa", ignoreCase = true) },
        GameItem("What is the opposite of HOT?", "What is the opposite of hot?") { it.trim().equals("cold", ignoreCase = true) },
        GameItem("Which traffic light color means stop?", "Which traffic light color means stop?") { it.trim().equals("red", ignoreCase = true) },
        GameItem("Type the number 100", "Type the number one-hundred:") { it.trim() == "100" }
    )

    fun getRandomChallenge(): GameItem {
        // Return a random challenge
        return pool[Random.nextInt(pool.size)]
    }
}
