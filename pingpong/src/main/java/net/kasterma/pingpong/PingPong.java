package net.kasterma.pingpong;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

/**
 * PingPong application
 *
 * Send ping/pong back and forth between different actors.
 */
@Slf4j
public class PingPong {
    /**
     * Create command line parser for this application.
     */
    private static CommandLine cmd(String[] args) {
        Option single = new Option("s", "single", false,
                "start a single game");
        Option tournament = new Option("t", "tournament", false,
                "start up tournament");
        Option playerCount = new Option("c", "playerCount", true,
                "Count of players in tournament");
        Option heatCount = new Option("h", "heats", true, "Number of heats");
        Options options = new Options();
        options.addOption(single);
        options.addOption(tournament);
        options.addOption(playerCount);
        options.addOption(heatCount);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            log.error("Error parting command line arguments: {}", e.toString());
            System.exit(1);
        }
        return cmd;
    }

    public static void main(String[] args) {
        CommandLine cmd = cmd(args);
        final ActorSystem system = ActorSystem.create("helloakka");
        try {
            ActorRef referee = system.actorOf(Referee.props(), "ref");
            if (cmd.hasOption("single")) {
                log.info("Staring single game");
                referee.tell(new Referee.SinglePair(Integer.valueOf(cmd.getOptionValue("heats"))), ActorRef.noSender());
            } else if (cmd.hasOption("tournament")) {
                log.info("Starting tournament with players {}", cmd.getOptionValue("playerCount"));
                referee.tell(new Referee.Tournament(Integer.valueOf(cmd.getOptionValue("playerCount")),
                        Integer.valueOf(cmd.getOptionValue("heats"))), ActorRef.noSender());
            }
            Thread.sleep(1000);
            referee.tell(new Referee.ShowScores(), ActorRef.noSender());
        } catch (InterruptedException e) {
            log.info("Sleep interrupted {}", e.toString());
        } finally {
            log.info("closing up shop");
            system.terminate();
        }
    }
}
