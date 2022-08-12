package teamcheck;

import chariot.Client;
import chariot.ClientAuth;
import chariot.model.Ack;
import chariot.model.Fail;
import chariot.model.Team;
import chariot.model.User;

import java.util.function.Consumer;
import java.util.function.Predicate;

public record CheckUtil(
        String teamId,
        Consumer<Team> teamHandler,
        Consumer<User> userHandler,
        Predicate<User> userFilter) {

    public void process(Client client) {

        var t = client.teams() .byTeamId(teamId());

        t.ifPresentOrElse(team -> {
            teamHandler.accept(team);
            client.teams().usersByTeamId(team.id()).stream()
                .filter(userFilter())
                .forEach(userHandler());
        },
        () -> System.err.format("Couldn't find team [%s]%n", teamId()));
    }

    public static CheckUtil of(
            String teamId,
            Consumer<Team> teamHandler,
            Consumer<User> userHandler) {
        return of(teamId, teamHandler, userHandler, __ -> true);
    }

    static CheckUtil of(
            String teamId,
            Consumer<Team> teamHandler,
            Consumer<User> userHandler,
            Predicate<User> userFilter) {
        return new CheckUtil(teamId, teamHandler, userHandler, userFilter);
    }

    public interface Mate {
        boolean kick(String userId);
    }

    public Mate andMate(ClientAuth client, Runnable onTokenBeenRevoked) {
        return (u) -> {

            var res = client.teams().kickFromTeam(teamId(), u);

            if (res.isPresent()) {
                return res.get().ok();
            } else {
                if (res instanceof Fail<Ack> f && f.message().contains("No such token"))
                    try { onTokenBeenRevoked.run(); } catch (Exception ex) {}

                return false;
            }
        };
    }
}
