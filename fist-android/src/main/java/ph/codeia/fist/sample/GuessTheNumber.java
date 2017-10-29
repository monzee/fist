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

import ph.codeia.fist.AndroidRunner;
import ph.codeia.fist.R;
import ph.codeia.fist.moore.Mu;

public class GuessTheNumber extends AppCompatActivity {

    private Mu.Runner<Game> game;
    private Mu.Actor<Game> screen;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //noinspection unchecked
        game = (AndroidRunner<Game>) getLastCustomNonConfigurationInstance();
        if (game == null) {
            game = new AndroidRunner<>(new Game());
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guessing_game);
        TextView message = (TextView) findViewById(R.id.message);
        EditText input = (EditText) findViewById(R.id.guess);
        input.setOnEditorActionListener((textView, i, keyEvent) -> {
            String text = textView.getText().toString();
            if (keyEvent != null || text.isEmpty()) return false;
            screen.exec(guess(Integer.parseInt(text)));
            boolean notDone = game.inspect(state -> Game.IN_PLAY.contains(state.result));
            if (notDone) textView.setText(null);
            return notDone;
        });
        screen = Mu.bind(game, state -> render(state, message));
    }

    @Override
    protected void onResume() {
        super.onResume();
        screen.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screen.stop();
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
                        + "You have %d tries to get it right.", state.triesLeft));
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
                        state.guess, state.triesLeft, tries));
                break;
            case HIGH:
                message.setText(String.format(
                        "%d is too high; %d %s remaining.",
                        state.guess, state.triesLeft, tries));
                break;
        }
    }

    static Mu.Action<Game> newGame() throws InterruptedException {
        Thread.sleep(10_000);
        return state -> Mu.enter(new Game());
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

    private static class Game {
        enum Result { BEGIN, LOW, HIGH, WON, LOST }
        static final Random RNG = new Random();
        static final EnumSet<Result> IN_PLAY = EnumSet.of(Result.LOW, Result.HIGH);

        Result result = Result.BEGIN;
        int secret = RNG.nextInt(100) + 1;
        int triesLeft = 6;
        int guess;
    }
}
