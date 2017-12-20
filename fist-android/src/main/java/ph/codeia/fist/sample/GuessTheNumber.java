package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.TextView;

import java.util.EnumSet;
import java.util.Random;

import ph.codeia.fist.AndroidTroupe;
import ph.codeia.fist.AndroidFst;
import ph.codeia.fist.Fst;
import ph.codeia.fist.R;
import ph.codeia.fist.Mu;

public class GuessTheNumber extends AppCompatActivity {

    private static class Game {
        enum Result {BEGIN, LOW, HIGH, WON, LOST}

        static final Random RNG = new Random();
        static final EnumSet<Result> IN_PLAY = EnumSet.of(Result.LOW, Result.HIGH);

        Result result = Result.BEGIN;
        int secret = RNG.nextInt(100) + 1;
        int triesLeft = 6;
        int guess;

        boolean isNotDone() {
            return IN_PLAY.contains(result);
        }
    }

    private Fst<Game> game;
    private Fst.Actor<Game, ?> screen;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //noinspection unchecked
        game = (AndroidFst<Game>) getLastCustomNonConfigurationInstance();
        if (game == null) {
            game = new AndroidFst<>(new Game());
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guessing_game);
        TextView message = findViewById(R.id.message);
        EditText input = findViewById(R.id.guess);
        input.setOnEditorActionListener((textView, i, keyEvent) -> {
            String text = textView.getText().toString();
            if (keyEvent != null || text.isEmpty()) return false;
            screen.exec(guess(Integer.parseInt(text)));
            boolean notDone = game.project(Game::isNotDone);
            if (notDone) textView.setText(null);
            return notDone;
        });
        screen = AndroidTroupe.of(this).bind(game, state -> render(state, message));
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return game;
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    static void render(Game state, TextView message) {
        String tries = state.triesLeft == 1 ? "try" : "tries";
        switch (state.result) {
        case BEGIN:
            message.setText(String.format(""
                    + "Choose a number from 1-100 inclusive.%n"
                    + "You have %d tries to get it right.",
                    state.triesLeft
            ));
            break;
        case WON:
            message.setText("Correct! Starting a new game in 10 seconds...");
            break;
        case LOST:
            message.setText(String.format("Game over! The number was %d.", state.secret));
            break;
        case LOW:
            message.setText(String.format(
                    "%d is too low; %d %s remaining.",
                    state.guess, state.triesLeft, tries
            ));
            break;
        case HIGH:
            message.setText(String.format(
                    "%d is too high; %d %s remaining.",
                    state.guess, state.triesLeft, tries
            ));
            break;
        }
    }

    static Mu.Action<Game> newGame() throws InterruptedException {
        Thread.sleep(10_000);
        return Mu.Action.pure(new Game());
    }

    static Mu.Action<Game> guess(int n) {
        return state -> {
            switch (state.result) {
            case LOST:  // fallthrough
            case WON:
                return Mu.noop();
            default:
                if (n == state.secret) {
                    state.result = Game.Result.WON;
                    return Mu.enter(state).then(GuessTheNumber::newGame);
                }
                if (state.triesLeft == 1) {
                    state.result = Game.Result.LOST;
                    return Mu.enter(state).then(GuessTheNumber::newGame);
                }
                state.guess = n;
                state.result = n < state.secret ? Game.Result.LOW : Game.Result.HIGH;
                state.triesLeft--;
                return Mu.reenter();
            }
        };
    }
}
