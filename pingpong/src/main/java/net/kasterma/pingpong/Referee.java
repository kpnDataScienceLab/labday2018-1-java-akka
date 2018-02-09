package net.kasterma.pingpong;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Referee of our games.
 *
 * Creates the players and sets up the games.
 */
public class Referee extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private Map<String, ActorRef> players = new HashMap<>();
    private Map<String, Integer> points = new HashMap<>();
    private List<String> playing = new ArrayList<>();
    private Map<ActorRef, String> whoDropped = new HashMap<>();
    private Map<ActorRef, String> whoOpponent = new HashMap<>();
    private Random random = new Random();
    private Integer count;

    static Props props() {
        return Props.create(Referee.class);
    }

    @AllArgsConstructor
    static class SinglePair {
        @Getter
        final Integer heats;
    }

    @AllArgsConstructor
    static class Tournament {
        @Getter
        private final Integer playerCount;
        @Getter
        private final Integer heats;
    }

    static class ShowScores {
    }

    private void runSingleGame(SinglePair singlePair) {
        log.info("Ref starting single game");
        players.put("player1", getContext().actorOf(Player.props("player1", 42L), "player1"));
        players.put("player2", getContext().actorOf(Player.props("player2", 43L), "player2"));
        count = singlePair.getHeats() - 1;
        startGame("player1", "player2");
    }


    private void startGame(String player1, String player2) {
        playing.add(player1);
        playing.add(player2);
        players.get(player2).tell(new Player.Ready(players.get(player1)), getSelf());
        players.get(player1).tell(new Player.Serve(players.get(player2)), getSelf());
    }

    private Boolean runGame() {
        List<String> available = players.entrySet().stream()
                .map(Map.Entry::getKey)
                .filter(n -> !playing.contains(n))
                .collect(Collectors.toList());
        if (available.size() < 2) {
            return false;
        } else {
            Integer id1 = random.nextInt(available.size());
            Integer id2 = random.nextInt(available.size() - 1);
            if (id2 >= id1) {
                id2++;
            }
            startGame(available.get(id1), available.get(id2));
            return true;
        }
    }

    private void runTournament(Tournament tournament) {
        log.info("Ref starting tournament with {} players.", tournament.getPlayerCount());
        for(Integer idx = 0; idx < tournament.getPlayerCount(); idx++) {
            String playerName = "player-" + idx;
            players.put(playerName, getContext().actorOf(Player.props(playerName, 42L), playerName));
        }
        count = tournament.getHeats() - 1;
        while(runGame()) {
            log.info("Starting game");
        }
    }

    /**
     * Actions to take when actor dropped, dropped the ball.
     *
     * @param dropped actor that dropped the ball
     */
    private void dropBall(Player.Dropped dropped) {
        getSender().tell(new Player.WhoAreYou(), getSelf());
        getSender().tell(new Player.WhoIsYourOpponent(), getSelf());
    }

    /**
     * Record and act on identities.  We only request these in the context of a dropped ball.  Then the dropper gets
     * asked for its identifier, and the identifier of its opponent.
     *
     * @param ident received Identity message
     */
    private void record(Player.Identity ident) {
        if (ident.getMe()) {
            whoDropped.put(getSender(), ident.getIdentity());
        } else {
            whoOpponent.put(getSender(), ident.getIdentity());
        }

        if (whoDropped.containsKey(getSender()) && whoOpponent.containsKey(getSender())) {
            String droppedId = whoDropped.get(getSender());
            String oppId = whoOpponent.get(getSender());
            whoDropped.remove(getSender());
            whoOpponent.remove(getSender());

            log.info("player {}", droppedId);
            log.info("opponent {}", oppId);

            Integer pts = points.getOrDefault(oppId, 0);
            points.put(oppId, pts + 1);

            playing.remove(droppedId);
            playing.remove(oppId);

        }
        log.info("Still playing {}", playing.toString());
        if (playing.isEmpty() && count > 0) {
            while(runGame()) {
                log.info("Starting game");
            }
            count--;
        }
    }

    /**
     * Log the current scores.
     *
     * @param ss receved ShowScores message
     */
    private void showScores(ShowScores ss) {
        log.info(points.toString());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SinglePair.class, this::runSingleGame)
                .match(Tournament.class, this::runTournament)
                .match(Player.Dropped.class, this::dropBall)
                .match(Player.Identity.class, this::record)
                .match(ShowScores.class, this::showScores)
                .build();
    }
}
