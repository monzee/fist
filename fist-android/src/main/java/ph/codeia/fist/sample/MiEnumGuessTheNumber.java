package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

import ph.codeia.fist.AndroidTroupe;
import ph.codeia.fist.AndroidFst;
import ph.codeia.fist.Effects;
import ph.codeia.fist.Fst;
import ph.codeia.fist.R;
import ph.codeia.fist.Mi;

@SuppressLint({"DefaultLocale", "SetTextI18N"})
public class MiEnumGuessTheNumber extends Fragment implements Play.Ui {
    private final Fst<GuessingGame> game = new AndroidFst<>(new GuessingGame(6));
    private Fst.Actor<GuessingGame, Play.Ui> ui;
    private TextView message;
    private EditText input;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        ui = AndroidTroupe.of(this).bind(game, this);
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.activity_guessing_game, container, false);
        message = root.findViewById(R.id.message);
        input = root.findViewById(R.id.guess);
        input.setOnEditorActionListener((textView, i, keyEvent) -> {
            String text = textView.getText().toString();
            if (keyEvent != null || text.isEmpty()) return false;
            ui.exec(Play.guess(Integer.parseInt(text)));
            return game.project(GuessingGame::isNotDone);
        });
        return root;
    }

    @Override
    public void tell(String message, Object... fmtArgs) {
        Toast.makeText(getContext(), String.format(message, fmtArgs), Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void clearInput() {
        input.setText(null);
    }

    @Override
    public void onBegin(int maxTries) {
        message.setText(String.format(""
                + "Choose a number from 1-100 inclusive.%n"
                + "You have %d tries to get it right.",
                maxTries
        ));
    }

    @Override
    public void onLow(int guess, int triesLeft) {
        String tries = triesLeft == 1 ? "try" : "tries";
        message.setText(String.format(
                "%d is too low; %d %s left",
                guess, triesLeft, tries
        ));
    }

    @Override
    public void onHigh(int guess, int triesLeft) {
        String tries = triesLeft == 1 ? "try" : "tries";
        message.setText(String.format(
                "%d is too high; %d %s left",
                guess, triesLeft, tries
        ));
    }

    @Override
    public void onWin() {
        message.setText("Correct!");
    }

    @Override
    public void onLose(int secret) {
        message.setText(String.format("Game over! The number was %d.", secret));
    }
}

class GuessingGame {
    private static final Random RNG = new Random();

    enum State {BEGIN, PLAYING, GAME_OVER}

    final int secret;
    final int maxTries;
    State state = State.BEGIN;
    int guess;
    int triesLeft;

    GuessingGame(int maxTries) {
        secret = RNG.nextInt(100) + 1;
        this.maxTries = maxTries;
        triesLeft = maxTries;
    }

    boolean isNotDone() {
        return state != State.GAME_OVER;
    }
}

class Play {
    interface Ui extends Effects<GuessingGame> {
        void tell(String message, Object... fmtArgs);
        void clearInput();
        void onBegin(int maxTries);
        void onLow(int guess, int triesLeft);
        void onHigh(int guess, int triesLeft);
        void onWin();
        void onLose(int secret);

        @Override
        default void onEnter(GuessingGame game) {
            switch (game.state) {
            case BEGIN:
                onBegin(game.maxTries);
                break;
            case PLAYING:
                if (game.guess < game.secret) {
                    onLow(game.guess, game.triesLeft);
                }
                else {
                    onHigh(game.guess, game.triesLeft);
                }
                break;
            case GAME_OVER:
                if (game.guess == game.secret) {
                    onWin();
                }
                else {
                    onLose(game.secret);
                }
                break;
            }
        }
    }

    static Mi.Action<GuessingGame, Ui> newGame(int maxTries) {
        return (_g, ui) -> {
            ui.tell("Starting a new game in 10 seconds.");
            return Mi.async(() -> {
                Thread.sleep(10_000);
                return (_fg, futureUi) -> {
                    futureUi.clearInput();
                    return Mi.enter(new GuessingGame(maxTries));
                };
            });
        };
    }

    static Mi.Action<GuessingGame, Ui> guess(int n) {
        return (game, ui) -> {
            switch (game.state) {
            case BEGIN:
                game.state = GuessingGame.State.PLAYING;
                // fallthrough
            case PLAYING:
                game.guess = n;
                game.triesLeft--;
                if (n == game.secret || game.triesLeft == 0) {
                    game.state = GuessingGame.State.GAME_OVER;
                    return newGame(game.maxTries).after(Mi.reenter());
                }
                else {
                    ui.clearInput();
                    return Mi.reenter();
                }
            case GAME_OVER:
                ui.tell("Wait for the new game to start");
                return Mi.noop();
            }
            return Mi.raise(new IllegalStateException(game.state.name()));
        };
    }
}
