package teamcheck;

import module chariot;

import module java.base;

public record CheckUtil(
        String teamId,
        Consumer<Team> teamHandler,
        Consumer<User> userHandler,
        Predicate<User> userFilter) {

    public void process(Client client) {

        var t = client.teams() .byTeamId(teamId());

        t.ifPresentOrElse(team -> {
            teamHandler.accept(team);
            client.teams().usersByTeamIdFull(team.id()).stream()
                .map(TeamMemberFull::user)
                .filter(userFilter())
                .forEach(userHandler());
        },
        () -> System.err.format("Couldn't find team [%s]%n", teamId()));
    }

    public static CheckUtil of(
            String teamId,
            Consumer<Team> teamHandler,
            Consumer<User> userHandler) {
        return of(teamId, teamHandler, userHandler, _ -> true);
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
            if (client.teams().kickFromTeam(teamId(), u) instanceof Fail<?> f) {
                if (f.message().contains("No such token")) {
                    try { onTokenBeenRevoked.run(); } catch (Exception ex) {}
                }
                return false;
            }
            return true;
        };
    }
}
