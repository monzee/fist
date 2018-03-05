package ph.codeia.fist.ktdemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import ph.codeia.fist.*
import ph.codeia.fist.lifecycle.bind
import java.util.*


/*
 * This file is a part of the fist project.
 */

class KtGuessTheNumber : Fragment() {

    private lateinit var ui: Fst.Binding<GuessingGame, Ui>
    private lateinit var guess: EditText
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        ui = bind(GuessingGame(6), @SuppressLint("SetTextI18n") object : Ui {
            override fun tell(message: String, vararg fmtArgs: Any?) {
                Toast.makeText(context, message.format(fmtArgs), Toast.LENGTH_SHORT).show()
            }

            override fun prepareForInput() {
                guess.text = null
            }

            override fun onBegin(maxTries: Int) {
                message.text = "Guess the number from 1-100. You have $maxTries ${maxTries.tries}."
            }

            override fun onLow(guess: Int, triesLeft: Int) {
                message.text = "$guess is too low; $triesLeft ${triesLeft.tries} left."
            }

            override fun onHigh(guess: Int, triesLeft: Int) {
                message.text = "$guess is too high; $triesLeft ${triesLeft.tries} left."
            }

            override fun onWin() {
                message.text = "You got it!"
            }

            override fun onLose(secret: Int) {
                message.text = "You lose! The number was $secret"
            }

            private val Int.tries get() = if (this == 1) "try" else "tries"
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.activity_guessing_game, container, false)?.also {
        message = it.findViewById(R.id.message)
        guess = it.findViewById(R.id.guess)
        guess.setOnEditorActionListener { _, _, keyEvent ->
            val input = guess.text.toString()
            when {
                keyEvent != null -> false
                input.isEmpty() -> false
                else -> {
                    ui += guess(input.toInt())
                    ui(GuessingGame::isNotDone)
                }
            }
        }
    }
}

private val RNG = Random()

private enum class State {BEGIN, PLAYING, GAME_OVER}

private class GuessingGame(val maxTries: Int) {
    val secret = RNG.nextInt(100) + 1
    var triesLeft = maxTries
    var guess = -1
    var state = State.BEGIN
    val isNotDone get() = state != State.GAME_OVER
}

private interface Ui : Effects<GuessingGame> {
    fun tell(message: String, vararg fmtArgs: Any?)
    fun prepareForInput()
    fun onBegin(maxTries: Int)
    fun onLow(guess: Int, triesLeft: Int)
    fun onHigh(guess: Int, triesLeft: Int)
    fun onWin()
    fun onLose(secret: Int)

    override fun onEnter(game: GuessingGame) {
        when (game.state) {
            State.BEGIN -> onBegin(game.maxTries)
            State.PLAYING -> when {
                game.guess < game.secret -> onLow(game.guess, game.triesLeft)
                else -> onHigh(game.guess, game.triesLeft)
            }
            State.GAME_OVER -> when {
                game.guess == game.secret -> onWin()
                else -> onLose(game.secret)
            }
        }
    }
}

private fun newGame(maxTries: Int) = action<GuessingGame, Ui> { _, ui ->
    ui.tell("Starting a new game in 10 seconds.")
    async {
        Thread.sleep(10_000)
        action { _, futureUi ->
            futureUi.prepareForInput()
            enter(GuessingGame(maxTries))
        }
    }
}

private fun guess(n: Int) = action<GuessingGame, Ui> { game, ui ->
    when (game.state) {
        State.BEGIN,
        State.PLAYING -> {
            game.state = State.PLAYING
            game.guess = n
            game.triesLeft--
            if (n == game.secret || game.triesLeft == 0) {
                game.state = State.GAME_OVER
                reenter + newGame(game.maxTries)
            }
            else {
                ui.prepareForInput()
                reenter
            }
        }
        State.GAME_OVER -> {
            ui.tell("Wait for the new game to start")
            noop
        }
    }
}
