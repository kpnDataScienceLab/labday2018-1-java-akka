package net.kasterma.pingpong;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;
import java.util.Random;

/**
 * Player in a ping-pong tournament.
 *
 * A referee will send it opponents through Ready or Serve, then ping/pong until one of the players decides to smash
 * at which time it becomes a smash fest of stronger smashes until one player drops the ball.
 *
 */
public class Player extends AbstractActor {
    private final String identity;
    private final Random random;
    private ActorRef opponent;
    private ActorRef referee;
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    static Props props(String identity, Long seed) {
        return Props.create(Player.class, identity, seed);
    }

    static class Ping {
    }

    static class Pong {
    }

    @AllArgsConstructor
    static class Smash {
        @Getter
        private final Float strength;
    }

    static class Dropped {
    }

    @AllArgsConstructor
    static class Serve {
        @Getter
        private final ActorRef opponent;
    }

    @AllArgsConstructor
    static class Ready {
        @Getter
        private final ActorRef opponent;
    }

    static class WhoAreYou {
    }

    static class WhoIsYourOpponent {
    }

    @AllArgsConstructor
    static class Identity {
        @Getter
        final Boolean me;  // false for opponent
        @Getter
        final String identity;

        Identity(String identity) {
            this(true, identity);
        }
    }

    Player(String identity, Long seed) {
        this.identity = identity;
        this.random = new Random(seed);
    }

    /**
     * Decide whether to smash back (probability 0.1)
     *
     * @return true if want to smash.
     */
    Boolean toSmash() {
        return random.nextFloat() < 0.1;
    }

    /**
     * Succeed at returning smash?
     *
     * @return true if succeed at returning smash
     */
    Optional<Smash> returnSmash(Smash smash) {
        Smash returnSmash = new Smash(random.nextFloat());
        if (smash.getStrength() < returnSmash.getStrength())
            return Optional.of(returnSmash);
        else
            return Optional.empty();
    }

    void sendPong(ActorRef to) {
        log.info("{} send pong", identity);
        to.tell(new Pong(), getSelf());
    }

    void sendPing(ActorRef to) {
        log.info("{} send ping", identity);
        to.tell(new Ping(), getSelf());
    }

    void sendSmash(ActorRef to) {
        Smash smash = new Smash(random.nextFloat());
        sendSmash(to, smash);
    }

    void sendSmash(ActorRef to, Smash smash) {
        log.info("{} send smash of strength {}", identity, smash.getStrength());
        to.tell(smash, getSelf());
    }

    void sendDrop(ActorRef to) {
        log.info("{} dropped the ball", identity);
        to.tell(new Dropped(), getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Ping.class, p -> {
                    ActorRef sender = getSender();
                    if (sender != opponent) {
                        throw new Exception("Ping from non-opponent");
                    }
                    if (toSmash())
                        sendSmash(opponent);
                    else
                        sendPong(opponent);
                })
                .match(Pong.class, p -> {
                    ActorRef sender = getSender();
                    if (sender != opponent) {
                        throw new Exception("Pong from non-opponent");
                    }
                    sendPing(getSender());
                })
                .match(Smash.class, p -> {
                    ActorRef sender = getSender();
                    if (sender != opponent) {
                        throw new Exception("Smash from non-opponent");
                    }
                    Optional<Smash> returnSmash = returnSmash(p);
                    if (returnSmash.isPresent()) {
                        sendSmash(opponent, returnSmash.get());
                    } else {
                        sendDrop(referee);
                    }
                })
                .match(Serve.class, s -> {
                    opponent = s.getOpponent();
                    referee = getSender();
                    sendPing(opponent);
                })
                .match(Ready.class, s -> {
                    opponent = s.getOpponent();
                    referee = getSender();
                })
                .match(WhoAreYou.class, s -> getSender().tell(new Identity(identity), getSelf()))
                .match(WhoIsYourOpponent.class, s -> {
                    opponent.tell(new WhoAreYou(), getSelf());
                })
                .match(Identity.class, s -> referee.tell(new Identity(false, s.getIdentity()), getSelf()))
                .build();
    }
}
